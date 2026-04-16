# Wearable Integration Spec

> **Phase:** P7 (AI Trainer groundwork — parallel to Data Foundation)
> **Status:** `not-started`
> **Effort:** S (HC enhancements only — Tier 1)
> **Depends on:** Health Connect base (P3 done), Firebase Auth (done)
>
> **Scope decision (2026-04-15):** Tier 2 (direct vendor APIs — Garmin, Oura) is explicitly deferred. Engineering cost and approval overhead don't justify the incremental gain over HC. All wearable integration is Health Connect only.

---

## 1. Purpose

The AI trainer needs physiological signals from wearables — sleep quality, heart rate variability, recovery markers, stress, VO2 Max. This spec defines a **tiered wearable integration strategy**:

- **Tier 1 (Health Connect):** The universal base layer. Works with ALL Android wearables. No backend required.
- **Tier 2 (Direct vendor APIs):** Optional premium integrations for richer data. Requires a lightweight backend (Firebase Cloud Functions).

**Design principle:** The AI trainer must work well with Tier 1 alone. Tier 2 makes it work *better* but is never required.

---

## 2. Target Vendors & Market Context

| Vendor | Key Devices | HC Support | Direct API Available | Priority |
|---|---|---|---|---|
| **Garmin** | Venu, Forerunner, Fenix, Instinct | Yes (since 2023) | Yes (Garmin Health API, OAuth 1.0a, server-to-server) | High |
| **Samsung** | Galaxy Watch series | Yes (co-creator of HC) | Limited (Privileged SDK, restricted) | Medium (HC covers most data) |
| **Xiaomi / Amazfit (Zepp)** | Mi Band, Amazfit GTR/GTS/T-Rex | Yes (via Zepp app) | Zepp API (limited, OAuth 2.0) | Medium |
| **Huawei** | Watch GT, Watch Fit, Band | Partial (newer devices via Health app) | Huawei Health Kit (restricted to HMS ecosystem) | Low |
| **Oura** | Oura Ring Gen 3/4 | Yes (since 2024) | Yes (Oura Cloud API v2, OAuth 2.0) | Low-Medium |
| **Apple Watch** | All models | No (iOS only) | N/A for Android | Out of scope |
| **Whoop / Polar** | — | — | — | Out of scope |

---

## 3. Greatest Common Factor — Data Available via Health Connect from ALL Major Vendors

This is the data every major Android wearable (Garmin, Samsung, Xiaomi, Huawei) syncs to Health Connect. This is the **guaranteed data floor** for all users.

| Data Type | HC Record Type | Already in PowerME? | AI Trainer Use |
|---|---|---|---|
| **Daily Steps** | `StepsRecord` | Yes (live) | Activity level, NEAT estimation |
| **Resting Heart Rate** | `RestingHeartRateRecord` | Yes (live) | Recovery marker (ReadinessEngine) |
| **HRV RMSSD** | `HeartRateVariabilityRmssdRecord` | Yes (live) | Recovery marker (ReadinessEngine) |
| **Sleep Duration + Basic Stages** | `SleepSessionRecord` | Yes (live, duration only) | Recovery marker (ReadinessEngine) |
| **Weight** | `WeightRecord` | Yes (live) | Body metrics tracking |
| **Body Fat %** | `BodyFatRecord` | Yes (live) | Body composition |
| **Height** | `HeightRecord` | Yes (live) | BMI calculation |
| **Active Heart Rate** | `HeartRateRecord` | Planned (P4) | Workout intensity, HR zones |
| **Active Calories** | `ActiveCaloriesBurnedRecord` | Planned (P4) | Energy expenditure, NEAT |
| **Distance** | `DistanceRecord` | Planned (P4) | Cardio volume |
| **SpO2 (spot check)** | `OxygenSaturationRecord` | Planned (P4) | Recovery anomaly detection |
| **VO2 Max** | `Vo2MaxRecord` | Planned (P4) | Aerobic fitness level |
| **Exercise Sessions** | `ExerciseSessionRecord` | Planned (Phase B) | Cross-app workout import |
| **Menstruation** | `MenstruationPeriodRecord` | Planned (P7, female only) | Cycle-adjusted training |

### HC data NOT yet read but available and relevant to AI trainer:

| Data Type | HC Record Type | Current Status | Recommendation |
|---|---|---|---|
| **Sleep stages** (light/deep/REM/awake) | `SleepSessionRecord.stages` | We read duration only, not stages | **Add stage extraction** — deep sleep duration is a stronger recovery signal than total duration |
| **Respiratory Rate** | `RespiratoryRateRecord` | Deferred (GAP-16) | **Add in P7** — early illness/overtraining marker, available from Garmin/Samsung/Oura via HC |
| **Basal Metabolic Rate** | `BasalMetabolicRateRecord` | Spec'd but not in code | Add when smart scale data matters |
| **Lean Body Mass** | `LeanBodyMassRecord` | Spec'd but not in code | Add when smart scale data matters |
| **Bone Mass** | `BoneMassRecord` | Spec'd but not in code | Low priority |

---

## 4. What Health Connect Does NOT Provide (Vendor-Proprietary Data)

This is data that **no vendor** syncs to Health Connect. It's only available through direct API integrations.

| Data Category | Garmin | Samsung | Xiaomi/Amazfit | Oura | AI Trainer Value |
|---|---|---|---|---|---|
| **Stress Score / All-day stress** | Body Battery + Stress level (per-minute) | Samsung stress (limited) | Zepp PAI / stress | — | Replaces self-reported stress (better than nothing) |
| **Training Load / Status** | Training Load, Training Status, Training Effect | — | — | — | Pre-computed ACWR-like metric |
| **Recovery Time (hours)** | Yes | — | — | — | Direct session scheduling input |
| **Training Readiness** | Yes (composite score) | — | — | Readiness Score | Composite readiness, richer than our ReadinessEngine |
| **Advanced Sleep Score** | Sleep Score + detailed staging | Sleep Score | Sleep Score | Sleep Score + contributors | Better sleep quality signal than duration alone |
| **Continuous SpO2** | Overnight continuous | Yes | Some models | Yes | Recovery quality signal |
| **Respiration Rate** | All-day + sleep | — | — | Sleep only | Overtraining / illness marker |
| **Body Temperature** | — | Skin temp (some models) | — | Yes | Recovery / illness / cycle phase marker |
| **HRV Status (contextualized)** | 7-day baseline, balanced/low/unbalanced | — | — | HRV contributors | More useful than raw RMSSD |
| **VO2 Max (proprietary calc)** | Yes (Firstbeat) | — | — | — | HC `Vo2MaxRecord` exists but Garmin may not sync to it |

### Key insight: What the AI trainer can compute WITHOUT proprietary data

| Proprietary Metric | Our Substitute (from HC + app data) | Quality vs. Proprietary |
|---|---|---|
| Garmin Training Load | `sRPE x duration` (GAP-1) → ACWR | 80% as good — sRPE is validated by Foster et al. |
| Garmin Training Readiness | `ReadinessEngine` (HRV + sleep + RHR z-scores) | 70% as good — missing stress/activity context |
| Garmin Body Battery | No direct substitute | Unique gap — continuous energy tracking |
| Garmin Stress Score | No direct substitute (we chose minimal friction, no daily check-in) | Significant gap for non-Garmin users |
| Garmin/Oura Sleep Score | Can compute from HC sleep stages (deep + REM % + duration + efficiency) | 75% as good if we extract stages |
| Garmin Recovery Time | Can estimate from ACWR + session load trends | 60% as good — Garmin uses HR data during activity |
| Oura Readiness Score | `ReadinessEngine` + cycle data | 70% as good |

**Conclusion:** The AI trainer is functional with HC-only data + app-internal signals (sRPE, volume, performance trends). Direct vendor APIs add ~20-30% improvement in recovery/readiness prediction quality. They are a "premium" upgrade, not a requirement.

---

## 5. Tier 1 — Health Connect Enhancements (No Backend Required)

Before adding any direct vendor integration, maximize what we extract from Health Connect.

### 5.1 Sleep Stage Extraction (Currently Missing)

We read `SleepSessionRecord` but only extract total duration. The stages are available:

| Stage | HC Enum | AI Use |
|---|---|---|
| AWAKE | `SleepSessionRecord.STAGE_TYPE_AWAKE` | Sleep efficiency = 1 - (awake/total) |
| LIGHT | `SleepSessionRecord.STAGE_TYPE_LIGHT` | Baseline sleep |
| DEEP | `SleepSessionRecord.STAGE_TYPE_DEEP` | Physical recovery — primary marker |
| REM | `SleepSessionRecord.STAGE_TYPE_REM` | Cognitive recovery, memory consolidation |
| OUT_OF_BED | `SleepSessionRecord.STAGE_TYPE_OUT_OF_BED` | Interruptions |

**New computed metrics from stages:**
- `deepSleepMinutes` — strongest single predictor of physical recovery
- `remSleepMinutes` — cognitive recovery
- `sleepEfficiency` — `(total - awake) / total` — better than raw duration
- `sleepScore` — weighted composite: `0.35 * duration_score + 0.30 * deep_pct_score + 0.20 * rem_pct_score + 0.15 * efficiency_score`

**Storage — extend `health_connect_sync`:**

| Column | Type | Notes |
|---|---|---|
| `deepSleepMinutes` | `Int?` | Deep sleep duration from HC stages |
| `remSleepMinutes` | `Int?` | REM duration from HC stages |
| `lightSleepMinutes` | `Int?` | Light sleep duration |
| `awakeMinutes` | `Int?` | Awake time during sleep session |
| `sleepEfficiency` | `Float?` | Computed: (total - awake) / total |

**Migration:** 5 `ALTER TABLE health_connect_sync ADD COLUMN` statements.

### 5.2 Respiratory Rate (Currently Deferred)

Add `RespiratoryRateRecord` to Health Connect reads. Available from Garmin, Samsung, Oura via HC.

**Storage — extend `health_connect_sync`:**

| Column | Type | Notes |
|---|---|---|
| `respiratoryRate` | `Float?` | Breaths per minute (overnight average) |

**HC permission:** `READ_RESPIRATORY_RATE`

### 5.3 VO2 Max as Time Series

Currently planned as single-point read (P4). Extend to store in `metric_log` with `MetricType.VO2_MAX` for trending.

### 5.4 Enhanced ReadinessEngine (v2)

With sleep stages + respiratory rate, upgrade the readiness formula:

**Current:** `HRV (0.45) + Sleep Duration (0.35) + RHR (0.20)` → 0-100 score

**Proposed:** `HRV (0.30) + Deep Sleep % (0.20) + Sleep Efficiency (0.15) + RHR (0.15) + Respiratory Rate (0.10) + Sleep Duration (0.10)` → 0-100 score

This brings HC-only readiness quality much closer to proprietary scores (Garmin Training Readiness, Oura Readiness).

---

## 6. Tier 2 — Direct Vendor API Integration (Requires Backend)

### 6.1 Architecture Overview

```
┌──────────────┐     Bluetooth     ┌──────────────────┐     Cloud Sync     ┌─────────────────┐
│  Wearable    │ ──────────────── │  Vendor App      │ ──────────────── │  Vendor Cloud   │
│  (Garmin/    │                   │  (Garmin Connect/ │                   │  (Garmin/Oura   │
│   Oura)      │                   │   Oura)           │                   │   servers)      │
└──────────────┘                   └──────────────────┘                   └────────┬────────┘
                                            │                                      │
                                            │ Also writes to                       │ Webhook push
                                            ▼                                      ▼
                                   ┌──────────────────┐              ┌──────────────────────┐
                                   │  Health Connect   │              │  Firebase Cloud       │
                                   │  (on phone)       │              │  Functions (backend)  │
                                   └────────┬─────────┘              └──────────┬───────────┘
                                            │                                   │
                                            │ Local read                        │ Writes to
                                            ▼                                   ▼
                                   ┌─────────────────────────────────────────────────────┐
                                   │                    PowerME App                       │
                                   │                                                     │
                                   │  ┌─────────────────┐    ┌─────────────────────────┐ │
                                   │  │ HC Data Layer    │    │ Firestore (vendor data) │ │
                                   │  │ (Tier 1 — all   │    │ (Tier 2 — Garmin/Oura   │ │
                                   │  │  users)          │    │  users who opted in)    │ │
                                   │  └────────┬────────┘    └───────────┬─────────────┘ │
                                   │           │                         │               │
                                   │           ▼                         ▼               │
                                   │  ┌──────────────────────────────────────────────┐   │
                                   │  │   WearableDataRepository (unified interface)  │   │
                                   │  │   Merges HC + vendor data, prefers vendor     │   │
                                   │  │   for metrics where vendor is more accurate   │   │
                                   │  └──────────────────────────────────────────────┘   │
                                   └─────────────────────────────────────────────────────┘
```

### 6.2 Backend: Firebase Cloud Functions

**Why Cloud Functions (not a custom server):**
- Already using Firebase (Auth + Firestore) — no new infrastructure vendor
- Serverless — scales to zero, pay-per-invocation, no server maintenance
- Firestore integration is native
- Supports HTTP triggers (for webhooks) and scheduled functions (for polling)
- Cost: negligible at low-to-moderate user counts (<$1/month for hundreds of users)

**Functions needed per vendor:**

| Function | Trigger | Purpose |
|---|---|---|
| `garminOAuthStart` | HTTP | Initiates OAuth 1.0a flow, returns authorization URL |
| `garminOAuthCallback` | HTTP | Receives OAuth callback, stores tokens in Firestore |
| `garminWebhook` | HTTP | Receives data push from Garmin, writes to user's Firestore doc |
| `garminDailyPoll` | Scheduled (fallback) | Polls Garmin API for users whose webhooks may have been missed |
| `ouraOAuthStart` | HTTP | OAuth 2.0 start |
| `ouraOAuthCallback` | HTTP | OAuth 2.0 callback |
| `ouraWebhook` | HTTP | Receives Oura data push |

**Firestore structure for vendor data:**

```
users/{uid}/
  wearable_connections/
    garmin/
      accessToken: encrypted
      accessTokenSecret: encrypted
      connectedAt: timestamp
      lastSyncAt: timestamp
    oura/
      accessToken: encrypted
      refreshToken: encrypted
      connectedAt: timestamp
      lastSyncAt: timestamp
  wearable_data/
    {date}/                          ← one doc per day
      source: "garmin" | "oura"
      trainingLoad: number?
      trainingStatus: string?        ← "PRODUCTIVE" | "DETRAINING" | etc.
      bodyBattery: { morning: int, evening: int, timeline: [...] }?
      stressAvg: int?
      stressTimeline: [...]?
      recoveryTimeHours: int?
      trainingReadiness: int?
      sleepScore: int?
      deepSleepMinutes: int?
      remSleepMinutes: int?
      respiratoryRate: float?
      vo2Max: float?
      continuousSpO2: { avg: float, min: float }?
      bodyTemperature: float?        ← Oura only
      hrvStatus: string?             ← "BALANCED" | "LOW" | "UNBALANCED"
      updatedAt: timestamp
```

### 6.3 Garmin Connect API — Integration Details

| Aspect | Detail |
|---|---|
| **API** | Garmin Health API (also called Wellness API) |
| **Auth** | OAuth 1.0a (older protocol — requires HMAC-SHA1 signing) |
| **Model** | Push (webhooks) + Pull (REST endpoints) |
| **Registration** | Apply at developer.garmin.com → business case review → approval (weeks) |
| **Data format** | JSON |
| **Key endpoints** | `/dailies`, `/activities`, `/sleeps`, `/stressDetails`, `/bodyBattery`, `/hrv`, `/pulseOx`, `/respiration`, `/trainingStatus`, `/trainingReadiness` |
| **Rate limits** | Per-app and per-user, standard REST limits |
| **Cost** | Free for approved partners |

**Garmin-specific data we'd extract:**

| Metric | Endpoint | Stored In | AI Use |
|---|---|---|---|
| Training Load (7-day) | `/activities` → `trainingLoadBalance` | `wearable_data/{date}` | Compare with our sRPE-based load; use as ground truth |
| Training Status | `/trainingStatus` | `wearable_data/{date}` | Override/supplement ReadinessEngine |
| Body Battery | `/bodyBattery` | `wearable_data/{date}` | Morning energy level → session intensity recommendation |
| Stress (all-day avg) | `/stressDetails` | `wearable_data/{date}` | Lifestyle stress signal (replaces self-report) |
| Recovery Time | `/activities` → `recoveryTime` | `wearable_data/{date}` | "You need 36 more hours before next hard session" |
| Training Readiness | `/trainingReadiness` | `wearable_data/{date}` | Composite readiness with more inputs than our engine |
| VO2 Max | `/userMetrics` | `wearable_data/{date}` + `metric_log` | Aerobic fitness trending |
| HRV Status | `/hrv` → `status` | `wearable_data/{date}` | Contextualized HRV (baseline-aware) |
| Respiration Rate | `/respiration` → `avgSleepRespirationValue` | `wearable_data/{date}` | Illness / overtraining early signal |

### 6.4 Oura API — Integration Details

| Aspect | Detail |
|---|---|
| **API** | Oura Cloud API v2 |
| **Auth** | OAuth 2.0 (modern, standard) |
| **Model** | Pull (REST) + Webhooks available |
| **Registration** | cloud.ouraring.com/docs — personal tokens available, OAuth for multi-user |
| **Data format** | JSON |
| **Key endpoints** | `/daily_readiness`, `/daily_sleep`, `/daily_activity`, `/heartrate`, `/sleep_time` |
| **Cost** | Free |

**Oura-specific data we'd extract:**

| Metric | Endpoint | AI Use |
|---|---|---|
| Readiness Score (0-100) | `/daily_readiness` | Richer readiness than HC-only |
| Sleep Score (0-100) | `/daily_sleep` → `score` | Better than computing from stages |
| Body Temperature deviation | `/daily_readiness` → `temperature_deviation` | Cycle phase confirmation (female), illness detection |
| Sleep contributors | `/daily_sleep` → `contributors` | Pinpoint what degraded sleep (latency, efficiency, deep, REM) |
| HRV (nightly) | `/daily_sleep` → `average_hrv` | Cross-validate with HC HRV |
| Respiratory Rate | `/daily_sleep` → `average_breath` | Illness / overtraining marker |

---

## 7. Unified Data Layer — WearableDataRepository

The app needs a single interface that merges HC data with optional vendor data, so the AI trainer doesn't need to know where data came from.

```kotlin
interface WearableDataRepository {
    
    // Core daily health snapshot — merges all available sources
    suspend fun getDailyHealthSnapshot(date: LocalDate): DailyHealthSnapshot
    
    // Check what data sources are connected
    fun getConnectedSources(): Flow<Set<WearableSource>>
    
    // Vendor connection management
    suspend fun connectGarmin(): Result<Unit>
    suspend fun connectOura(): Result<Unit>
    suspend fun disconnectVendor(source: WearableSource): Result<Unit>
}

data class DailyHealthSnapshot(
    // Always available (HC Tier 1)
    val sleepDurationMinutes: Int?,
    val hrv: Double?,
    val rhr: Int?,
    val steps: Int?,
    
    // Available with HC sleep stage extraction (Tier 1 enhanced)
    val deepSleepMinutes: Int?,
    val remSleepMinutes: Int?,
    val sleepEfficiency: Float?,
    val respiratoryRate: Float?,
    
    // Available only with Tier 2 vendor integration
    val trainingLoad: Double?,           // Garmin only
    val trainingStatus: String?,         // Garmin only
    val bodyBattery: Int?,               // Garmin only (morning value)
    val stressScore: Int?,               // Garmin only (daily avg)
    val recoveryTimeHours: Int?,         // Garmin only
    val vendorReadinessScore: Int?,      // Garmin Training Readiness or Oura Readiness
    val vendorSleepScore: Int?,          // Garmin or Oura Sleep Score
    val bodyTemperature: Float?,         // Oura only
    val hrvStatus: String?,              // Garmin HRV Status
    
    // Metadata
    val sources: Set<WearableSource>,    // Which sources contributed
    val date: LocalDate
)

enum class WearableSource {
    HEALTH_CONNECT,
    GARMIN,
    OURA
}
```

**Merge priority:** When the same metric is available from both HC and a vendor API, prefer the vendor API (higher accuracy/resolution). Example: Garmin's sleep score > our computed sleep score from HC stages.

---

## 8. App-Side UI Touchpoints

### 8.1 Wearable Connection Screen (Settings)

A new section in Settings: **"Connected Devices"**

```
┌─────────────────────────────────────────┐
│  Connected Devices                      │
│                                         │
│  ✅ Health Connect          Connected   │
│     Steps, Sleep, HRV, RHR, Weight     │
│                                         │
│  ⬜ Garmin Connect          Connect ›   │
│     Unlocks: Training Load, Body        │
│     Battery, Stress, Recovery Time      │
│                                         │
│  ⬜ Oura Ring               Connect ›   │
│     Unlocks: Readiness Score, Body      │
│     Temperature, Sleep Score            │
│                                         │
└─────────────────────────────────────────┘
```

Tapping "Connect" opens the vendor's OAuth flow in a browser/webview.

### 8.2 Readiness Card Enhancement

When vendor data is available, the ReadinessGaugeCard shows richer context:

- **HC only:** "Readiness: 72 — HRV normal, sleep 7.5h, RHR normal"
- **+ Garmin:** "Readiness: 72 — Training Load balanced, Body Battery 65, Stress low, Recovery: 12h remaining"
- **+ Oura:** "Readiness: 72 — Oura Readiness 74, Body Temp +0.1°C, Deep sleep 1h 45m"

### 8.3 Onboarding / Profile

During onboarding (after HC permissions), offer vendor connection:
"Do you use a Garmin, Oura, or other fitness wearable? Connect it for deeper insights."

---

## 9. Implementation Phasing

| Sub-phase | What | Effort | Backend? | Unlocks |
|---|---|---|---|---|
| **Tier 1a** | Extract sleep stages from existing HC `SleepSessionRecord` | XS | No | Deep sleep %, sleep efficiency, computed sleep score |
| **Tier 1b** | Add respiratory rate from HC (`RespiratoryRateRecord`) | XS | No | Illness/overtraining detection |
| **Tier 1c** | ReadinessEngine v2 (incorporate stages + respiratory rate) | S | No | Better readiness accuracy for ALL users |
| **Tier 1d** | VO2 Max as time series in `metric_log` | XS | No | Aerobic fitness trending |
| **Tier 2a** | Firebase Cloud Functions setup + Garmin OAuth | M | Yes (new) | Garmin authentication flow |
| **Tier 2b** | Garmin webhook receiver + Firestore storage | M | Yes | Garmin data flowing into app |
| **Tier 2c** | `WearableDataRepository` + merge logic | S | No | Unified data layer |
| **Tier 2d** | Settings UI (Connected Devices) | S | No | User-facing connection management |
| **Tier 2e** | Oura OAuth + webhook (follows same pattern as Garmin) | S | Yes | Oura data flowing into app |

**Recommendation:** Ship Tier 1 (a-d) with the AI Trainer Data Foundation (P7a). Tier 2 can be a separate phase (P8 or P7e) after the AI agent proves value with HC-only data.

---

## 10. Privacy & Security Considerations

| Concern | Mitigation |
|---|---|
| Vendor OAuth tokens stored in Firestore | Encrypt at rest using Firebase Security Rules + server-side encryption |
| Wearable health data in Firestore | Firestore security rules restrict access to `users/{uid}/**` — only the owner can read/write |
| Vendor data retention | Honor vendor ToS — delete data if user disconnects or deletes account |
| GDPR / data minimization | Only store metrics the AI trainer actually uses; no raw sensor dumps |
| Garmin approval risk | Garmin may deny apps they view as competitors — position PowerME as a complementary training app, not a Garmin Connect replacement |

---

## 11. Decision Log

| Decision | Rationale |
|---|---|
| HC as base layer, vendor APIs as optional | Maximum user reach; AI trainer must work without vendor integration |
| Firebase Cloud Functions for backend | Already on Firebase; serverless; near-zero cost; no new vendor |
| Garmin first, Oura second | Garmin has largest serious fitness market share + richest proprietary data |
| Samsung — HC only (no direct API) | HC covers most Samsung data; Privileged SDK is restricted and low ROI |
| Xiaomi/Amazfit — HC only | Zepp API is limited; HC covers the basics |
| Huawei — HC only | HMS ecosystem restrictions; HC support improving |
| Apple Watch — out of scope | iOS only; revisit if app goes cross-platform |
| Whoop/Polar — out of scope | Niche market; can add later if demand warrants |
| Sleep stage extraction before vendor APIs | Biggest single quality improvement for readiness, zero infrastructure cost |
| Computed sleep score as fallback | 75% quality of vendor sleep scores; good enough for HC-only users |

---

## 12. What This Spec Does NOT Cover

| Topic | Why Separate |
|---|---|
| AI agent decision logic using wearable data | Separate AI agent spec |
| ReadinessEngine v2 algorithm details | Separate spec or update to TRENDS_SPEC.md |
| Firebase Cloud Functions setup / deployment | Infrastructure spec |
| Garmin Developer Program application | Business/admin task, not engineering spec |
| UI design for Connected Devices screen | UI spec or implement with existing patterns |

---

*Spec created: 2026-04-15*

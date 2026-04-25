# Gym Profiles

| Field | Value |
|---|---|
| **Type** | Epic |
| **Phase** | P10 |
| **Status** | `not-started` |
| **Children** | 0 tasks |
| **Rollup** | — |

---

## Overview

Gym Profiles lets users create or join a shared gym space within PowerME. Members of the same gym can see each other's shared equipment lists, discover and import routines posted by other members, and find gyms nearby by name or GPS location.

This is a social/community layer built on top of the existing Firestore backend. It is intentionally optional — solo users who never join a gym are completely unaffected by this feature.

---

## Architecture

> ⚠️ **Implementation requires Opus + plan mode.** All child tasks must begin with a plan-mode session before any code is written.
> ⚠️ **UI/UX design requires `/ui-ux-pro-max`** before any Compose work begins on gym discovery, profile, or member screens.

- **Firestore `gyms` collection** — one document per gym; subcollection `members` (userId → role); `equipment` array; `routines` subcollection or array of routine refs.
- **Gym identity** — unique gym ID (UUID), human-readable name, optional GPS coordinates (lat/lng stored on creation), optional address string.
- **Discovery** — by exact name search OR by proximity (Haversine distance on client using stored lat/lng). No server-side geo-query required for MVP.
- **Membership model** — roles: `owner` (creator), `member`. Owner can remove members; members can leave. No approval flow for MVP (open join by name/code).
- **Equipment sharing** — gym's shared equipment list is read-only to members; it supplements (does not replace) the user's personal equipment prefs.
- **Routine sharing** — members can publish a routine to the gym; other members can import it as a copy into their own routine list.
- **Privacy** — no user PII shared via gym (display name only, not email). Location permission required only for "Find nearby gyms" flow.
- **Room** — no local DB entity for remote gym data (fetched on demand). Only the user's `gymId` membership ref is persisted locally (AppSettingsDataStore or User entity).

---

## Phasing

| Tier | Task | Description |
|---|---|---|
| T0 | `gym_data_model` | Firestore schema design + GymRepository + GymDao (local membership ref only) |
| T1 | `gym_create_profile` | Create gym screen — name, optional location, equipment list |
| T1 | `gym_join` | Join gym by name search or nearby GPS discovery |
| T2 | `gym_member_view` | Gym home screen — member list, shared equipment, posted routines |
| T2 | `gym_share_routine` | Publish a routine to gym / import a gym routine as your own copy |
| T3 | `gym_settings_card` | Settings card showing current gym membership + Leave/Change gym action |

Child tasks are filed separately via `/file_task epic:gym_profiles <description>`.

---

## Invariants

- Joining a gym is always **opt-in** — the app must never auto-assign a user to a gym.
- Gym equipment list supplements but **never overwrites** the user's personal equipment preferences.
- Importing a routine from a gym always creates an **independent copy** — changes to the original do not propagate.
- Location data (lat/lng) is collected only with explicit `ACCESS_COARSE_LOCATION` permission and stored in Firestore only when the user creates or searches for gyms.
- All gym Firestore writes must include `updatedAt` for LWW conflict resolution consistency with the rest of the sync layer.
- No user email or Firebase UID is exposed to other gym members — only a display name.

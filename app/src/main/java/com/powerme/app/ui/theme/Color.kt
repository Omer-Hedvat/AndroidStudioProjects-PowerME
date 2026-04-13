package com.powerme.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// PowerME v6.0 Design System — Pro Tracker Palette

// ── Core Tokens ───────────────────────────────────────────────────────────────
val ProBackground  = Color(0xFF101010)   // True neutral near-black — page bg
val ProSurface     = Color(0xFF1C1C1C)   // Neutral dark grey — cards / bottom bars
val ProSurfaceVar  = Color(0xFF282828)   // Neutral lifted surface — chips / rows
val ProViolet      = Color(0xFF9B7DDB)   // Brand purple — desaturated lavender-violet
val ProMagenta     = Color(0xFF9E6B8A)   // Secondary accent — dusty rose / mauve
val ProCloudGrey   = Color(0xFFEDEDEF)   // High-emphasis text — neutral near-white
val ProSubGrey     = Color(0xFFA0A0A0)   // Medium-emphasis text — neutral grey
val ProError       = Color(0xFFE05555)   // Error / destructive — desaturated red

// ── Border tokens ─────────────────────────────────────────────────────────────
val ProOutline     = Color(0xFF383838)   // Neutral unfocused input border
val ProOutlineSoft = Color(0xFF2E2E2E)   // Softer variant for outlineVariant slot

// ── Semantic timer colors ─────────────────────────────────────────────────────
val TimerGreen  = Color(0xFF4CC990)   // Desaturated emerald — finish workout, completed checkmarks
val TimerRed    = Color(0xFFE04458)   // Desaturated red — rest state indicator
val NeonPurple  = Color(0xFFBB86FC)   // Bright neon purple — active rest timer accent

// ── Semantic color for Form Cues banner ───────────────────────────────────────
val FormCuesGold = Color(0xFF5A4D1A)   // Muted gold — not representable by M3 token

// ── Readiness gauge ──────────────────────────────────────────────────────────
val ReadinessAmber = Color(0xFFFFB74D)  // Moderate readiness tier — warm amber

// ── Light mode tokens ─────────────────────────────────────────────────────────
val Slate50   = Color(0xFFF8FAFC)
val Slate100  = Color(0xFFF1F5F9)
val Slate900  = Color(0xFF0F172A)
val LightBackground = Slate50
val LightSurface    = Slate100
val LightOnSurface  = Slate900
val LightPrimary    = Color(0xFF6B3FA0)   // Darkened violet — WCAG AA on white (~6.5:1)
val LightSecondary  = Color(0xFF7D3B65)   // Darkened berry — WCAG AA on white (~6:1)
val LightError      = Color(0xFFB3261E)   // M3 standard light error

// ── Superset spine color palette ─────────────────────────────────────────────
val SupersetPalette = listOf(
    Color(0xFFE91E8C),  // Pink
    Color(0xFF4CAF50),  // Green
    Color(0xFFFFEB3B),  // Yellow
    Color(0xFFFF9800),  // Orange
    Color(0xFF00BCD4),  // Cyan
    Color(0xFF9C27B0),  // Purple
    Color(0xFFFF5722),  // Deep Orange
    Color(0xFF03A9F4),  // Light Blue
)

fun supersetColor(groupId: String?): Color =
    if (groupId == null) Color.Transparent
    else SupersetPalette[abs(groupId.hashCode()) % SupersetPalette.size]

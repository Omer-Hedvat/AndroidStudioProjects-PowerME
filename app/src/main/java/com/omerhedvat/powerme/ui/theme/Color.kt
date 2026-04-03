package com.omerhedvat.powerme.ui.theme

import androidx.compose.ui.graphics.Color

// PowerME v4.0 Design System — Pro Tracker Palette

// ── Core Tokens ───────────────────────────────────────────────────────────────
val StremioBackground  = Color(0xFF000000)   // Pure OLED black — page bg
val StremioSurface     = Color(0xFF252525)   // Near-black — cards / bottom bars (Strong-style)
val StremioSurfaceVar  = Color(0xFF303030)   // Neutral lifted surface — chips / rows
val StremioInputPill   = Color(0xFF2A2A2A)   // Neutral input field bg (no purple tint)
val StremioViolet      = Color(0xFFA061FF)   // Brand purple — accent only; brighter vs black bg
val StremioMagenta     = Color(0xFFB3478C)   // Secondary accent — Berry/Magenta
val StremioCloudGrey   = Color(0xFFFFFFFF)   // High-emphasis text — pure white
val StremioSubGrey     = Color(0xFFA0A0A0)   // Medium-emphasis text
val StremioError       = Color(0xFFFF4444)   // Error / destructive

// ── New token ─────────────────────────────────────────────────────────────────
val ProOutline         = Color(0xFF3D3D3D)   // Neutral unfocused input border

// ── Semantic timer colors (unchanged) ─────────────────────────────────────────
val TimerGreen = Color(0xFF34D399)   // Emerald400 — high-contrast work state
val TimerRed   = Color(0xFFFF1744)   // rest state

// ── Semantic color for Form Cues banner ───────────────────────────────────────
val FormCuesGold = Color(0xFF5A4D1A)   // Muted gold — not representable by M3 token

// ── Light mode tokens ─────────────────────────────────────────────────────────
val Slate50   = Color(0xFFF8FAFC)
val Slate100  = Color(0xFFF1F5F9)
val Slate900  = Color(0xFF0F172A)
val LightBackground = Slate50
val LightSurface    = Slate100
val LightOnSurface  = Slate900

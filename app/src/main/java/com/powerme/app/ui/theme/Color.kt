package com.powerme.app.ui.theme

import androidx.compose.ui.graphics.Color

// PowerME v5.0 Design System — Pro Tracker Palette

// ── Core Tokens ───────────────────────────────────────────────────────────────
val ProBackground  = Color(0xFF0F0D13)   // Near-black with subtle purple warmth — page bg
val ProSurface     = Color(0xFF1C1A24)   // Purple-tinted dark — cards / bottom bars
val ProSurfaceVar  = Color(0xFF28253A)   // Purple-tinted lifted surface — chips / rows
val ProInputPill   = Color(0xFF221F30)   // Input field bg (no M3 mapping, kept for clarity)
val ProViolet      = Color(0xFF9B7DDB)   // Brand purple — desaturated lavender-violet
val ProMagenta     = Color(0xFF9E6B8A)   // Secondary accent — dusty rose / mauve
val ProCloudGrey   = Color(0xFFE8E4F0)   // High-emphasis text — lavender-tinted off-white
val ProSubGrey     = Color(0xFF9E99AB)   // Medium-emphasis text — purple-tinted
val ProError       = Color(0xFFE05555)   // Error / destructive — desaturated red

// ── Border token ──────────────────────────────────────────────────────────────
val ProOutline     = Color(0xFF3A3650)   // Purple-tinted unfocused input border

// ── Semantic timer colors ─────────────────────────────────────────────────────
val TimerGreen = Color(0xFF4CC990)   // Desaturated emerald — finish workout, completed checkmarks
val TimerRed   = Color(0xFFE04458)   // Desaturated red — rest state indicator

// ── Semantic color for Form Cues banner ───────────────────────────────────────
val FormCuesGold = Color(0xFF5A4D1A)   // Muted gold — not representable by M3 token

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

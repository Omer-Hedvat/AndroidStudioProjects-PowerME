package com.omerhedvat.powerme.ui.theme

import androidx.compose.ui.graphics.Color

// PowerME v3.0 Design System — Stremio 'Pure Performance' Palette

// ── Stremio Core Tokens ───────────────────────────────────────────────────────
val StremioBackground  = Color(0xFF0F0F1E)   // Deep Indigo-Black — page background
val StremioSurface     = Color(0xFF191932)   // Deep Purple — cards / bottom bars
val StremioSurfaceVar  = Color(0xFF1F1F3E)   // Slightly lighter surface for chips / rows
val StremioInputPill   = Color(0xFF2C2C4E)   // v20 — set input field background pill
val StremioViolet      = Color(0xFF7B5BE4)   // Primary accent — Stremio Violet
val StremioMagenta     = Color(0xFFB3478C)   // Secondary accent — Berry/Magenta
val StremioCloudGrey   = Color(0xFFE1E1E6)   // Primary on-surface text
val StremioSubGrey     = Color(0xFF8E8E9E)   // Secondary / hint text
val StremioError       = Color(0xFFFF4444)   // Error / destructive

// ── Semantic timer colors (unchanged) ─────────────────────────────────────────
val TimerGreen = Color(0xFF34D399)   // Emerald400 — high-contrast work state
val TimerRed   = Color(0xFFFF1744)   // rest state

// ── Legacy aliases — mapped to nearest Stremio tokens for source compatibility ─
// These keep existing composable imports compiling without change.
val DeepNavy    = StremioBackground    // was Slate950
val NavySurface = StremioSurface       // was Slate900
val SlateGrey   = StremioSurfaceVar    // was Slate800
val OledBlack   = StremioBackground    // was Slate950
val LightGrey   = StremioSubGrey       // secondary text

// ── Primary accent alias (old NeonBlue / ElectricBlue → Stremio Violet) ───────
val NeonBlue     = StremioViolet        // was Facebook Blue #1877F2
val ElectricBlue = StremioMagenta       // was #166FE5; now Berry/Magenta secondary

// ── Slate text alias for theme tokens ─────────────────────────────────────────
val Slate200  = StremioCloudGrey        // dark-mode primary text

// ── Light mode tokens (kept; light theme unchanged) ───────────────────────────
val Slate50   = Color(0xFFF8FAFC)
val Slate100  = Color(0xFFF1F5F9)
val Slate900  = Color(0xFF0F172A)
val LightBackground = Slate50
val LightSurface    = Slate100
val LightOnSurface  = Slate900

// ── Medical restriction warning colors (Noa's injury-ledger overlay) ──────────
val MedicalAmber          = Color(0xFFFFC107)   // YELLOW list text — Material Amber 500
val MedicalAmberContainer = Color(0xFFFFA000)   // YELLOW list card background — Material Amber 700

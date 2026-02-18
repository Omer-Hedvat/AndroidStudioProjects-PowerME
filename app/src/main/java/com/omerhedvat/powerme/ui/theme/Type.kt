package com.omerhedvat.powerme.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// JetBrains Mono — used for timer displays and numeric data.
// To enable Google Fonts downloading, add R.array.com_google_android_gms_fonts_certs
// via Android Studio's "Add downloadable font" wizard, then replace this with:
//
//   private val provider = GoogleFont.Provider(
//       providerAuthority = "com.google.android.gms.fonts",
//       providerPackage  = "com.google.android.gms",
//       certificates     = R.array.com_google_android_gms_fonts_certs
//   )
//   val JetBrainsMono = FontFamily(Font(GoogleFont("JetBrains Mono"), provider))
//
// For now, system monospace is used as a safe, always-available fallback.
val JetBrainsMono = FontFamily.Monospace

// Usage constant — apply to timer displays and stat numbers.
val MonoTextStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontFeatureSettings = "tnum"
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

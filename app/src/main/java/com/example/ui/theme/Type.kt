package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val NewsreaderFontFamily = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal),
    Font(R.font.newsreader, FontWeight.Medium),
    Font(R.font.newsreader, FontWeight.SemiBold)
)

// STRICT REQUIREMENT: Kalam is the ONLY handwriting typeface used for generated notes and PDF export
val KalamFontFamily = FontFamily(
    Font(R.font.kalam, FontWeight.Normal),
    Font(R.font.kalam, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.4).sp
    ),
    displayMedium = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.15).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.12.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.12.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.35.sp
    )
)

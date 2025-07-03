package com.Curtis.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Example: Use a bold, geometric sans-serif font if available, else default
val PremiumFont = FontFamily.Default // Replace with custom font if added

// Set of Material typography styles to start with
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = PremiumFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        letterSpacing = 2.sp,
        color = HighContrastWhite
    ),
    headlineMedium = TextStyle(
        fontFamily = PremiumFont,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 1.sp,
        color = HighContrastWhite
    ),
    bodyLarge = TextStyle(
        fontFamily = PremiumFont,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        letterSpacing = 0.5.sp,
        color = HighContrastWhite
    ),
    bodyMedium = TextStyle(
        fontFamily = PremiumFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
        color = HighContrastWhite
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
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
    */
)
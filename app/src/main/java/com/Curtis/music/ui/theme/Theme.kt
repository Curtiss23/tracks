package com.Curtis.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonPink,
    secondary = NeonBlue,
    tertiary = NeonGreen,
    background = DeepBlack,
    surface = ElectricPurple,
    onPrimary = DeepBlack,
    onSecondary = DeepBlack,
    onTertiary = DeepBlack,
    onBackground = AcidYellow,
    onSurface = NeonPink
)

private val LightColorScheme = lightColorScheme(
    primary = NeonBlue,
    secondary = NeonPink,
    tertiary = NeonGreen,
    background = AcidYellow,
    surface = HotOrange,
    onPrimary = DeepBlack,
    onSecondary = DeepBlack,
    onTertiary = DeepBlack,
    onBackground = DeepBlack,
    onSurface = NeonBlue
)

@Composable
fun MusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
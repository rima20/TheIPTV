package com.example.theiptv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TheIPTVTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentBlue,
            onPrimary = Night,
            secondary = AccentTeal,
            onSecondary = Night,
            tertiary = AccentAmber,
            background = Night,
            onBackground = TextPrimary,
            surface = NightSurface,
            onSurface = TextPrimary,
            surfaceVariant = NightSurfaceAlt,
            onSurfaceVariant = TextMuted,
            error = Danger,
            onError = TextPrimary
        ),
        typography = Typography,
        content = content
    )
}

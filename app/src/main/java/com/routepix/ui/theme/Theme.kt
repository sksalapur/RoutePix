package com.routepix.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal30,
    secondary = Orange80,
    onSecondary = Orange30,
    tertiary = Blue80,
    onTertiary = Blue40,
    error = ErrorRedLight,
    onError = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Neutral95,
    secondary = Orange40,
    onSecondary = Neutral95,
    tertiary = Blue40,
    onTertiary = Neutral95,
    error = ErrorRed,
    onError = Neutral95
)

@Composable
fun RoutepixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}


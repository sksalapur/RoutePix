package com.routepix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.routepix.R

// ── Outfit via Google Fonts (Downloadable Font — no TTF needed) ────────────
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

private val outfitFont = GoogleFont("Outfit")

val OutfitFamily = FontFamily(
    Font(googleFont = outfitFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = outfitFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = outfitFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = outfitFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = outfitFont, fontProvider = provider, weight = FontWeight.Bold),
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Bold,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
)

// ── Colour schemes ─────────────────────────────────────────────────────────

// AMOLED Dark — pure black background, minimal elevation contrast
private val DarkColorScheme = darkColorScheme(
    primary              = Indigo80,
    onPrimary            = Indigo20,
    primaryContainer     = Indigo40,
    onPrimaryContainer   = Indigo80,

    secondary            = Slate80,
    onSecondary          = Slate20,
    secondaryContainer   = Slate20,
    onSecondaryContainer = Slate80,

    tertiary             = Mauve80,
    onTertiary           = Mauve40,
    tertiaryContainer    = Mauve40,
    onTertiaryContainer  = Mauve80,

    error                = ErrorLight,
    onError              = ErrorDark,
    errorContainer       = ErrorDark,
    onErrorContainer     = ErrorLight,

    // AMOLED pure black surfaces
    surface              = AmoledBlack,
    onSurface            = Color(0xFFE6E0E9),
    surfaceVariant       = AmoledSurf03,
    onSurfaceVariant     = NeutralVar80,
    background           = AmoledBlack,
    onBackground         = Color(0xFFE6E0E9),
    outline              = Color(0xFF49454F),
    outlineVariant       = Color(0xFF2A2831),
)

// Professional Light — consistent indigo + slate
private val LightColorScheme = lightColorScheme(
    primary              = Indigo40,
    onPrimary            = Surface99,
    primaryContainer     = Indigo80,
    onPrimaryContainer   = Indigo20,

    secondary            = Slate40,
    onSecondary          = Surface99,
    secondaryContainer   = Slate80,
    onSecondaryContainer = Slate20,

    tertiary             = Mauve40,
    onTertiary           = Surface99,
    tertiaryContainer    = Mauve80,
    onTertiaryContainer  = Mauve40,

    error                = ErrorDark,
    onError              = Surface99,
    errorContainer       = ErrorLight,
    onErrorContainer     = ErrorDark,

    surface              = Surface99,
    onSurface            = Color(0xFF1C1B1F),
    surfaceVariant       = NeutralVar90,
    onSurfaceVariant     = NeutralVar30,
    background           = Surface99,
    onBackground         = Color(0xFF1C1B1F),
    outline              = NeutralVar30,
    outlineVariant       = NeutralVar90,
)

@Composable
fun RoutepixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = AppTypography,
        content     = content
    )
}

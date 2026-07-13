package com.yuukias.seminararc.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val BrandNavy = Color(0xFF0D2A5C)
private val BrandNavyDeep = Color(0xFF081B3D)
private val BrandCyan = Color(0xFF1AA7D8)
private val BrandCyanSoft = Color(0xFFD4F2FB)
private val BrandMist = Color(0xFFF7F9FC)

val SeminarArcLightColorScheme = lightColorScheme(
    primary = BrandNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = BrandNavyDeep,
    secondary = Color(0xFF1698C7),
    onSecondary = Color.White,
    secondaryContainer = BrandCyanSoft,
    onSecondaryContainer = Color(0xFF08384A),
    tertiary = Color(0xFF8F6A2A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E3BF),
    onTertiaryContainer = Color(0xFF3A2710),
    background = BrandMist,
    onBackground = Color(0xFF162033),
    surface = Color(0xFFFCFDFF),
    onSurface = Color(0xFF162033),
    surfaceVariant = Color(0xFFEAF0F7),
    onSurfaceVariant = Color(0xFF526176),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF738397),
    outlineVariant = Color(0xFFC3CEDA),
    inverseSurface = Color(0xFF2A3140),
    inverseOnSurface = Color(0xFFEEF3FB),
    surfaceTint = BrandNavy,
    scrim = Color.Black,
)

val SeminarArcDarkColorScheme = darkColorScheme(
    primary = Color(0xFFAFC7FF),
    onPrimary = Color(0xFF002655),
    primaryContainer = Color(0xFF003A7D),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Color(0xFF7FD8F2),
    onSecondary = Color(0xFF003545),
    secondaryContainer = Color(0xFF004D62),
    onSecondaryContainer = BrandCyanSoft,
    tertiary = Color(0xFFE7C78C),
    onTertiary = Color(0xFF4F3800),
    tertiaryContainer = Color(0xFF6D4F16),
    onTertiaryContainer = Color(0xFFF7E3BF),
    background = Color(0xFF0E1520),
    onBackground = Color(0xFFE8EDF6),
    surface = Color(0xFF101926),
    onSurface = Color(0xFFE8EDF6),
    surfaceVariant = Color(0xFF223043),
    onSurfaceVariant = Color(0xFFBCC8D8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8D9AAF),
    outlineVariant = Color(0xFF3B485A),
    inverseSurface = Color(0xFFE8EDF6),
    inverseOnSurface = Color(0xFF1E2633),
    surfaceTint = Color(0xFFAFC7FF),
    scrim = Color.Black,
)

data class SeminarArcExtendedColors(
    val recordingActive: Color,
    val recordingPaused: Color,
    val markMoment: Color,
    val slidePhoto: Color,
    val question: Color,
    val quickNote: Color,
    val processing: Color,
)

val LightExtendedColors = SeminarArcExtendedColors(
    recordingActive = Color(0xFFD92D20),
    recordingPaused = Color(0xFFD98C1F),
    markMoment = BrandNavy,
    slidePhoto = BrandCyan,
    question = Color(0xFFA56A12),
    quickNote = Color(0xFF3C7A5E),
    processing = Color(0xFF3A78D0),
)

val DarkExtendedColors = SeminarArcExtendedColors(
    recordingActive = Color(0xFFFF6D5E),
    recordingPaused = Color(0xFFFFC266),
    markMoment = Color(0xFFAFC7FF),
    slidePhoto = Color(0xFF7FD8F2),
    question = Color(0xFFFFD27A),
    quickNote = Color(0xFF8DD1B0),
    processing = Color(0xFF8FB7FF),
)

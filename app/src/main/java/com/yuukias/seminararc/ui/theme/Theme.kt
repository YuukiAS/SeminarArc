package com.yuukias.seminararc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun SeminarArcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> SeminarArcDarkColorScheme
        else -> SeminarArcLightColorScheme
    }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalSeminarArcSpacing provides SeminarArcSpacing(),
        LocalSeminarArcExtendedColors provides extendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SeminarArcTypography,
            shapes = SeminarArcShapes,
            content = content,
        )
    }
}

object SeminarArcThemeTokens {
    val spacing: SeminarArcSpacing
        @Composable
        get() = LocalSeminarArcSpacing.current

    val extendedColors: SeminarArcExtendedColors
        @Composable
        get() = LocalSeminarArcExtendedColors.current
}

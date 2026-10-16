/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.apptheme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.patrickgold.florisboard.app.AppTheme


/**
 * The app's colours: Lavender, in a light and a dark version. Fixed on purpose. They don't follow the
 * wallpaper, an accent picker or the mic look picked on the Style tab, so every screen looks the same
 * whatever button the user wears; the looks adapt to these colours instead (see MicCharmPainter).
 */
@Composable
fun getColorScheme(
    theme: AppTheme,
): ColorScheme {
    return when (theme) {
        AppTheme.AUTO -> if (isSystemInDarkTheme()) LavenderDark else LavenderLight
        AppTheme.AUTO_AMOLED -> if (isSystemInDarkTheme()) LavenderDark.amoled() else LavenderLight
        AppTheme.LIGHT -> LavenderLight
        AppTheme.DARK -> LavenderDark
        AppTheme.AMOLED_DARK -> LavenderDark.amoled()
    }
}

private val LavenderLight = lightColorScheme(
    primary = Color(0xFF6A3D9A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDCFF),
    onPrimaryContainer = Color(0xFF2A0E4A),
    inversePrimary = Color(0xFFD5BBFF),
    secondary = Color(0xFF655A6F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECDDF6),
    onSecondaryContainer = Color(0xFF20182A),
    // Blossom pink, the design's second accent.
    tertiary = Color(0xFF9C2B57),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFB3C8),
    onTertiaryContainer = Color(0xFF4A0A22),
    background = Color(0xFFFBF6FF),
    onBackground = Color(0xFF1E1726),
    surface = Color(0xFFFBF6FF),
    onSurface = Color(0xFF1E1726),
    surfaceVariant = Color(0xFFEADFF2),
    onSurfaceVariant = Color(0xFF5E5468),
    surfaceTint = Color(0xFF6A3D9A),
    inverseSurface = Color(0xFF332B3C),
    inverseOnSurface = Color(0xFFF6EEFC),
    error = Color(0xFFC2185B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE0E8),
    onErrorContainer = Color(0xFF8A1538),
    outline = Color(0xFF8A7F96),
    outlineVariant = Color(0xFFE3D6EF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF6FF),
    surfaceDim = Color(0xFFDDD4E6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6EFFD),
    surfaceContainer = Color(0xFFF1E8FB),
    surfaceContainerHigh = Color(0xFFEBE1F6),
    surfaceContainerHighest = Color(0xFFE5DAF1),
)

private val LavenderDark = darkColorScheme(
    primary = Color(0xFFCFB3F5),
    onPrimary = Color(0xFF3A1563),
    primaryContainer = Color(0xFF51307E),
    onPrimaryContainer = Color(0xFFEBDCFF),
    inversePrimary = Color(0xFF6A3D9A),
    secondary = Color(0xFFD0C1DA),
    onSecondary = Color(0xFF362D40),
    secondaryContainer = Color(0xFF4D4357),
    onSecondaryContainer = Color(0xFFECDDF6),
    tertiary = Color(0xFFFFB1C8),
    onTertiary = Color(0xFF5E1133),
    tertiaryContainer = Color(0xFF7C2D4B),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF16121C),
    onBackground = Color(0xFFECE4F5),
    surface = Color(0xFF16121C),
    onSurface = Color(0xFFECE4F5),
    surfaceVariant = Color(0xFF4A4153),
    onSurfaceVariant = Color(0xFFB3A8C0),
    surfaceTint = Color(0xFFCFB3F5),
    inverseSurface = Color(0xFFECE4F5),
    inverseOnSurface = Color(0xFF332B3C),
    error = Color(0xFFFF8FB1),
    onError = Color(0xFF5E1133),
    errorContainer = Color(0xFF7C2D4B),
    onErrorContainer = Color(0xFFFFD9E3),
    outline = Color(0xFF978CA3),
    outlineVariant = Color(0xFF4A4153),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3D3546),
    surfaceDim = Color(0xFF16121C),
    surfaceContainerLowest = Color(0xFF110D16),
    surfaceContainerLow = Color(0xFF1E1826),
    surfaceContainer = Color(0xFF221B2B),
    surfaceContainerHigh = Color(0xFF2D2536),
    surfaceContainerHighest = Color(0xFF383041),
)

fun ColorScheme.amoled(): ColorScheme {
    return this.copy(background = Color.Black, surface = Color.Black)
}

@Composable
fun FlorisAppTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val colors = getColorScheme(theme = theme)

    val darkTheme =
        theme == AppTheme.DARK
            || theme == AppTheme.AMOLED_DARK
            || (theme == AppTheme.AUTO && isSystemInDarkTheme())
            || (theme == AppTheme.AUTO_AMOLED && isSystemInDarkTheme())

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}

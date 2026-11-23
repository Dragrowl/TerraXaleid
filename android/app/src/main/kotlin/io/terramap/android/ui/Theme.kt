// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.terramap.android.data.DarkVariant
import io.terramap.android.data.ThemeMode

// The palette is deliberately close to the grey axis: every tone below sits at a chroma of roughly two
// to four percent, so the UI reads as neutral graphite with a faint green-grey cast instead of a warm
// paper white or a green-tinted black. Accent saturations are kept low for the same reason - the name
// and the chips are what should stand out, not the surfaces behind them.
private val Graphite = Color(0xFF4F5A51)
private val GraphiteLight = Color(0xFFB4C3B6)
private val WarmGrey = Color(0xFF5B5851)
private val WarmGreyLight = Color(0xFFC7C2B8)
private val CoolGrey = Color(0xFF525C60)
private val CoolGreyLight = Color(0xFFB6C5C9)

private val LightScheme = lightColorScheme(
    primary = Graphite,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3DD),
    onPrimaryContainer = Color(0xFF10140F),
    secondary = WarmGrey,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E0D9),
    onSecondaryContainer = Color(0xFF191713),
    tertiary = CoolGrey,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE3E6),
    onTertiaryContainer = Color(0xFF0B1113),
    background = Color(0xFFF6F6F4),
    onBackground = Color(0xFF1A1A18),
    surface = Color(0xFFF6F6F4),
    onSurface = Color(0xFF1A1A18),
    surfaceVariant = Color(0xFFE8E8E4),
    onSurfaceVariant = Color(0xFF494944),
    outline = Color(0xFF7A7A73),
    outlineVariant = Color(0xFFCFCFC9),
)

private val DarkScheme = darkColorScheme(
    primary = GraphiteLight,
    onPrimary = Color(0xFF1F2A21),
    primaryContainer = Color(0xFF3A463B),
    onPrimaryContainer = Color(0xFFD5E3D7),
    secondary = WarmGreyLight,
    onSecondary = Color(0xFF2A2823),
    secondaryContainer = Color(0xFF45423B),
    onSecondaryContainer = Color(0xFFE9E4DA),
    tertiary = CoolGreyLight,
    onTertiary = Color(0xFF1D2A2D),
    tertiaryContainer = Color(0xFF37474B),
    onTertiaryContainer = Color(0xFFD6E5E9),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE3E3DF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE3E3DF),
    surfaceVariant = Color(0xFF232322),
    onSurfaceVariant = Color(0xFFC7C7C1),
    outline = Color(0xFF8F8F88),
    outlineVariant = Color(0xFF3A3A38),
)

/** Same palette, but every base surface is pure black so an OLED panel actually switches pixels off. */
private val OledDarkScheme = DarkScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0B0B0B),
    onSurfaceVariant = Color(0xFFC7C7C1),
)

/**
 * @param mode forced by the config file; [ThemeMode.Auto] follows the system setting.
 * @param darkVariant only meaningful when the resolved theme ends up dark.
 */
@Composable
fun TerramapTheme(
    mode: ThemeMode = ThemeMode.Auto,
    darkVariant: DarkVariant = DarkVariant.Standard,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (mode) {
        ThemeMode.Auto -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = when {
        // Dynamic colour only when nothing is forced: a forced theme has to look the same on every
        // device, which is exactly what the screenshot captures depend on.
        mode == ThemeMode.Auto && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme && darkVariant == DarkVariant.Oled -> OledDarkScheme
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

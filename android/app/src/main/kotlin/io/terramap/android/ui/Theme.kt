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

private val Leaf = Color(0xFF4C6B43)
private val LeafDark = Color(0xFFB6D3A8)
private val Soil = Color(0xFF7A5A3A)
private val SoilDark = Color(0xFFE4C4A2)
private val Stone = Color(0xFF46626B)
private val StoneDark = Color(0xFFA8CBD4)

private val LightScheme = lightColorScheme(
    primary = Leaf,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE9C1),
    onPrimaryContainer = Color(0xFF0B2005),
    secondary = Soil,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1DCC5),
    onSecondaryContainer = Color(0xFF2A1808),
    tertiary = Stone,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCBE7EF),
    onTertiaryContainer = Color(0xFF001F26),
    background = Color(0xFFFBF7F2),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFBF7F2),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEFEAE1),
    onSurfaceVariant = Color(0xFF4A463E),
)

private val DarkScheme = darkColorScheme(
    primary = LeafDark,
    onPrimary = Color(0xFF14300C),
    primaryContainer = Color(0xFF35502C),
    onPrimaryContainer = Color(0xFFCDE9C1),
    secondary = SoilDark,
    onSecondary = Color(0xFF40290F),
    secondaryContainer = Color(0xFF5A4026),
    onSecondaryContainer = Color(0xFFF1DCC5),
    tertiary = StoneDark,
    onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF2C4A52),
    onTertiaryContainer = Color(0xFFCBE7EF),
    background = Color(0xFF14170F),
    onBackground = Color(0xFFE6E2D9),
    surface = Color(0xFF14170F),
    onSurface = Color(0xFFE6E2D9),
    surfaceVariant = Color(0xFF2A2F26),
    onSurfaceVariant = Color(0xFFC9C4BA),
)

/** Same palette, but every base surface is pure black so an OLED panel actually switches pixels off. */
private val OledDarkScheme = DarkScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFFC9C4BA),
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

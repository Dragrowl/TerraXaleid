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
)

@Composable
fun TerramapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

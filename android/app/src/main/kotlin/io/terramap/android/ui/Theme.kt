// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

/**
 * Same palette, but the page background is pure black so an OLED panel switches those pixels off.
 * Cards keep a slightly lifted fill (#0B0B0B): a fully black card on a fully black page loses its
 * edge, and the point of the variant is the background, not a uniformly black sheet.
 */
private val OledDarkScheme = DarkScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0B0B0B),
)

/** Resolves the config into a concrete scheme. Dynamic colour is intentionally not used. */
private fun schemeFor(mode: ThemeMode, darkVariant: DarkVariant, systemDark: Boolean): ColorScheme {
    val darkTheme = when (mode) {
        ThemeMode.Auto -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    return when {
        !darkTheme -> LightScheme
        darkVariant == DarkVariant.Oled -> OledDarkScheme
        else -> DarkScheme
    }
}

/**
 * Cross-fades every slot the UI actually reads, so a theme switch animates instead of snapping.
 *
 * A `Crossfade` over the whole tree would be simpler, but it would rebuild the composition and throw
 * away the current stage, selected worlds and scroll position - the switch happens on the settings
 * screen, so that would eject the user from where they are.
 */
@Composable
private fun animatedScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(ThemeTransitionMs, easing = EmphasizedDecelerate)
    return target.copy(
        primary = animateColorAsState(target.primary, spec, label = "primary").value,
        onPrimary = animateColorAsState(target.onPrimary, spec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, spec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryContainer").value,
        secondary = animateColorAsState(target.secondary, spec, label = "secondary").value,
        onSecondary = animateColorAsState(target.onSecondary, spec, label = "onSecondary").value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, spec, label = "secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, spec, label = "onSecondaryContainer").value,
        tertiary = animateColorAsState(target.tertiary, spec, label = "tertiary").value,
        onTertiary = animateColorAsState(target.onTertiary, spec, label = "onTertiary").value,
        tertiaryContainer = animateColorAsState(target.tertiaryContainer, spec, label = "tertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(target.onTertiaryContainer, spec, label = "onTertiaryContainer").value,
        background = animateColorAsState(target.background, spec, label = "background").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "onBackground").value,
        surface = animateColorAsState(target.surface, spec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, spec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant").value,
        outline = animateColorAsState(target.outline, spec, label = "outline").value,
        outlineVariant = animateColorAsState(target.outlineVariant, spec, label = "outlineVariant").value,
    )
}

/**
 * @param mode forced by the config file; [ThemeMode.Auto] follows the system light/dark setting.
 * @param darkVariant only meaningful when the resolved theme ends up dark.
 */
@Composable
fun TerramapTheme(
    mode: ThemeMode = ThemeMode.Auto,
    darkVariant: DarkVariant = DarkVariant.Standard,
    content: @Composable () -> Unit,
) {
    // isSystemInDarkTheme() is state, so the composition already re-runs when the system flips.
    val systemDark = isSystemInDarkTheme()
    val target = schemeFor(mode, darkVariant, systemDark)
    val scheme = animatedScheme(target)
    MaterialTheme(colorScheme = scheme, content = content)
}

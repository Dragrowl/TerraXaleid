// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.terraxaleid.android.data.DarkVariant
import io.terraxaleid.android.data.ThemeMode

// The palette is deliberately neutral: every surface sits on the grey axis (R=G=B or within one
// point), so nothing reads as warm paper or a yellow-tinted card. Only the accent keeps a faint
// green-grey cast, which is what keeps the UI from looking like plain Material grey.
private val Graphite = Color(0xFF4F5751)
private val GraphiteLight = Color(0xFFBCC4BE)
private val NeutralGrey = Color(0xFF565656)
private val NeutralGreyLight = Color(0xFFC6C6C6)
private val CoolGrey = Color(0xFF545B5C)
private val CoolGreyLight = Color(0xFFBFC6C8)

private val LightScheme = lightColorScheme(
    primary = Graphite,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1DE),
    onPrimaryContainer = Color(0xFF101410),
    secondary = NeutralGrey,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E3E3),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = CoolGrey,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE2E3),
    onTertiaryContainer = Color(0xFF0B1112),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF5F5F5),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE7E7E7),
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0xFF7A7A7A),
    outlineVariant = Color(0xFFCFCFCF),
)

private val DarkScheme = darkColorScheme(
    primary = GraphiteLight,
    onPrimary = Color(0xFF1F2420),
    primaryContainer = Color(0xFF3A413B),
    onPrimaryContainer = Color(0xFFD6DED8),
    secondary = NeutralGreyLight,
    onSecondary = Color(0xFF2A2A2A),
    secondaryContainer = Color(0xFF434343),
    onSecondaryContainer = Color(0xFFE6E6E6),
    tertiary = CoolGreyLight,
    onTertiary = Color(0xFF1D2425),
    tertiaryContainer = Color(0xFF374043),
    onTertiaryContainer = Color(0xFFD6E0E2),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE2E2E2),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE2E2E2),
    surfaceVariant = Color(0xFF232323),
    onSurfaceVariant = Color(0xFFC6C6C6),
    outline = Color(0xFF8F8F8F),
    outlineVariant = Color(0xFF3A3A3A),
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

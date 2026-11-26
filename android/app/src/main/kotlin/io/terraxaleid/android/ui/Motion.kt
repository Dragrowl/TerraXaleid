// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.terraxaleid.android.ui

import androidx.compose.foundation.background

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Motion constants shared by the screen transitions and the theme cross-fade, so both use the same
 * duration and the same two Material emphasized curves instead of drifting apart over time.
 *
 * The values come straight from the Material 3 motion spec: 300 ms, emphasized decelerate for
 * entering content, emphasized accelerate for leaving content.
 */
internal const val StageTransitionMs = 300

/** Colour changes get more time than a page change: the whole surface has to read as one move. */
internal const val ThemeTransitionMs = 450

internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
internal val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)


/**
 * Explicit colours for every app bar: the default would resolve `surface` once and then sit still while
 * the rest of the surface animates, which made the bar look like it lagged behind a theme switch.
 */
@androidx.compose.runtime.Composable
internal fun terramapTopBarColors() = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
    // Transparent on purpose: Material3's TopAppBar paints its container from a value resolved before
    // the scheme animation ticks, so it snapped at the end of a switch while the page faded. The bar
    // now sits on `Modifier.background(surface)` (see [terramapTopBarBackground]) which re-reads the
    // animated scheme every frame.
    containerColor = androidx.compose.ui.graphics.Color.Transparent,
    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
)

/** Animating backdrop for an app bar; pair it with [terramapTopBarColors]. */
@androidx.compose.runtime.Composable
internal fun terramapTopBarBackground(): androidx.compose.ui.Modifier =
    androidx.compose.ui.Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.surface)

// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.ui

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Motion constants shared by the screen transitions and the theme cross-fade, so both use the same
 * duration and the same two Material emphasized curves instead of drifting apart over time.
 *
 * The values come straight from the Material 3 motion spec: 300 ms, emphasized decelerate for
 * entering content, emphasized accelerate for leaving content.
 */
internal const val StageTransitionMs = 300

internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
internal val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

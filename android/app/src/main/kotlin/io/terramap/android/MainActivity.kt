// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.terramap.android.ui.AppRoot
import io.terramap.android.ui.TerramapTheme
import io.terramap.android.ui.ThemeController

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One read before the first frame: the theme must be right on the very first screenshot.
        ThemeController.attach(applicationContext)
        setContent {
            // Re-read on every resume so a pushed terramap.json takes effect without a restart: that is
            // what makes the light/dark screenshot pass reproducible. The settings screen writes the
            // same file through ThemeController, which keeps the in-memory value authoritative.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) ThemeController.reload(applicationContext)
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            val config = ThemeController.config
            TerramapTheme(mode = config.theme, darkVariant = config.darkVariant) {
                AppRoot()
            }
        }
    }
}

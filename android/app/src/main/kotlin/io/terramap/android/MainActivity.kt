// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.terramap.android.data.AppConfig
import io.terramap.android.data.AppConfigStore
import io.terramap.android.ui.AppRoot
import io.terramap.android.ui.TerramapTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // The config is re-read on every resume so a pushed file takes effect without a restart:
            // that is what makes the light/dark screenshot pass reproducible.
            var config by remember { mutableStateOf(AppConfigStore.read(this)) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        config = AppConfigStore.read(this@MainActivity)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            TerramapTheme(mode = config.theme, darkVariant = config.darkVariant) {
                AppRoot()
            }
        }
    }
}

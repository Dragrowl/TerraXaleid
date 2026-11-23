// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.terramap.android.ui.AppRoot
import io.terramap.android.ui.TerramapTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TerramapTheme {
                AppRoot()
            }
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.android

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.terraxaleid.android.data.AppConfigStore
import io.terraxaleid.android.ui.resolveTag
import io.terraxaleid.android.ui.AppRoot
import io.terraxaleid.android.ui.ProvideAppLanguage
import io.terraxaleid.android.ui.TerramapTheme
import io.terraxaleid.android.ui.ThemeController

class MainActivity : ComponentActivity() {

    /**
     * The activity context carries the chosen locale, so every surface that resolves strings from it -
     * popup menus, dialogs, toasts - speaks the same language as the Compose tree. Without this, a
     * dropdown opened from a translated screen still renders the system language.
     */
    override fun attachBaseContext(newBase: Context) {
        val language = AppConfigStore.read(newBase).language
        val system = newBase.resources.configuration.locales
        super.attachBaseContext(wrap(newBase, resolveTag(language, system)))
    }

    private fun wrap(base: Context, tag: String): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }

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
            // The device locale, never the wrapped one: "follow the system" has to keep following the
            // phone even while the UI itself is translated to something else.
            val systemLocales = applicationContext.resources.configuration.locales
            ProvideAppLanguage(config.language, systemLocales) {
                TerramapTheme(mode = config.theme, darkVariant = config.darkVariant) {
                    // The status bar strip is painted by the window background, not by Compose, so
                    // without this it keeps the static colour from themes.xml and visibly lags the
                    // 450 ms scheme animation. SideEffect keeps it in step with every frame.
                    val pageBackground = MaterialTheme.colorScheme.background
                    val lightBars = pageBackground.luminance() > 0.5f
                    SideEffect {
                        window.decorView.setBackgroundColor(pageBackground.toArgb())
                        window.statusBarColor = pageBackground.toArgb()
                        window.navigationBarColor = pageBackground.toArgb()
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = lightBars
                            isAppearanceLightNavigationBars = lightBars
                        }
                    }
                    AppRoot()
                }
            }
        }
    }
}

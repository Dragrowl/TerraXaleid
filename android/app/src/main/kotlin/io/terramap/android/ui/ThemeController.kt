// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.terramap.android.data.AppConfig
import io.terramap.android.data.AppConfigStore
import io.terramap.android.data.BackExit
import io.terramap.android.data.DarkVariant
import io.terramap.android.data.ThemeMode

/**
 * Single source of truth for the settings that live in `terramap.json`.
 *
 * The activity needs the theme above [AppRoot] while the settings screen needs to change it from
 * inside, so the state sits in a process-wide holder instead of a `ViewModel`: there is exactly one
 * activity and nothing here outlives the process except the file on disk.
 *
 * Every setter changes the in-memory value first and then persists. A read-only config location must
 * not undo a switch the user just saw happen, so a failed write is only logged by [AppConfigStore].
 */
object ThemeController {

    private var appContext: Context? = null

    var config by mutableStateOf(AppConfig())
        private set

    /** Called once from `MainActivity.onCreate`; also performs the first read. */
    fun attach(context: Context) {
        val app = context.applicationContext
        appContext = app
        config = AppConfigStore.read(app)
    }

    /** Re-read after the activity resumes, so a pushed config file takes effect without a restart. */
    fun reload(context: Context? = appContext) {
        val app = context ?: return
        appContext = app.applicationContext
        config = AppConfigStore.read(app)
    }

    fun setTheme(mode: ThemeMode) = update(config.copy(theme = mode))

    fun setDarkVariant(variant: DarkVariant) = update(config.copy(darkVariant = variant))

    fun setBackExit(exit: BackExit) = update(config.copy(backExit = exit))

    private fun update(next: AppConfig) {
        config = next
        val app = appContext ?: return
        AppConfigStore.write(app, next)
    }
}

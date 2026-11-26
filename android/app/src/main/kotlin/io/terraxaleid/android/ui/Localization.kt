// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.android.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.terraxaleid.android.data.AppLanguage
import java.util.Locale

/**
 * Overrides the resource locale for everything below it.
 *
 * The app ships five translations and lets the user pick one in Settings, which Android's own
 * per-app language API cannot express on every supported release, so the switch is done here: a
 * [android.content.Context] is rebuilt against a modified [Configuration] and provided as
 * `LocalContext`. `stringResource` resolves against that context, and providing the matching
 * `LocalConfiguration` makes Compose recompose the whole tree when the language changes.
 */
@Composable
internal fun ProvideAppLanguage(
    language: AppLanguage,
    system: LocaleList,
    content: @Composable () -> Unit,
) {
    val base = LocalContext.current
    val tag = resolveTag(language, system)

    val localized = remember(base, tag) {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        base.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}

/**
 * The concrete locale a choice maps to.
 *
 * "Follow the system" is not a locale of its own: it is whichever of the five supported languages the
 * device is set to right now, so the UI can translate immediately instead of waiting for a restart.
 * [AppLanguage.wire] still stores `system`, which is why the next launch re-resolves it and a system
 * language change in between is picked up.
 */
internal fun resolveTag(language: AppLanguage, system: LocaleList): String {
    if (language.tag != null) return language.tag
    val locale = if (system.isEmpty) Locale.getDefault() else system[0]
    return when (locale.language.lowercase(Locale.ROOT)) {
        "zh" -> {
            val region = locale.country.uppercase(Locale.ROOT)
            when (region) {
                "TW", "MO" -> "zh-TW"
                "HK" -> "zh-TW"
                else -> "zh-CN"
            }
        }
        "ja" -> "ja-JP"
        else -> "en-US"
    }
}


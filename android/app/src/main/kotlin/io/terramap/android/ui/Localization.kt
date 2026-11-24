// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.terramap.android.data.AppLanguage
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
internal fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val tag = language.tag
    if (tag == null) {
        content()
        return
    }

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

/** Text a language option is labelled with, in that language's own script. */
internal fun AppLanguage.displayName(): String = when (this) {
    AppLanguage.FollowSystem -> "system"
    AppLanguage.SimplifiedChinese -> "简体中文"
    AppLanguage.TraditionalChinese -> "繁體中文（台灣）"
    AppLanguage.HongKongTraditional -> "繁體中文（香港）"
    AppLanguage.Japanese -> "日本語"
    AppLanguage.English -> "English"
}

// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(ExperimentalMaterial3Api::class)

package io.terramap.android.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import io.terramap.android.data.AppLanguage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.terramap.android.R
import io.terramap.android.data.BackExit
import io.terramap.android.data.DarkVariant
import io.terramap.android.data.ThemeMode
import kotlinx.coroutines.launch

/**
 * Every option is an expanded card with a subtitle - no dropdowns, no second level - so the whole
 * screen can be read without opening anything. Values live in [ThemeController] and are written to
 * `terramap.json` on change.
 */
@Composable
internal fun SettingsScreen(onBack: () -> Unit) {
    val config = ThemeController.config
    val systemDark = isSystemInDarkTheme()
    val resolvedDark = when (config.theme) {
        ThemeMode.Auto -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val linkFailed = stringResource(R.string.about_link_failed)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(colors = terramapTopBarColors(),
                modifier = terramapTopBarBackground(),
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard(
                title = stringResource(R.string.theme_title),
                subtitle = stringResource(R.string.theme_subtitle),
            ) {
                // Three previews side by side: the thumbnails are real screenshots of this app, so what
                // is shown is exactly what the switch produces.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOption(
                        modifier = Modifier.weight(1f),
                        image = R.drawable.theme_preview_auto,
                        label = stringResource(R.string.theme_auto),
                        selected = config.theme == ThemeMode.Auto,
                        onClick = { ThemeController.setTheme(ThemeMode.Auto) },
                    )
                    ThemeOption(
                        modifier = Modifier.weight(1f),
                        image = R.drawable.theme_preview_light,
                        label = stringResource(R.string.theme_light),
                        selected = config.theme == ThemeMode.Light,
                        onClick = { ThemeController.setTheme(ThemeMode.Light) },
                    )
                    ThemeOption(
                        modifier = Modifier.weight(1f),
                        image = R.drawable.theme_preview_dark,
                        label = stringResource(R.string.theme_dark),
                        selected = config.theme == ThemeMode.Dark,
                        onClick = { ThemeController.setTheme(ThemeMode.Dark) },
                    )
                }
            }

            // Shown whenever the resolved theme is dark, which includes the automatic mode on a
            // device that is currently in dark mode.
            if (resolvedDark) {
                SettingsCard(
                    title = null,
                    subtitle = null,
                ) {
                    // Two tabs on one row: the choice is binary, so a stacked radio list wastes height.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VariantTab(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.dark_variant_standard),
                            selected = config.darkVariant == DarkVariant.Standard,
                            onClick = { ThemeController.setDarkVariant(DarkVariant.Standard) },
                        )
                        VariantTab(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.dark_variant_oled),
                            selected = config.darkVariant == DarkVariant.Oled,
                            onClick = { ThemeController.setDarkVariant(DarkVariant.Oled) },
                        )
                    }
                }
            }

            SettingsCard(
                title = stringResource(R.string.behavior_title),
                subtitle = null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.behavior_exit_title),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = config.backExit == BackExit.Kill,
                        onCheckedChange = { on ->
                            ThemeController.setBackExit(if (on) BackExit.Kill else BackExit.Background)
                        },
                    )
                }
            }

            // The language row sits under Behaviour. A dropdown rather than a card list: six entries
            // would push About off the first screen, and the current value stays visible in the row.
            SettingsCard(title = null, subtitle = null) {
                LanguageRow()
            }

            // The about block sits at the bottom of the same page - no second level to walk into.
            SettingsCard(
                title = stringResource(R.string.about_title),
                subtitle = null,
            ) {
                AboutBlock(onLinkFailed = { scope.launch { snackbar.showSnackbar(linkFailed) } })
            }
        }
    }
}

@Composable
private fun ThemeOption(
    modifier: Modifier,
    @DrawableRes image: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Image(
                painter = painterResource(image),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .clip(shape)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = shape,
                    ),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(2.dp),
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun VariantTab(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) scheme.onSurface.copy(alpha = 0.10f).compositeOver(scheme.surfaceVariant) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else scheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Card shell shared by every settings block: bold title, muted subtitle, then the control. */
@Composable
internal fun SettingsCard(
    title: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/**
 * The language row: one line, current value on the right, dropdown opening in place.
 *
 * Language names are always written in their own script, so a Chinese reader can find "繁體中文"
 * without first understanding the current language of the app.
 */
@Composable
private fun LanguageRow() {
    val current = ThemeController.config.language
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = current.label(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(text = language.label(), maxLines = 1, softWrap = false)
                    },
                    onClick = {
                        expanded = false
                        ThemeController.setLanguage(language)
                    },
                    trailingIcon = {
                        if (language == current) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppLanguage.label(): String = when (this) {
    AppLanguage.FollowSystem -> stringResource(R.string.language_follow)
    AppLanguage.SimplifiedChinese -> stringResource(R.string.language_sc)
    AppLanguage.TraditionalChinese -> stringResource(R.string.language_tc)
    AppLanguage.HongKongTraditional -> stringResource(R.string.language_hktc)
    AppLanguage.Japanese -> stringResource(R.string.language_ja)
    AppLanguage.English -> stringResource(R.string.language_en)
}

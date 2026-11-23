// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(ExperimentalMaterial3Api::class)

package io.terramap.android.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.terramap.android.BuildConfig
import io.terramap.android.R
import io.terramap.android.data.BackExit
import io.terramap.android.data.DarkVariant
import io.terramap.android.data.ThemeMode

/**
 * Every option is an expanded card with a subtitle - no dropdowns, no second level - so the whole
 * screen can be read without opening anything. Values live in [ThemeController] and are written to
 * `terramap.json` on change.
 */
@Composable
internal fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val config = ThemeController.config
    Scaffold(
        topBar = {
            TopAppBar(
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

            // Only meaningful once the resolved theme is dark, so it appears with the dark choice.
            if (config.theme == ThemeMode.Dark) {
                SettingsCard(
                    title = stringResource(R.string.dark_variant_title),
                    subtitle = stringResource(R.string.dark_variant_subtitle),
                ) {
                    DarkVariantRow(
                        title = stringResource(R.string.dark_variant_standard),
                        body = stringResource(R.string.dark_variant_standard_body),
                        selected = config.darkVariant == DarkVariant.Standard,
                        onClick = { ThemeController.setDarkVariant(DarkVariant.Standard) },
                    )
                    DarkVariantRow(
                        title = stringResource(R.string.dark_variant_oled),
                        body = stringResource(R.string.dark_variant_oled_body),
                        selected = config.darkVariant == DarkVariant.Oled,
                        onClick = { ThemeController.setDarkVariant(DarkVariant.Oled) },
                    )
                }
            }

            SettingsCard(
                title = stringResource(R.string.behavior_title),
                subtitle = stringResource(R.string.behavior_exit_body),
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

            // One entry point, whole card tappable: the about page is a card stack too, not a menu.
            SettingsCard(
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_subtitle, BuildConfig.VERSION_NAME),
                modifier = Modifier.clickable(onClick = onOpenAbout),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
private fun DarkVariantRow(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.size(4.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Card shell shared by every settings block: bold title, muted subtitle, then the control. */
@Composable
internal fun SettingsCard(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
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

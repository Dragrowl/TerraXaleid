// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(ExperimentalMaterial3Api::class)

package io.terramap.android.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.terramap.android.BuildConfig
import io.terramap.android.R
import kotlinx.coroutines.launch

private val IconTile = androidx.compose.ui.graphics.Color(0xFF12161D)
private const val BILIBILI_URL = "https://space.bilibili.com/3461573882415902"

/**
 * One card: identity on top, publisher and licence on the left of the bottom row, the two link
 * buttons right-aligned next to it. The GitHub target is intentionally empty for now, so that button
 * is drawn disabled and does nothing when tapped.
 */
@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.about_link_failed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The launcher icon is an adaptive-icon XML, which a painter cannot rasterise,
                        // so the mark is composed here from the same vector on its dark tile.
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(IconTile),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left aligned, as two short lines: who publishes it and under which licence.
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.about_publisher),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(R.string.about_license),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinkButton(
                            icon = R.drawable.ic_github,
                            label = stringResource(R.string.about_github),
                            // No repository URL yet: disabled rather than a button that lies.
                            onClick = null,
                        )
                        Spacer(Modifier.width(4.dp))
                        LinkButton(
                            icon = R.drawable.ic_bilibili,
                            label = stringResource(R.string.about_bilibili),
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BILIBILI_URL))
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    scope.launch { snackbar.showSnackbar(failed) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Small icon-only link: same muted grey as secondary text, dimmed when there is nowhere to go. */
@Composable
private fun LinkButton(
    icon: Int,
    label: String,
    onClick: (() -> Unit)?,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    BoxedIcon(icon = icon, label = label, tint = tint, enabled = onClick != null, onClick = onClick)
}

@Composable
private fun BoxedIcon(
    icon: Int,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: (() -> Unit)?,
) {
    IconButton(
        onClick = { onClick?.invoke() },
        enabled = enabled,
        modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

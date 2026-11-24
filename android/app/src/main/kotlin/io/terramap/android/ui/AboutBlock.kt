// SPDX-License-Identifier: GPL-3.0-or-later

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.terramap.android.BuildConfig
import io.terramap.android.R

private val IconTile = Color(0xFF12161D)
private const val BILIBILI_URL = "https://space.bilibili.com/3461573882415902"

/**
 * The about block, laid out inside the settings screen as its last card: mark and version on top,
 * publisher and licence bottom-left, the two link buttons bottom-right. No separate screen.
 */
@Composable
internal fun AboutBlock(onLinkFailed: () -> Unit) {
    val context = LocalContext.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        // The launcher icon is an adaptive-icon XML, which a painter cannot rasterise, so the mark is
        // composed from the same vector on its dark tile.
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
                    onLinkFailed()
                }
            },
        )
    }
}

/** Small icon-only link: muted grey, dimmed when there is nowhere to go. */
@Composable
private fun LinkButton(
    icon: Int,
    label: String,
    onClick: (() -> Unit)?,
) {
    IconButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.alpha(if (onClick != null) 1f else 0.38f),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

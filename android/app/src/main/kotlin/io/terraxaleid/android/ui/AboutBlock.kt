// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.android.ui

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
import io.terraxaleid.android.BuildConfig
import io.terraxaleid.android.R

private val IconTile = Color(0xFF12161D)
private const val GITHUB_URL = "https://github.com/Dragrowl/terramap"
private const val BILIBILI_URL = "https://space.bilibili.com/3461573882415902"
private const val BILIBILI_PACKAGE = "tv.danmaku.bili"
private const val BILIBILI_APP_URI = "bilibili://space/3461573882415902"

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
            onClick = { openUrl(context, GITHUB_URL, onLinkFailed) },
        )
        Spacer(Modifier.width(4.dp))
        LinkButton(
            icon = R.drawable.ic_bilibili,
            label = stringResource(R.string.about_bilibili),
            onClick = { openBilibili(context, onLinkFailed) },
        )
    }
}

/**
 * Contexts reached through `LocalContext` are configuration wrappers around the activity, not
 * activity instances, so every launch has to carry NEW_TASK or the platform throws
 * "Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK
 * flag" — which surfaced as a crash on these two buttons. Anything that can still go wrong (no
 * browser, a blocked intent) is reported instead of thrown.
 */
internal fun launch(context: android.content.Context, intent: Intent, onFailed: () -> Unit) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onFailed()
    } catch (_: SecurityException) {
        onFailed()
    } catch (_: RuntimeException) {
        onFailed()
    }
}

/** Opens a web link in whatever handles it. */
internal fun openUrl(context: android.content.Context, url: String, onFailed: () -> Unit) {
    launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), onFailed)
}

/**
 * Bilibili opens inside the app when it can handle the space link, and falls back to the default
 * browser otherwise. There is no package query first: from Android 11 an app cannot see another
 * package unless it is declared in <queries>, so `getLaunchIntentForPackage` answers null for an
 * installed Bilibili and the old code always fell through to the browser. Launching an explicit
 * intent is still allowed, so the attempt itself is the test.
 */
private fun openBilibili(context: android.content.Context, onFailed: () -> Unit) {
    val inApp = Intent(Intent.ACTION_VIEW, Uri.parse(BILIBILI_APP_URI)).setPackage(BILIBILI_PACKAGE)
    inApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(inApp) }.isSuccess
    if (opened) return
    openUrl(context, BILIBILI_URL, onFailed)
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

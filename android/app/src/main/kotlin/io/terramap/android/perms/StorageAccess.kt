// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.perms

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The tool has to read and write another app's private save folder, so on API 30+ it needs the
 * "all files access" special permission, and on API 24..29 the legacy storage permission pair.
 */
object StorageAccess {

    private val legacyPermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            legacyPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

    /** Permissions to request at runtime on API 24..29; empty on API 30+. */
    fun legacyRequest(context: Context): Array<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return emptyArray()
        return legacyPermissions
            .filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    /** Settings screen for all-files access; falls back to the list screen when the former is missing. */
    fun settingsIntent(context: Context): Intent {
        val specific = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        return try {
            context.packageManager.resolveActivity(specific, 0)
            specific
        } catch (e: ActivityNotFoundException) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
}

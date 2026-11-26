// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.android.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import rikka.shizuku.Shizuku

/** Result of one shell command executed through Shizuku (uid 2000). */
data class ShellResult(val code: Int, val out: String, val err: String) {
    val ok: Boolean get() = code == 0
}

/**
 * Runs commands as the `shell` user through Shizuku.
 *
 * Android 11+ gives every app a FUSE view in which another app's `Android/data` directory cannot be
 * listed at all, and `MANAGE_EXTERNAL_STORAGE` does not lift that. The shell user (uid 2000) is a
 * member of `ext_data_rw`, so it can read and write the game's save tree; this class is the only
 * place that talks to Shizuku, everything above it works on ordinary [java.io.File] paths that the
 * shell command has staged into app-readable storage.
 */
object ShizukuShell {

    private const val TAG = "terraxaleid"
    const val REQUEST_CODE = 0x5A12
    private const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"

    fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean {
        if (!isBinderAlive()) return false
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /** True when the installed Shizuku is reachable but this app has not been granted API access. */
    fun needsPermission(): Boolean = isBinderAlive() && !hasPermission()

    /**
     * Asks Shizuku for API access.
     *
     * When the binder is not there yet (service stopped, or the manager was never started after a
     * reinstall) this opens Shizuku itself, which is the only place the service can be started from.
     */
    fun requestPermission(context: Context) {
        if (!isBinderAlive()) {
            openManager(context)
            return
        }
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure {
                Log.w(TAG, "shizuku requestPermission failed", it)
                openManager(context)
            }
    }

    /** Opens the Shizuku app so the service can be (re)started and the grant reviewed. */
    /**
     * Brings the Shizuku app to the front so the user can grant access there.
     *
     * Three attempts, in order: the launcher intent (works on every Shizuku build and needs the
     * `<queries>` entry for the package), the app-info screen, and the Play/market page. Returns
     * false when none of them could be launched so the caller can tell the user instead of leaving
     * the tap looking dead.
     */
    fun openManager(context: Context): Boolean {
        val pm = context.packageManager
        pm.getLaunchIntentForPackage(MANAGER_PACKAGE)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(launch) }.isSuccess) return true
        }
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$MANAGER_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(details) }.isSuccess) return true
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$MANAGER_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(market) }.isSuccess) return true
        Log.w(TAG, "cannot open Shizuku")
        return false
    }

    /**
     * Runs one shell command and collects its output.
     *
     * The streams are drained on helper threads and stdin is closed before waiting: a remote process
     * whose stdout pipe is never drained (or whose stdin stays open) blocks `waitFor()` forever, which
     * is exactly what a plain read-then-wait sequence did.
     */
    fun exec(command: String): ShellResult? {
        if (!hasPermission()) {
            Log.i(TAG, "exec[$command] skipped: no shizuku permission")
            return null
        }
        return try {
            val process = newProcess(arrayOf("sh", "-c", command))
            runCatching { process.outputStream.close() }
            val out = StringBuilder()
            val err = StringBuilder()
            val reader = Thread {
                runCatching { process.inputStream.use { out.append(String(it.readBytes())) } }
            }.apply { isDaemon = true; start() }
            val errorReader = Thread {
                runCatching { process.errorStream.use { err.append(String(it.readBytes())) } }
            }.apply { isDaemon = true; start() }
            val code = process.waitFor()
            reader.join(5000)
            errorReader.join(5000)
            // Release the remote process before the next call: the server keeps one slot per client
            // and the second command of a session blocks until the first is reaped.
            runCatching { process.destroy() }
            ShellResult(code, out.toString(), err.toString()).also {
                Log.i(TAG, "exec[$command] -> ${it.code} out=${it.out.trim()} err=${it.err.trim()}")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "shizuku exec failed: $command", e)
            null
        }
    }

    fun execOk(command: String): Boolean = exec(command)?.ok == true

    /**
     * Starts a shell command through the Shizuku server.
     *
     * Shizuku 13.1.5 keeps `newProcess` out of the public API surface, so it is reached by
     * reflection; the alternative (a Shizuku UserService) would need a second process and an AIDL
     * file for something the binder already offers.
     */
    private fun newProcess(command: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, command, null, null) as Process
    }
}

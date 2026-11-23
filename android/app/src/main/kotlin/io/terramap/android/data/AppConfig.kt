// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.data

import android.content.Context
import android.util.Log
import java.io.File
import org.json.JSONObject

/**
 * Theme selection as it can be forced from a config file.
 *
 * This exists because the theme has to be reproducible from the outside: the theme thumbnails are
 * captured from the running app, so a build must be able to boot straight into light or dark without
 * anyone touching the UI. The settings screen writes the same file.
 */
enum class ThemeMode(val wire: String) {
    Auto("auto"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromWire(value: String?): ThemeMode =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: Auto
    }
}

/** Two dark flavours: the regular dark scheme, or true black for OLED panels. */
enum class DarkVariant(val wire: String) {
    Standard("standard"),
    Oled("oled"),
    ;

    companion object {
        fun fromWire(value: String?): DarkVariant =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: Standard
    }
}

/**
 * What a back press on the world list does.
 *
 * [Background] keeps the activity alive behind whatever the user opens next, [Kill] removes the task
 * so no process is left holding the parsed world and map. Both are one press: there is no
 * "press twice to exit" step.
 */
enum class BackExit(val wire: String) {
    Background("background"),
    Kill("kill"),
    ;

    companion object {
        fun fromWire(value: String?): BackExit =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: Background
    }
}

data class AppConfig(
    val theme: ThemeMode = ThemeMode.Auto,
    val darkVariant: DarkVariant = DarkVariant.Standard,
    val backExit: BackExit = BackExit.Background,
    /** Where this config was read from, for the log line. Null when nothing existed yet. */
    val source: String? = null,
)

/**
 * Reads and writes `terramap.json`. Lookup order, first hit wins:
 *
 * 1. `/sdcard/Android/data/io.terramap.android/files/terramap.json` — the app's own external dir,
 *    writable over adb without any permission and survives reinstalls of the same package;
 * 2. `/sdcard/Download/.terramap/terramap.json` — next to the Shizuku staging tree, easy to find;
 * 3. the app-private `files/terramap.json`.
 *
 * A missing or malformed file is not an error: it just means "follow the system".
 */
object AppConfigStore {

    private const val TAG = "terramap"
    private const val FILE_NAME = "terramap.json"

    fun candidates(context: Context): List<File> = buildList {
        context.getExternalFilesDir(null)?.let { add(File(it, FILE_NAME)) }
        add(File("/sdcard/Download/.terramap/$FILE_NAME"))
        add(File(context.filesDir, FILE_NAME))
    }

    fun read(context: Context): AppConfig {
        val files = candidates(context)
        val hit = files.firstOrNull { it.isFile }
        if (hit == null) {
            // Seed the first location so editing it in place is possible without guessing the schema.
            val seed = files.first()
            writeFile(seed, AppConfig())
            Log.i(TAG, "config: none found, seeded ${seed.absolutePath}; using defaults")
            return AppConfig()
        }
        val config = runCatching {
            val json = JSONObject(hit.readText(Charsets.UTF_8))
            AppConfig(
                theme = ThemeMode.fromWire(json.optString("theme")),
                darkVariant = DarkVariant.fromWire(json.optString("darkVariant")),
                backExit = BackExit.fromWire(json.optString("backExit")),
                source = hit.absolutePath,
            )
        }.getOrElse { error ->
            Log.w(TAG, "config: ${hit.absolutePath} is unreadable (${error.message}); using defaults")
            AppConfig(source = hit.absolutePath)
        }
        Log.i(
            TAG,
            "config: theme=${config.theme.wire} darkVariant=${config.darkVariant.wire} " +
                "backExit=${config.backExit.wire} from=${config.source}",
        )
        return config
    }

    /**
     * Writes the same location [read] would hit first. Failure is logged, never thrown: a read-only
     * external dir must not break a theme switch that already happened in memory.
     */
    fun write(context: Context, config: AppConfig): Boolean {
        val target = candidates(context).first()
        if (writeFile(target, config)) {
            Log.i(
                TAG,
                "config: wrote theme=${config.theme.wire} darkVariant=${config.darkVariant.wire} " +
                    "backExit=${config.backExit.wire} to=${target.absolutePath}",
            )
            return true
        }
        return false
    }

    /** Write to a sibling temp file and rename, so a half-written file never becomes the live config. */
    private fun writeFile(target: File, config: AppConfig): Boolean = runCatching {
        val json = JSONObject()
            .put("theme", config.theme.wire)
            .put("darkVariant", config.darkVariant.wire)
            .put("backExit", config.backExit.wire)
        val body = json.toString(2) + "\n"
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        temp.writeText(body, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            // Devices where rename across the FUSE boundary fails: fall back to a direct write.
            target.writeText(body, Charsets.UTF_8)
            temp.delete()
        }
        true
    }.getOrElse { error ->
        Log.w(TAG, "config: could not write ${target.absolutePath} (${error.message})")
        false
    }
}

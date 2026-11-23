// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.data

import android.util.Log
import java.io.File

/**
 * Stages the game's save tree into a location this app can read, so the rest of the pipeline stays
 * plain `File` IO.
 *
 * Every access to the game's own `Android/data` tree goes through [ShizukuShell] — the app never runs
 * `su` itself, and it does not matter whether Shizuku is running as shell or as root.
 */
object TerraMirror {

    private const val TAG = "terramap"

    /** Save tree of the international mobile build. */
    const val TERRARIA_BASE = "/sdcard/Android/data/com.and.games505.TerrariaPaid"

    /** Public staging area: the shell user can write it, and all-files access lets us read it. */
    const val WORK_DIR = "/sdcard/Download/.terramap"

    /**
     * Outcome of one write-back.
     *
     * [copied] is the verdict — the exit status of the `cp` itself. [sizeMatches] is a secondary check
     * that is allowed to disagree (some devices and file systems do not report the size we expect right
     * after a write); the caller logs it instead of turning a successful copy into a failure.
     */
    data class PushResult(
        val copied: Boolean,
        val expected: Long,
        val written: Long?,
        val code: Int,
        val error: String,
    ) {
        val sizeMatches: Boolean get() = written != null && written == expected
    }

    fun isAvailable(): Boolean = ShizukuShell.hasPermission()

    /** `true` when the game actually has a save tree at [TERRARIA_BASE]. */
    fun gameTreeExists(): Boolean =
        ShizukuShell.exec("test -d '$TERRARIA_BASE/Worlds' && echo yes")?.out?.contains("yes") == true

    /**
     * Builds a staged copy of the game's save tree.
     *
     * Returns null when Shizuku is unusable or the game directory is missing; the caller then just
     * shows the other roots.
     */
    fun prepare(): SaveRoot? {
        if (!isAvailable()) return null
        if (!gameTreeExists()) return null
        val worlds = "$WORK_DIR/Worlds"
        val players = "$WORK_DIR/Players"
        // One compound command, and no `rm -rf` on the staging tree: deleting a directory that the
        // shell still has open blocks for minutes on this device (rm reports "Text file busy" and the
        // call never returns). Copying over the existing files is enough — cp truncates in place.
        val script = buildString {
            append("mkdir -p '$worlds' '$players' && ")
            append("cp -f '$TERRARIA_BASE/Worlds/'*.wld '$worlds'/ && ")
            append("cp -rf '$TERRARIA_BASE/Players/.' '$players'/ && ")
            append("ls '$worlds'/*.wld | wc -l")
        }
        val result = ShizukuShell.exec(script) ?: return null
        val staged = result.out.trim().lines().lastOrNull { it.trim().toIntOrNull() != null }
        val count = staged?.trim()?.toIntOrNull() ?: 0
        if (!result.ok || count <= 0) {
            Log.w(TAG, "mirror prepare failed: code=${result.code} out=${result.out} err=${result.err}")
            return null
        }
        Log.i(TAG, "mirror staged $count world(s) in $worlds")
        return SaveRoot(
            label = "游戏目录 (Shizuku)",
            worldsDir = File(worlds),
            playersDir = File(players),
            remoteBase = TERRARIA_BASE,
        )
    }

    /**
     * Returns a root over a tree that is already staged (for example copied by an earlier run).
     *
     * `prepare()` needs Shizuku to finish every step; adoption only needs the files to be there, so a
     * half-reported run still leaves the app usable — the write-back path is the same.
     */
    fun adoptStaging(): SaveRoot? {
        val worlds = File("$WORK_DIR/Worlds")
        val players = File("$WORK_DIR/Players")
        if (!worlds.isDirectory) return null
        val wlds = worlds.listFiles { file -> file.isFile && file.name.endsWith(".wld", true) }
        if (wlds.isNullOrEmpty()) return null
        return SaveRoot(
            label = "游戏目录 (Shizuku)",
            worldsDir = worlds,
            playersDir = players,
            remoteBase = TERRARIA_BASE,
        )
    }

    /**
     * Copies one staged map back into the live save tree.
     *
     * Ownership is preserved explicitly: when Shizuku runs as root, `cp` would leave the new file owned
     * by `root:root` and the game (a different uid) may then fail to read it. The original's `uid:gid`
     * and mode are read first and re-applied afterwards; `chmod` on `/sdcard` answers EOPNOTSUPP on
     * some devices, so both restores are best-effort.
     */
    fun pushBack(remoteBase: String, stagedPlayersDir: File, target: File): PushResult {
        val expected = target.length()
        val relative = target.absolutePath.removePrefix(stagedPlayersDir.absolutePath)
            .trimStart(File.separatorChar)
        if (relative.isEmpty()) {
            return PushResult(false, expected, null, -1, "staged path is not inside the mirror")
        }
        val remote = "$remoteBase/Players/$relative"
        val copy = buildString {
            append("own=\$(stat -c %u:%g '$remote' 2>/dev/null); ")
            append("mode=\$(stat -c %a '$remote' 2>/dev/null); ")
            append("if [ -f '$remote' ]; then cp -f '$remote' '$remote.bak'; fi; ")
            append("cp -f '${target.absolutePath}' '$remote' || exit 1; ")
            append("if [ -n \"\$own\" ]; then chown \"\$own\" '$remote' 2>/dev/null || true; fi; ")
            append("if [ -n \"\$mode\" ]; then chmod \"\$mode\" '$remote' 2>/dev/null || true; fi; ")
            append("echo copied")
        }
        val copyResult = ShizukuShell.exec(copy)
        val copied = copyResult?.ok == true && copyResult.out.contains("copied")
        // The copy status is the verdict; the size is only diagnostics, so a device that reports an
        // unexpected size cannot turn a good write into a reported failure.
        val written = ShizukuShell.exec("stat -c %s '$remote'")
            ?.out?.trim()?.lines()?.lastOrNull()?.trim()?.toLongOrNull()
        val result = PushResult(
            copied = copied,
            expected = expected,
            written = written,
            code = copyResult?.code ?: -1,
            error = copyResult?.err?.trim().orEmpty(),
        )
        if (copied && !result.sizeMatches) {
            Log.w(TAG, "pushBack size mismatch for $remote: expected=$expected written=$written")
        } else if (!copied) {
            Log.e(TAG, "pushBack failed for $remote: code=${result.code} err=${result.error}")
        } else {
            Log.i(TAG, "pushBack ok for $remote ($written bytes)")
        }
        return result
    }

    /** Copies the device-side `<name>.map.bak` back over the staged map and the live map. */
    fun restoreBackup(remoteBase: String, stagedPlayersDir: File, target: File): Boolean {
        val relative = target.absolutePath.removePrefix(stagedPlayersDir.absolutePath)
            .trimStart(File.separatorChar)
        if (relative.isEmpty()) return false
        val remote = "$remoteBase/Players/$relative"
        val command = buildString {
            append("if [ ! -f '$remote.bak' ]; then echo nobak; exit 1; fi; ")
            append("own=\$(stat -c %u:%g '$remote.bak' 2>/dev/null); ")
            append("mode=\$(stat -c %a '$remote.bak' 2>/dev/null); ")
            append("cp -f '$remote.bak' '$remote' || exit 1; ")
            append("if [ -n \"\$own\" ]; then chown \"\$own\" '$remote' 2>/dev/null || true; fi; ")
            append("if [ -n \"\$mode\" ]; then chmod \"\$mode\" '$remote' 2>/dev/null || true; fi; ")
            append("cp -f '$remote.bak' '${target.absolutePath}' || exit 1; ")
            append("echo restored")
        }
        val result = ShizukuShell.exec(command)
        val done = result?.ok == true && result.out.contains("restored")
        if (!done) {
            Log.e(TAG, "mirror restore failed for $remote: code=${result?.code} err=${result?.err}")
        } else {
            Log.i(TAG, "mirror restore ok for $remote")
        }
        return done
    }

    fun cleanup() {
        ShizukuShell.exec("rm -rf '$WORK_DIR'")
    }
}

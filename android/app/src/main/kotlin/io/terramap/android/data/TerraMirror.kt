// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.data

import android.util.Log
import java.io.File

/**
 * Stages the game's save tree into a location this app can read, so the rest of the pipeline stays
 * plain `File` IO.
 *
 * Flow: `prepare()` copies `Worlds/ *.wld` and `Players/` from the game's `Android/data` directory
 * into `/sdcard/Download/.terramap` using the shell user; the user works on the staged copy; every
 * written `.map` is copied back (`cp` + `chmod 660`) with a `.bak` of the original next to it.
 */
object TerraMirror {

    private const val TAG = "terramap"

    /** Save tree of the international mobile build. */
    const val TERRARIA_BASE = "/sdcard/Android/data/com.and.games505.TerrariaPaid"

    /** Public staging area: the shell user can write it, and all-files access lets us read it. */
    const val WORK_DIR = "/sdcard/Download/.terramap"

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
     * [stagedPlayersDir] is the mirror's `Players/` directory; [target] is the staged map file. The
     * original is kept as `<name>.map.bak` on the device, exactly like the direct-write path does.
     */
    fun pushBack(remoteBase: String, stagedPlayersDir: File, target: File): Boolean {
        val relative = target.absolutePath.removePrefix(stagedPlayersDir.absolutePath)
            .trimStart(File.separatorChar)
        if (relative.isEmpty()) return false
        val remote = "$remoteBase/Players/$relative"
        // `chmod` is deliberately not part of the chain: /sdcard answers EOPNOTSUPP for chmod on some
        // devices, which aborted the `&&` chain after the copy and made a successful write look like a
        // failure. Verifying the size of the remote file is the honest check.
        val expected = target.length()
        // Everything goes through Shizuku, and Shizuku may well be running as root (the user can grant
        // it superuser). A root `cp` would leave `root:root` ownership behind, so the owner and mode of
        // the original are captured first and restored afterwards. `chmod` on /sdcard answers
        // EOPNOTSUPP on some devices, hence `|| true` — the size check below is the real verdict.
        val command = buildString {
            append("own=\$(stat -c %u:%g '$remote' 2>/dev/null); ")
            append("mode=\$(stat -c %a '$remote' 2>/dev/null); ")
            append("if [ -f '$remote' ]; then cp -f '$remote' '$remote.bak'; fi; ")
            append("cp -f '${target.absolutePath}' '$remote' || exit 1; ")
            append("if [ -n \"\$own\" ]; then chown \"\$own\" '$remote' 2>/dev/null || true; fi; ")
            append("if [ -n \"\$mode\" ]; then chmod \"\$mode\" '$remote' 2>/dev/null || true; fi; ")
            append("stat -c %s '$remote'")
        }
        val result = ShizukuShell.exec(command)
        val written = result?.out?.trim()?.lines()?.lastOrNull()?.trim()?.toLongOrNull()
        if (result == null || !result.ok || written != expected) {
            Log.w(TAG, "mirror pushBack failed for $remote: code=${result?.code} size=$written expected=$expected err=${result?.err}")
            return false
        }
        return true
    }

    /** Copies the device-side `<name>.map.bak` back over the staged map and the live map. */
    fun restoreBackup(remoteBase: String, stagedPlayersDir: File, target: File): Boolean {
        val relative = target.absolutePath.removePrefix(stagedPlayersDir.absolutePath)
            .trimStart(File.separatorChar)
        if (relative.isEmpty()) return false
        val remote = "$remoteBase/Players/$relative"
        val command = buildString {
            append("if [ ! -f '$remote.bak' ]; then echo nobak; exit 1; fi; ")
            append("own=\$(stat -c %u:%g '$remote' 2>/dev/null); ")
            append("mode=\$(stat -c %a '$remote' 2>/dev/null); ")
            append("cp -f '$remote.bak' '$remote' || exit 1; ")
            append("if [ -n \"\$own\" ]; then chown \"\$own\" '$remote' 2>/dev/null || true; fi; ")
            append("if [ -n \"\$mode\" ]; then chmod \"\$mode\" '$remote' 2>/dev/null || true; fi; ")
            append("cp -f '$remote.bak' '${target.absolutePath}' || exit 1; ")
            // Compare the restored file against the backup on the device itself, so the check does not
            // depend on what the staging area looks like.
            append("bs=\$(stat -c %s '$remote.bak'); rs=\$(stat -c %s '$remote'); ")
            append("if [ \"\$bs\" = \"\$rs\" ]; then echo \"\$rs\"; else echo mismatch; fi")
        }
        val result = ShizukuShell.exec(command)
        val size = result?.out?.trim()?.lines()?.lastOrNull()?.trim()?.toLongOrNull()
        if (result == null || !result.ok || size == null || size <= 0) {
            Log.w(TAG, "mirror restore failed for $remote: code=${result?.code} size=$size err=${result?.err}")
            return false
        }
        return true
    }

    fun cleanup() {
        ShizukuShell.exec("rm -rf '$WORK_DIR'")
    }
}

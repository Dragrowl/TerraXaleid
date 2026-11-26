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

    /**
     * Legacy staging area. It sits under `Download`, so reading it needs "all files access"; kept only
     * so a tree staged by an older build is still adoptable.
     */
    const val LEGACY_WORK_DIR = "/sdcard/Download/.terramap"

    /**
     * Staging area for this build: the app's own external files dir.
     *
     * `Android/data/<own package>/files` is readable by the app with no storage permission at all, and
     * the `shell` user (member of `ext_data_rw`) can write into it, so the whole mirror round-trip no
     * longer depends on MANAGE_EXTERNAL_STORAGE. Staging under `Download` was the reason a device with
     * Shizuku but without all-files access could enter the app yet see no worlds.
     */
    fun workDir(context: android.content.Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "mirror")

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
    fun prepare(context: android.content.Context): SaveRoot? {
        if (!isAvailable()) return null
        if (!gameTreeExists()) return null
        val base = workDir(context)
        val worlds = "${base.absolutePath}/Worlds"
        val players = "${base.absolutePath}/Players"
        // One compound command, and no `rm -rf` on the staging tree: deleting a directory that the
        // shell still has open blocks for minutes on this device (rm reports "Text file busy" and the
        // call never returns). Copying over the existing files is enough — cp truncates in place.
        val script = buildString {
            append("mkdir -p '$worlds' '$players' && ")
            append("cp -f '$TERRARIA_BASE/Worlds/'*.wld '$worlds'/ && ")
            append("cp -rf '$TERRARIA_BASE/Players/.' '$players'/ && ")
            // Drop mirror entries the game no longer has, otherwise a world or character deleted in
            // game keeps showing up from the last mirror.
            append("for f in '$worlds'/*.wld; do [ -e \"\$f\" ] || continue; ")
            append("b=\$(basename \"\$f\"); [ -e '$TERRARIA_BASE/Worlds/'\"\$b\" ] || rm -f \"\$f\"; done; ")
            append("for d in '$players'/*; do [ -d \"\$d\" ] || continue; ")
            append("n=\$(basename \"\$d\"); [ -d '$TERRARIA_BASE/Players/'\"\$n\" ] || rm -rf \"\$d\"; done; ")
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
            authoritative = true,
        )
    }

    /**
     * Returns a root over a tree that is already staged (for example copied by an earlier run).
     *
     * `prepare()` needs Shizuku to finish every step; adoption only needs the files to be there, so a
     * half-reported run still leaves the app usable — the write-back path is the same.
     */
    fun adoptStaging(context: android.content.Context): SaveRoot? {
        // With a working Shizuku the caller should build a fresh mirror instead: adopting a leftover
        // tree would show worlds and characters the game has already deleted.
        if (isAvailable()) return null
        // New location first; the Download tree is what builds before this one produced.
        for (base in listOf(workDir(context), File(LEGACY_WORK_DIR))) {
            val worlds = File(base, "Worlds")
            val players = File(base, "Players")
            if (!worlds.isDirectory) continue
            val wlds = worlds.listFiles { file -> file.isFile && file.name.endsWith(".wld", true) }
            if (wlds.isNullOrEmpty()) continue
            return SaveRoot(
                label = "游戏目录 (Shizuku)",
                worldsDir = worlds,
                playersDir = players,
                remoteBase = TERRARIA_BASE,
            authoritative = true,
            )
        }
        return null
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
        // Ownership matters more than it looks: with Shizuku running as root a fresh `cp` produces a
        // root-owned file with root's umask, and the game (uid u0_a72) then cannot read the map it just
        // got. The original still sets the pattern when it exists; a brand new map inherits the owner of
        // its own directory, which is the character's save folder.
        val copy = buildString {
            append("p='$remote'; ")
            append("own=\$(stat -c %u:%g \"\$p\" 2>/dev/null); ")
            append("mode=\$(stat -c %a \"\$p\" 2>/dev/null); ")
            append("if [ -f \"\$p\" ]; then cp -f \"\$p\" \"\$p.bak\"; ")
            append("if [ -n \"\$own\" ]; then chown \"\$own\" \"\$p.bak\" 2>/dev/null || true; ")
            append("chmod \"\${mode:-660}\" \"\$p.bak\" 2>/dev/null || true; fi; fi; ")
            append("cp -f '${target.absolutePath}' \"\$p\" || exit 1; ")
            append("[ -z \"\$own\" ] && own=\$(stat -c %u:%g \"\$(dirname \"\$p\")\" 2>/dev/null); ")
            append("if [ -n \"\$own\" ]; then chown \"\$own\" \"\$p\" 2>/dev/null || true; fi; ")
            append("chmod \"\${mode:-660}\" \"\$p\" 2>/dev/null || true; ")
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

    /**
     * Copies one file to another on device, re-applying the destination's original owner and mode.
     *
     * Returns the shell exit code (0 = done), or -1 when Shizuku is unavailable.
     */
    fun copyFile(source: String, destination: String, preserveOwnerOf: String? = null): Int {
        val ownerOf = preserveOwnerOf ?: destination
        val command = buildString {
            append("own=\$(stat -c %u:%g '$ownerOf' 2>/dev/null); ")
            append("mode=\$(stat -c %a '$ownerOf' 2>/dev/null); ")
            append("cp -f '$source' '$destination' || exit 1; ")
            append("if [ -n \"\$own\" ]; then chown \"\$own\" '$destination' 2>/dev/null || true; fi; ")
            append("if [ -n \"\$mode\" ]; then chmod \"\$mode\" '$destination' 2>/dev/null || true; fi; ")
            append("echo done")
        }
        val result = ShizukuShell.exec(command)
        if (result?.ok != true || !result.out.contains("done")) {
            Log.e(TAG, "copyFile failed: '$source' -> '$destination' code=${result?.code} err=${result?.err}")
            return result?.code ?: -1
        }
        return 0
    }

    /** Deletes one file or directory; 0 on success. */
    fun remove(path: String): Int {
        val result = ShizukuShell.exec("rm -rf '$path' && echo removed") ?: return -1
        if (!result.ok || !result.out.contains("removed")) {
            Log.e(TAG, "remove failed: '$path' code=${result.code} err=${result.err}")
            return result.code.takeIf { it != 0 } ?: -1
        }
        return 0
    }

    /**
     * Renames the existing backup chain of [target] one step up before a new backup is written.
     *
     * `.bak` becomes `.bak.1`, `.bak.1` becomes `.bak.2` and so on, so choosing "keep the previous
     * backup" never silently discards it.
     */
    fun rotateBackup(remoteBase: String, stagedPlayersDir: File, target: File): Int {
        val relative = target.absolutePath.removePrefix(stagedPlayersDir.absolutePath)
            .trimStart(File.separatorChar)
        if (relative.isEmpty()) return -1
        val remote = "$remoteBase/Players/$relative"
        val command = buildString {
            append("p='$remote'; ")
            append("if [ -f \"\$p.bak\" ]; then ")
            append("n=0; for f in \"\$p\".bak.*; do [ -e \"\$f\" ] || continue; ")
            append("k=\${f##*.}; case \"\$k\" in ''|*[!0-9]*) continue;; esac; [ \"\$k\" -gt \"\$n\" ] && n=\"\$k\"; done; ")
            append("i=\$n; while [ \"\$i\" -ge 1 ]; do mv -f \"\$p.bak.\$i\" \"\$p.bak.\$((i+1))\" 2>/dev/null; i=\$((i-1)); done; ")
            append("mv -f \"\$p.bak\" \"\$p.bak.1\"; fi; ")
            append("echo rotated")
        }
        val result = ShizukuShell.exec(command)
        if (result?.ok != true || !result.out.contains("rotated")) {
            Log.w(TAG, "rotateBackup failed for $remote: code=${result?.code} err=${result?.err}")
            return result?.code ?: -1
        }
        return 0
    }
    fun cleanup(context: android.content.Context) {
        ShizukuShell.exec("rm -rf '${workDir(context).absolutePath}'")
    }
}

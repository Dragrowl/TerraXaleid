// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One `.map` on device together with the backups that belong to it. */
data class MapBackupRow(
    val mapName: String,
    val worldName: String,
    val characterName: String,
    val mapPath: String,
    val mapSize: Long,
    val mapModified: Long,
    val backups: List<BackupFile>,
) {
    val hasBackup: Boolean get() = backups.isNotEmpty()
    val newest: BackupFile? get() = backups.maxByOrNull { it.modified }
}

data class BackupFile(
    val name: String,
    val path: String,
    val size: Long,
    val modified: Long,
)

/**
 * Backups live next to the map they belong to: `<name>.map.bak` is the newest pre-overwrite state, and
 * when the user asks to keep an older one it is renamed to `<name>.map.bak.1`, `.bak.2`, ... so the
 * chain never collapses into a single file the user cannot get back.
 *
 * Everything here works through [TerraMirror] when the root is the game's own directory, and through
 * plain [File] API for the portable roots, so the restore screen behaves the same either way.
 */
object Backups {

    private const val TAG = "terramap"

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun timeLabel(epochSeconds: Long): String =
        if (epochSeconds <= 0) "-" else stamp.format(Date(epochSeconds * 1000))

    fun sizeLabel(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /**
     * Which of [characters] already have a `.bak` next to their map, asked of the *device*, not of the
     * staged mirror.
     *
     * The mirror is a snapshot: after a restore or a delete it no longer matches what is on disk, and the
     * overwrite prompt must describe the real state.
     */
    suspend fun existingBackups(root: SaveRoot, characters: List<CharacterEntry>): Set<String> =
        withContext(Dispatchers.IO) {
            val remote = root.remoteBase
            if (remote == null) {
                return@withContext characters
                    .filter { it.hasMap }
                    .filter { File(it.mapFile.parentFile, it.mapFile.name + ".bak").isFile }
                    .map { it.name }
                    .toSet()
            }
            val maps = characters.filter { it.hasMap }
            if (maps.isEmpty()) return@withContext emptySet()
            val script = maps.joinToString("; ") { character ->
                val path = "$remote/Players/${character.directory.name}/${character.mapFile.name}"
                "if [ -f '$path.bak' ]; then echo '${character.name}'; fi"
            }
            val result = ShizukuShell.exec(script) ?: return@withContext emptySet()
            if (!result.ok) {
                Log.w(TAG, "existingBackups failed: code=${result.code} err=${result.err}")
                return@withContext emptySet()
            }
            result.out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    /** Scans one root and pairs every map with its backups. */
    suspend fun scan(root: SaveRoot, worlds: List<WorldEntry>): List<MapBackupRow> = withContext(Dispatchers.IO) {
        val remote = root.remoteBase
        val entries = if (remote != null) listRemote(remote) else listLocal(root.playersDir)
        val byWorld = worlds.associateBy { it.mapFileName.lowercase() }

        entries
            .filter { it.name.endsWith(".map", ignoreCase = true) }
            .map { map ->
                val parent = map.path.substringBeforeLast('/')
                val backups = entries
                    .filter { it.path != map.path && it.path.startsWith(map.path + ".bak") }
                    .map { BackupFile(it.name, it.path, it.size, it.modified) }
                    .sortedByDescending { it.modified }
                val character = parent.substringAfterLast('/')
                MapBackupRow(
                    mapName = map.name,
                    worldName = byWorld[map.name.lowercase()]?.name ?: map.name,
                    characterName = character,
                    mapPath = map.path,
                    mapSize = map.size,
                    mapModified = map.modified,
                    backups = backups,
                )
            }
            .sortedWith(compareBy({ it.worldName }, { it.characterName }))
    }

    /**
     * Copies the newest backup of every given row over its map.
     *
     * The backup is left in place: restoring is an undo, not a destructive operation, and the user may
     * want to look at the map first and restore again later.
     */
    suspend fun restore(rows: List<MapBackupRow>): List<ItemResult> = withContext(Dispatchers.IO) {
        rows.map { row ->
            val backup = row.newest
            if (backup == null) {
                ItemResult("${row.worldName} · ${row.characterName}", ItemStatus.SKIPPED, "没有备份")
            } else {
                when {
                    row.mapPath.startsWith("/") -> {
                        val code = TerraMirror.copyFile(backup.path, row.mapPath, preserveOwnerOf = row.mapPath)
                        if (code == 0) {
                            ItemResult("${row.worldName} · ${row.characterName}", ItemStatus.SUCCESS, "已恢复 ${backup.name}")
                        } else {
                            ItemResult("${row.worldName} · ${row.characterName}", ItemStatus.FAILED, "恢复失败（exit $code）")
                        }
                    }

                    else -> {
                        try {
                            File(backup.path).copyTo(File(row.mapPath), overwrite = true)
                            ItemResult("${row.worldName} · ${row.characterName}", ItemStatus.SUCCESS, "已恢复 ${backup.name}")
                        } catch (e: Exception) {
                            ItemResult("${row.worldName} · ${row.characterName}", ItemStatus.FAILED, e.message ?: "恢复失败")
                        }
                    }
                }
            }
        }
    }

    /** Deletes every backup of the given rows, keeping the maps themselves. */
    suspend fun deleteBackups(rows: List<MapBackupRow>): List<ItemResult> = withContext(Dispatchers.IO) {
        rows.map { row ->
            val label = "${row.worldName} · ${row.characterName}"
            if (row.backups.isEmpty()) {
                ItemResult(label, ItemStatus.SKIPPED, "没有备份")
            } else {
                val failures = row.backups.count { TerraMirror.remove(it.path) != 0 }
                if (failures == 0) ItemResult(label, ItemStatus.SUCCESS, "已删除 ${row.backups.size} 份备份")
                else ItemResult(label, ItemStatus.FAILED, "$failures 份删除失败")
            }
        }
    }

    /**
     * Removes the map itself, which the game then rebuilds as fully unexplored - the "all black" reset
     * players use to start a fresh exploration.
     */
    suspend fun deleteMaps(rows: List<MapBackupRow>): List<ItemResult> = withContext(Dispatchers.IO) {
        rows.map { row ->
            val label = "${row.worldName} · ${row.characterName}"
            if (TerraMirror.remove(row.mapPath) == 0) {
                ItemResult(label, ItemStatus.SUCCESS, "已删除地图（游戏内将显示为全黑）")
            } else {
                ItemResult(label, ItemStatus.FAILED, "删除失败")
            }
        }
    }

    private data class Entry(val name: String, val path: String, val size: Long, val modified: Long)

    private fun listLocal(playersDir: File): List<Entry> {
        if (!playersDir.isDirectory) return emptyList()
        val out = ArrayList<Entry>()
        playersDir.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            dir.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                if (!file.name.contains(".map")) return@forEach
                out.add(Entry(file.name, file.absolutePath, file.length(), file.lastModified() / 1000))
            }
        }
        return out
    }

    /** One shell round-trip for the whole tree: paths, sizes and mtimes on separate lines. */
    private fun listRemote(remoteBase: String): List<Entry> {
        val script = "find '$remoteBase/Players' -type f -name '*.map*' 2>/dev/null | " +
            "while read -r f; do printf '%s\\t%s\\t%s\\n' \"\$f\" \"\$(stat -c %s \"\$f\")\" \"\$(stat -c %Y \"\$f\")\"; done"
        val result = ShizukuShell.exec(script) ?: return emptyList()
        if (!result.ok) {
            Log.w(TAG, "backup scan failed: code=${result.code} err=${result.err}")
            return emptyList()
        }
        return result.out.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val size = parts[1].trim().toLongOrNull() ?: return@mapNotNull null
            val mtime = parts[2].trim().toLongOrNull() ?: 0L
            Entry(parts[0].substringAfterLast('/'), parts[0], size, mtime)
        }.toList()
    }
}

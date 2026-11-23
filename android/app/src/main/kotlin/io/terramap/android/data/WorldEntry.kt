// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.data

import io.terramap.core.PlayerMaps
import io.terramap.core.TerrariaFormatException
import io.terramap.core.WorldFile
import io.terramap.core.WorldMeta
import java.io.File
import java.io.RandomAccessFile

/** A `.wld` with its header decoded, plus the map file name the game will look for. */
data class WorldEntry(
    val file: File,
    val meta: WorldMeta,
    val mapFileName: String,
) {
    val name: String get() = meta.name
    val isMaster: Boolean get() = meta.difficulty == "大师" || meta.difficulty == "传奇"
}

/** A world file we could not read, kept so the UI can say why instead of failing silently. */
data class ScanIssue(val fileName: String, val reason: String)

data class ScanResult(
    val worlds: List<WorldEntry>,
    val issues: List<ScanIssue>,
)

/** A character directory and whether it already has a map for the selected world. */
data class CharacterEntry(
    val name: String,
    val directory: File,
    val mapFile: File,
) {
    val hasMap: Boolean get() = mapFile.isFile
}

object WorldScanner {

    /** Section 1 sits right after a ~167 byte header; 64 KiB is far more than the metadata walk needs. */
    private const val HEADER_PREFIX = 64 * 1024

    fun scan(files: List<File>): ScanResult {
        val worlds = ArrayList<WorldEntry>(files.size)
        val issues = ArrayList<ScanIssue>()
        for (file in files) {
            try {
                val meta = WorldFile.parseMeta(readPrefix(file))
                val mapName = PlayerMaps.mapFileNameForWorld(TerraPaths.hexToBytes(meta.guidHex))
                worlds.add(WorldEntry(file, meta, mapName))
            } catch (e: TerrariaFormatException) {
                issues.add(ScanIssue(file.name, e.message ?: "存档格式无法识别"))
            } catch (e: Exception) {
                issues.add(ScanIssue(file.name, e.message ?: e.javaClass.simpleName))
            }
        }
        worlds.sortBy { it.name.lowercase() }
        return ScanResult(worlds, issues)
    }

    fun characters(root: SaveRoot, entry: WorldEntry): List<CharacterEntry> =
        TerraPaths.characterDirs(root).map { dir ->
            CharacterEntry(
                name = PlayerMaps.playerNameFromDirectory(dir.path),
                directory = dir,
                mapFile = File(dir, entry.mapFileName),
            )
        }

    private fun readPrefix(file: File): ByteArray = RandomAccessFile(file, "r").use { raf ->
        val size = minOf(raf.length(), HEADER_PREFIX.toLong()).toInt()
        val buffer = ByteArray(size)
        raf.readFully(buffer)
        buffer
    }
}

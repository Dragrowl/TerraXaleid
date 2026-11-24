// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android.data

import android.os.Environment
import java.io.File

/** One save-tree: where `Worlds/` and `Players/` live for a given install. */
data class SaveRoot(
    val label: String,
    val worldsDir: File,
    val playersDir: File,
    /** Non-null when the directories above are a staged copy of this device path (Shizuku mode). */
    val remoteBase: String? = null,
) {
    /** True when writes must be copied back to [remoteBase] instead of being local. */
    val staged: Boolean get() = remoteBase != null
}

object TerraPaths {

    const val TERRARIA_PACKAGE = "com.and.games505.TerrariaPaid"

    private fun ext(): File = Environment.getExternalStorageDirectory()

    /**
     * Ordered candidates.
     *
     * Android 11+ hands out a per-app view of `/sdcard` in which another app's `Android/data`
     * directory is not listable at all — `MANAGE_EXTERNAL_STORAGE` does not change that. The
     * portable folders below are therefore the reliable path on a stock device: copy the two save
     * directories there and everything else works unchanged.
     */
    fun candidates(context: android.content.Context): List<SaveRoot> {
        val e = ext()
        val appDir = File(e, "Android/data/$TERRARIA_PACKAGE")
        return listOf(
            // Shizuku staging area: whichever code path filled it, the app can read it directly.
            // `remoteBase` stays null here, so reveal writes only into the staging copy.
            SaveRoot(
                label = "Shizuku 暂存",
                worldsDir = File(e, "Download/.terramap/Worlds"),
                playersDir = File(e, "Download/.terramap/Players"),
            ),
            SaveRoot(
                label = "Download",
                worldsDir = File(e, "Download/TerrariaSaves/Worlds"),
                playersDir = File(e, "Download/TerrariaSaves/Players"),
            ),
            SaveRoot(
                label = "Documents",
                worldsDir = File(e, "Documents/TerrariaSaves/Worlds"),
                playersDir = File(e, "Documents/TerrariaSaves/Players"),
            ),
            SaveRoot(
                label = "游戏目录",
                worldsDir = File(appDir, "Worlds"),
                playersDir = File(appDir, "Players"),
            ),
            SaveRoot(
                label = "游戏目录 (files)",
                worldsDir = File(appDir, "files/Worlds"),
                playersDir = File(appDir, "files/Players"),
            ),
            SaveRoot(
                label = "我的文档",
                worldsDir = File(e, "Documents/My Games/Terraria/Worlds"),
                playersDir = File(e, "Documents/My Games/Terraria/Players"),
            ),
            SaveRoot(
                label = "Terraria 目录",
                worldsDir = File(e, "Terraria/Worlds"),
                playersDir = File(e, "Terraria/Players"),
            ),
        )
    }

    fun existingRoots(context: android.content.Context): List<SaveRoot> =
        candidates(context).filter { it.worldsDir.isDirectory || it.playersDir.isDirectory }

    fun worldFiles(root: SaveRoot): List<File> =
        root.worldsDir.listFiles { f -> f.isFile && f.name.endsWith(".wld", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** Character folders, one per player that has ever entered this world. */
    fun characterDirs(root: SaveRoot): List<File> =
        root.playersDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "odd hex length: ${hex.length}" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

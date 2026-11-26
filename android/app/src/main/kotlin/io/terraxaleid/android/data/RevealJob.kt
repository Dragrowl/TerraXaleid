// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.android.data

import android.content.res.Resources
import android.util.Log
import io.terraxaleid.android.R
import io.terraxaleid.core.MapFile
import io.terraxaleid.core.MapHeader
import io.terraxaleid.core.Reveal
import io.terraxaleid.core.RevealOptions
import io.terraxaleid.core.World
import io.terraxaleid.core.WorldFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class ItemStatus { SUCCESS, SKIPPED, FAILED }

data class ItemResult(
    val label: String,
    val status: ItemStatus,
    val message: String = "",
)

data class RevealOutcome(
    val items: List<ItemResult>,
    val targets: List<File>,
    val bytesWritten: Int,
) {
    val succeeded: Int get() = items.count { it.status == ItemStatus.SUCCESS }
}

/**
 * Turns one world into one `.map` payload and copies it to every selected character.
 *
 * The payload is built once per world: the palette is world-wide, so every character gets byte-identical
 * bytes, and a large world is only parsed once. Originals are backed up to `<name>.map.bak` before the
 * replacement is renamed into place, so `undo` is a plain copy back.
 */
object RevealJob {

    private const val TAG = "terraxaleid"

    suspend fun reveal(
        entry: WorldEntry,
        characters: List<CharacterEntry>,
        header: MapHeader,
        root: SaveRoot? = null,
        keepPreviousBackup: Boolean = false,
        res: Resources,
        onProgress: (String, Float) -> Unit = { _, _ -> },
    ): RevealOutcome = withContext(Dispatchers.IO) {
        val items = ArrayList<ItemResult>()
        val targets = ArrayList<File>()
        var bytesWritten = 0

        val payload = try {
            onProgress(res.getString(R.string.run_reading_world, entry.name), 0.05f)
            val world = WorldFile.load(entry.file.readBytes())
            onProgress(res.getString(R.string.run_generating, world.width, world.height), 0.25f)
            val bytes = buildPayload(world, header)
            onProgress(res.getString(R.string.run_verifying), 0.6f)
            verify(bytes, world.width, world.height)
            bytes
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "reveal OOM on ${entry.file.name}", e)
            return@withContext RevealOutcome(
                items = listOf(ItemResult(entry.name, ItemStatus.FAILED, res.getString(R.string.run_too_large))),
                targets = emptyList(),
                bytesWritten = 0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "reveal failed on ${entry.file.name}", e)
            return@withContext RevealOutcome(
                items = listOf(ItemResult(entry.name, ItemStatus.FAILED, e.message ?: e.javaClass.simpleName)),
                targets = emptyList(),
                bytesWritten = 0,
            )
        }

        val total = characters.size.coerceAtLeast(1)
        characters.forEachIndexed { index, character ->
            val label = character.name
            onProgress(res.getString(R.string.run_writing, label), 0.6f + 0.4f * index / total)
            try {
                if (!character.directory.isDirectory && !character.directory.mkdirs()) {
                    items.add(ItemResult(label, ItemStatus.FAILED, res.getString(R.string.run_no_char_dir)))
                    return@forEachIndexed
                }
                val target = character.mapFile
                if (target.isFile) {
                    if (keepPreviousBackup) rotateLocalBackup(target)
                    val remoteForRotate = root?.remoteBase
                    if (keepPreviousBackup && remoteForRotate != null) {
                        TerraMirror.rotateBackup(remoteForRotate, root.playersDir, target)
                    }
                    target.copyTo(File(target.parentFile, target.name + ".bak"), overwrite = true)
                }
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.writeBytes(payload)
                if (target.isFile && !target.delete()) throw IOException(res.getString(R.string.run_replace_failed, target.name))
                if (!tmp.renameTo(target)) throw IOException(res.getString(R.string.run_write_failed, target.name))
                val remote = root?.remoteBase
                var sizeWarning = ""
                if (remote != null) {
                    val push = TerraMirror.pushBack(remote, root.playersDir, target)
                    if (!push.copied) {
                        val message = res.getString(R.string.run_push_failed, target.name, push.code)
                        val detail = if (push.error.isEmpty()) "" else " ${push.error}"
                        throw IOException(message + detail)
                    }
                    if (!push.sizeMatches) {
                        // The copy reported success; only the size read-back is odd. Keep the item as a
                        // success and say so, instead of claiming a failure the user cannot act on.
                        sizeWarning = res.getString(R.string.run_size_mismatch, push.written?.toString() ?: "?", push.expected.toString())
                    }
                }
                targets.add(target)
                bytesWritten += payload.size
                items.add(
                    ItemResult(
                        label,
                        ItemStatus.SUCCESS,
                        when {
                            remote != null && character.hasMap -> res.getString(R.string.run_written_back, sizeWarning)
                            remote != null -> res.getString(R.string.run_written_new, sizeWarning)
                            character.hasMap -> "已覆盖（原文件备份为 .map.bak）"
                            else -> "已新建地图"
                        },
                    ),
                )
            } catch (e: Exception) {
                Log.e(TAG, "write failed for $label", e)
                items.add(ItemResult(label, ItemStatus.FAILED, e.message ?: e.javaClass.simpleName))
            }
        }

        RevealOutcome(items, targets, bytesWritten)
    }

    suspend fun undo(targets: List<File>, root: SaveRoot? = null): List<ItemResult> = withContext(Dispatchers.IO) {
        targets.map { target ->
            val backup = File(target.parentFile, target.name + ".bak")
            try {
                val remote = root?.remoteBase
                if (remote != null) {
                    if (TerraMirror.restoreBackup(remote, root.playersDir, target)) {
                        ItemResult(target.name, ItemStatus.SUCCESS, "已从设备备份还原")
                    } else {
                        ItemResult(target.name, ItemStatus.SKIPPED, "设备上没有备份")
                    }
                } else if (!backup.isFile) {
                    ItemResult(target.name, ItemStatus.SKIPPED, "没有备份")
                } else {
                    backup.copyTo(target, overwrite = true)
                    ItemResult(target.name, ItemStatus.SUCCESS, "已还原")
                }
            } catch (e: Exception) {
                ItemResult(target.name, ItemStatus.FAILED, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Shifts `<name>.map.bak` to `.bak.1`, `.bak.1` to `.bak.2`, ... for the portable roots.
     *
     * Mirrors [TerraMirror.rotateBackup] so both roots keep the same history layout.
     */
    private fun rotateLocalBackup(target: File) {
        val newest = File(target.parentFile, target.name + ".bak")
        if (!newest.isFile) return
        var highest = 0
        target.parentFile?.listFiles()?.forEach { file ->
            val suffix = file.name.substringAfter(target.name + ".bak.", missingDelimiterValue = "")
            val index = suffix.toIntOrNull() ?: 0
            if (index > highest) highest = index
        }
        for (index in highest downTo 1) {
            val from = File(target.parentFile, "${target.name}.bak.$index")
            if (from.isFile) from.renameTo(File(target.parentFile, "${target.name}.bak.${index + 1}"))
        }
        newest.renameTo(File(target.parentFile, "${target.name}.bak.1"))
    }
    private fun buildPayload(world: World, header: MapHeader): ByteArray {
        require(header.width == world.width && header.height == world.height) {
            "地图头 ${header.width}×${header.height} 与世界 ${world.width}×${world.height} 不匹配"
        }
        return Reveal.write(header, world, RevealOptions(light = 255, overwriteExplored = true))
    }

    /** Cheap and sufficient: after a full overwrite every cell must read as explored. */
    private fun verify(payload: ByteArray, width: Int, height: Int) {
        val back = MapFile.read(payload)
        check(back.header.width == width && back.header.height == height) { "回读尺寸不一致" }
        val expected = width * height
        val explored = back.exploredCount()
        check(explored == expected) { "回读仅 $explored/$expected 格已点亮" }
    }
}

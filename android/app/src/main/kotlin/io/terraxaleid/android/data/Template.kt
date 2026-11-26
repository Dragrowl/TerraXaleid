// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.android.data

import android.content.res.Resources
import io.terraxaleid.android.R
import io.terraxaleid.core.MapHeader
import io.terraxaleid.core.TerrariaFormatException
import java.io.ByteArrayOutputStream
import java.io.File

sealed interface TemplateResult {
    data class Ready(val header: MapHeader, val source: String) : TemplateResult
    data class Missing(val reason: String) : TemplateResult
}

/**
 * A new `.map` can only be written from an existing header: the palette (option bitmaps + per-tile
 * variant counts) is fixed by the game build, not by the world. So the header is borrowed:
 *
 * 1. any character's map of the *same* world — the byte-exact case;
 * 2. a map of another world with the same dimensions — header re-serialised with this world's
 *    name/id (colours stay right because the palette is build-wide);
 * 3. nothing — the UI disables the button and explains what to do.
 */
object Templates {

    fun resolve(
        entry: WorldEntry,
        root: SaveRoot,
        allRoots: List<SaveRoot>,
        res: Resources,
    ): TemplateResult {
        var versionReason: String? = null

        // 1. same world, any character
        for (dir in TerraPaths.characterDirs(root)) {
            val candidate = File(dir, entry.mapFileName)
            if (!candidate.isFile) continue
            try {
                val header = MapHeader.parse(candidate.readBytes())
                if (header.width == entry.meta.width && header.height == entry.meta.height) {
                    return TemplateResult.Ready(header, res.getString(R.string.template_from_character, dir.name))
                }
            } catch (e: TerrariaFormatException) {
                versionReason = e.message
            }
        }

        // 2. donor with the same dimensions
        for (r in allRoots) {
            for (dir in TerraPaths.characterDirs(r)) {
                val maps = dir.listFiles { f -> f.isFile && f.name.endsWith(".map") } ?: continue
                for (map in maps) {
                    try {
                        val header = MapHeader.parse(map.readBytes())
                        if (header.width != entry.meta.width || header.height != entry.meta.height) continue
                        val rebuilt = rebuild(
                            header = header,
                            name = entry.meta.name,
                            worldId = entry.meta.worldId,
                            width = entry.meta.width,
                            height = entry.meta.height,
                        )
                        return TemplateResult.Ready(
                            rebuilt,
                            res.getString(R.string.template_from_donor, "${map.parentFile?.name}/${map.name}"),
                        )
                    } catch (e: TerrariaFormatException) {
                        versionReason = e.message
                    }
                }
            }
        }

        return TemplateResult.Missing(
            versionReason?.let { res.getString(R.string.template_unusable, it) }
                ?: res.getString(R.string.template_never_entered),
        )
    }

    /**
     * Re-serialises a `.map` header with a different world name / id / size. The option bitmaps are
     * rederived from the variant counts (`count != 1`), which is information-equivalent: a flagged
     * entry with count 1 and an unflagged entry both yield a single variant.
     */
    fun rebuild(header: MapHeader, name: String, worldId: Int, width: Int, height: Int): MapHeader {
        val out = ByteArrayOutputStream()
        out.writeLe32(MapHeader.FILE_VERSION)
        out.write(byteArrayOf(0x72, 0x65, 0x6C, 0x6F, 0x67, 0x69, 0x63)) // "relogic"
        out.write(MapHeader.FILE_TYPE)
        out.writeLe32(header.revision.toInt())
        out.writeLe64(0L)                                                 // favourite
        out.writeString7(name)
        out.writeLe32(worldId)
        out.writeLe32(height)
        out.writeLe32(width)
        out.writeLe16(header.maxTileId)
        out.writeLe16(header.maxWallId)
        out.writeLe16(4)                                                  // liquid gradients
        out.writeLe16(256)                                                // sky gradients
        out.writeLe16(256)                                                // dirt gradients
        out.writeLe16(256)                                                // rock gradients
        out.writeBits(header.tileCounts)
        out.writeBits(header.wallCounts)
        out.writeCounts(header.tileCounts)
        out.writeCounts(header.wallCounts)

        val bytes = out.toByteArray()
        val parsed = MapHeader.parse(bytes)
        check(parsed.width == width && parsed.height == height && parsed.worldId == worldId) {
            "rebuilt header did not round-trip"
        }
        check(parsed.worldName == name) { "rebuilt header lost the world name" }
        return parsed
    }

    private fun ByteArrayOutputStream.writeLe16(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        repeat(4) { write((value shr (8 * it)) and 0xFF) }
    }

    private fun ByteArrayOutputStream.writeLe64(value: Long) {
        repeat(8) { write(((value shr (8 * it)) and 0xFF).toInt()) }
    }

    private fun ByteArrayOutputStream.writeString7(value: String) {
        val raw = value.toByteArray(Charsets.UTF_8)
        var length = raw.size
        while (true) {
            val low = length and 0x7F
            length = length ushr 7
            if (length == 0) {
                write(low)
                break
            }
            write(low or 0x80)
        }
        write(raw)
    }

    /** LSB-first packing, mirroring `MapHeader.parse`'s `readBits`. */
    private fun ByteArrayOutputStream.writeBits(counts: IntArray) {
        val bytes = ByteArray((counts.size + 7) / 8)
        for (i in counts.indices) {
            if (counts[i] != 1) bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
        }
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeCounts(counts: IntArray) {
        for (count in counts) {
            if (count != 1) write(count.coerceIn(1, 255))
        }
    }
}

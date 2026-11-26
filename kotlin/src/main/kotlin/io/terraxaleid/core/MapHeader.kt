// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.core

/**
 * Parsed .map header plus the derived palette bases.
 *
 * The palette is 1-based: index 0 is "nothing", tile ids start at 1, then every wall, then the
 * liquids (4 slots), then 256 sky gradients, 256 dirt, 256 rock, and finally hell. Getting the
 * 1-based start wrong shifts every colour by one, which is exactly what produced the "everything
 * looks weird" map once.
 */
class MapHeader(
    val raw: ByteArray,
    val worldName: String,
    val worldId: Int,
    val width: Int,
    val height: Int,
    val maxTileId: Int,
    val maxWallId: Int,
    val tileCounts: IntArray,
    val wallCounts: IntArray,
    val revision: Long,
) {
    private val tilePrefix = IntArray(tileCounts.size + 1).also { p ->
        for (i in tileCounts.indices) p[i + 1] = p[i] + tileCounts[i]
    }
    private val wallPrefix = IntArray(wallCounts.size + 1).also { p ->
        for (i in wallCounts.indices) p[i + 1] = p[i] + wallCounts[i]
    }
    private val tileTotal = tilePrefix[tilePrefix.size - 1]
    private val wallTotal = wallPrefix[wallPrefix.size - 1]

    val tileBase: Int get() = 1
    val wallBase: Int get() = tileBase + tileTotal
    val liquidBase: Int get() = wallBase + wallTotal
    val skyBase: Int get() = liquidBase + 4
    val dirtBase: Int get() = skyBase + 256
    val rockBase: Int get() = dirtBase + 256
    val hellBase: Int get() = rockBase + 256

    fun tileType(tileId: Int): Int =
        if (tileId >= 0 && tileId < tilePrefix.size) tileBase + tilePrefix[tileId] else tileBase

    fun wallType(wallId: Int): Int =
        if (wallId >= 0 && wallId < wallPrefix.size) wallBase + wallPrefix[wallId] else wallBase

    /** Sky gradient slot: `byte(255 * y / surface)`, matching the game's own CalcSkyGradient. */
    fun skyType(y: Int, surface: Double): Int {
        if (surface <= 0.0) return skyBase
        val gradient = (255.0 * y / surface).toInt() and 0xFF
        return skyBase + gradient
    }

    companion object {
        const val FILE_VERSION = 33094
        const val FILE_TYPE = 1

        /** Cell grid, 64x64 per chunk, rounded up on both axes. */
        fun chunkGrid(width: Int, height: Int): Pair<Int, Int> =
            ((width + 63) / 64) to ((height + 63) / 64)

        fun parse(data: ByteArray): MapHeader {
            val r = SpanReader(data)

            val version = r.i32()
            if (version != FILE_VERSION) {
                throw TerrariaFormatException("unsupported map file version $version (expected $FILE_VERSION)")
            }

            val magic = r.take(7)
            val expected = byteArrayOf(0x72, 0x65, 0x6C, 0x6F, 0x67, 0x69, 0x63)
            if (!magic.contentEquals(expected)) {
                throw TerrariaFormatException("not a Terraria map file (magic mismatch)")
            }

            val fileType = r.u8()
            if (fileType != FILE_TYPE) throw TerrariaFormatException("unexpected map fileType $fileType")

            val revision = r.u32()
            r.skip(8)                                    // favorite
            val worldName = r.string7()
            val worldId = r.i32()
            val height = r.i32()
            val width = r.i32()
            val maxTileId = r.i16()
            val maxWallId = r.i16()
            r.skip(8)                                    // liquid / sky / dirt / rock gradient counts

            val tileFlags = readBits(r, maxTileId)
            val wallFlags = readBits(r, maxWallId)

            val tileCounts = IntArray(maxTileId) { if (tileFlags[it]) r.u8() else 1 }
            val wallCounts = IntArray(maxWallId) { if (wallFlags[it]) r.u8() else 1 }

            return MapHeader(
                raw = data.copyOfRange(0, r.position),
                worldName = worldName,
                worldId = worldId,
                width = width,
                height = height,
                maxTileId = maxTileId,
                maxWallId = maxWallId,
                tileCounts = tileCounts,
                wallCounts = wallCounts,
                revision = revision,
            )
        }

        /**
         * Option bitmaps are LSB-first inside each byte, and a fresh byte is fetched whenever the
         * mask wraps — verified against real 1.4.5.8 files.
         */
        private fun readBits(r: SpanReader, count: Int): BooleanArray {
            val bits = BooleanArray(count)
            var cur = 0
            var mask = 128
            for (i in 0 until count) {
                if (mask == 128) {
                    cur = r.u8()
                    mask = 1
                } else {
                    mask = mask shl 1
                }
                bits[i] = (cur and mask) == mask
            }
            return bits
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.core

import java.time.Instant
import java.time.ZoneId

/**
 * Reader for the two payloads L1 cares about: the .wld world file and, through [MapHeader] /
 * [MapFile], the .map explored-map cache.
 *
 * Nothing here opens a file: callers pass bytes in.
 */
object WorldFile {

    const val FORMAT = 326

    fun parseMeta(data: ByteArray): WorldMeta {
        val format = leadInt(data, 0)
        if (format != FORMAT) throw TerrariaFormatException("not a Terraria world file (format=$format)")
        if (!matchesMagic(data)) throw TerrariaFormatException("not a Terraria world file (magic mismatch)")

        val sectionCount = leadShort(data, 24)
        val section1 = leadInt(data, 26)

        val h = SpanReader(data)
        h.skip(section1)

        val name = h.string7()
        val seed = h.string7()
        h.skip(8)                                    // worldGenVersion
        val guidHex = h.take(16).joinToString("") { "%02x".format(it) }
        val worldId = h.i32()
        h.skip(16)                                   // left / right / top / bottom
        val height = h.i32()
        val width = h.i32()
        val gameMode = h.i32()

        val seedFlags = readBooleans(h, 9)
        val creation = h.i64()
        h.skip(8)                                    // last played
        h.skip(1)                                    // moon type
        h.skip(12)                                   // treeX[3]
        h.skip(16)                                   // treeStyle[4]
        h.skip(12)                                   // caveBackX[3]
        h.skip(16)                                   // caveBackStyle[4]
        h.skip(12)                                   // ice / jungle / hell background styles
        val spawnX = h.i32()
        val spawnY = h.i32()
        val worldSurface = h.f64()
        val rockLayer = h.f64()
        h.skip(8)                                    // time of day
        val dayTime = h.u8() != 0
        val moonPhase = h.i32()
        readBooleans(h, 2)                           // blood moon, eclipse
        val dungeonX = h.i32()
        val dungeonY = h.i32()
        val crimson = h.u8() != 0

        readBooleans(h, 18)                          // boss / progression flags
        readBooleans(h, 2)                           // shadow orb smashed, meteor spawned
        h.skip(1)                                    // shadow orb count
        h.skip(4)                                    // altar count
        val hardMode = h.u8() != 0
        h.skip(1)                                    // after party of doom
        h.skip(12)                                   // invasion delay / size / type
        h.skip(8)                                    // invasion x
        h.skip(8)                                    // slime rain time
        h.skip(1)                                    // sundial cooldown
        h.skip(1)                                    // raining
        h.skip(4)                                    // rain time
        h.skip(4)                                    // max rain
        h.skip(12)                                   // cobalt / mythril / adamantite
        h.skip(8)                                    // 8 background style bytes
        h.skip(4)                                    // cloud bg active
        h.skip(2)                                    // cloud count
        h.skip(4)                                    // wind speed target
        val anglerCount = h.i32().coerceAtLeast(0)
        repeat(anglerCount) { h.string7() }
        h.skip(1)                                    // saved angler
        h.skip(4)                                    // angler quest
        readBooleans(h, 3)                           // stylist, tax collector, golfer
        h.skip(4)                                    // invasion size start
        h.skip(4)                                    // cultist delay

        // BannerSystem.Load: int16 count + int32[count], then int16 count + uint16[count].
        // Miss the second array and every following flag shifts — a finished world reads as
        // "moon lord not defeated".
        val bannerCount = h.i16().coerceAtLeast(0)
        h.skip(4 * bannerCount)
        val claimCount = h.i16().coerceAtLeast(0)
        h.skip(2 * claimCount)

        h.skip(1)                                    // fast forward to dawn
        val endFlags = readBooleans(h, 4)            // fishron, martians, ancient cultist, moon lord

        val seedFlagNames = listOf(
            "drunk", "getGood", "tenthAnniv", "dontStarve", "notTheBees",
            "remix", "noTraps", "zenith", "skyblock",
        )
        val active = seedFlagNames.filterIndexed { i, _ -> seedFlags[i] }

        return WorldMeta(
            name = name,
            seed = seed,
            worldId = worldId,
            guidHex = guidHex,
            width = width,
            height = height,
            gameMode = gameMode,
            forTheWorthy = seedFlags[1],
            crimson = crimson,
            hardMode = hardMode,
            defeatedMoonLord = endFlags[3],
            spawnX = spawnX,
            spawnY = spawnY,
            worldSurface = worldSurface,
            rockLayer = rockLayer,
            dungeonX = dungeonX,
            dungeonY = dungeonY,
            moonPhase = moonPhase,
            dayTime = dayTime,
            creationTime = creation,
            seedFlags = active,
        ).also { require(sectionCount >= 2) { "world file declares $sectionCount section(s)" } }
    }

    /** Decodes the full tile grid so [Reveal] can rebuild the map palette from real blocks. */
    fun load(data: ByteArray): World {
        val format = leadInt(data, 0)
        if (format != FORMAT) throw TerrariaFormatException("not a Terraria world file (format=$format)")
        if (!matchesMagic(data)) throw TerrariaFormatException("not a Terraria world file (magic mismatch)")

        val sectionCount = leadShort(data, 24)
        if (sectionCount < 3) throw TerrariaFormatException("world file declares only $sectionCount section(s)")

        val offsets = IntArray(sectionCount) { leadInt(data, 26 + it * 4) }

        val maxImportant = leadShort(data, 70)
        val importantBits = data.copyOfRange(72, 72 + (maxImportant / 8) + 1)
        val important = BooleanArray(maxImportant + 1) { i ->
            ((importantBits[i / 8].toInt() shr (i % 8)) and 1) != 0
        }

        val hdr = SpanReader(data)
        hdr.skip(offsets[0])
        val name = hdr.string7()
        hdr.string7()                                 // seed
        hdr.skip(8)                                   // worldGenVersion
        hdr.skip(16)                                  // guid
        val worldId = hdr.i32()
        hdr.skip(16)                                  // pixel bounds
        val height = hdr.i32()
        val width = hdr.i32()

        // The two layer dividers sit at a fixed position in section 1; reading them from there is
        // exact, while scanning for a plausible pair can land on a neighbouring double and shift the
        // sky gradient by one.
        val (surface, rockLayer) = readLayers(data, offsets[0])

        val total = Math.multiplyExact(width, height)
        val tiles = ShortArray(total) { -1 }
        val walls = ShortArray(total) { -1 }
        val liquidAmounts = ByteArray(total)
        val liquidKinds = ByteArray(total)

        val start = offsets[1]
        val end = offsets[2]
        val r = SpanReader(data)
        r.skip(start)

        for (x in 0 until width) {
            var column = 0
            while (column < height) {
                val f1 = r.u8()
                var f2 = 0
                var f3 = 0
                if ((f1 and 1) != 0) {
                    f2 = r.u8()
                    if ((f2 and 1) != 0) {
                        f3 = r.u8()
                        if ((f3 and 1) != 0) r.skip(1)   // 4th flags byte: legal, unused here
                    }
                }

                val hasTile = ((f1 shr 1) and 1) != 0
                val hasWall = ((f1 shr 2) and 1) != 0
                val liquid = (f1 shr 3) and 3
                val wideId = ((f1 shr 5) and 1) != 0
                val rle = (((f1 shr 7) and 1) * 2) + ((f1 shr 6) and 1)

                var tileId = -1
                var wallId = -1

                if (hasTile) {
                    tileId = if (wideId) r.u16() else r.u8()
                    if (tileId <= maxImportant && important[tileId]) r.skip(4)      // frame u, v
                    if (((f3 shr 3) and 1) != 0) r.skip(1)                          // tile paint
                }

                if (hasWall) {
                    wallId = r.u8()
                    if (((f3 shr 4) and 1) != 0) r.skip(1)                          // wall paint
                }

                // 1..3 = water / lava / honey, and bit 23 upgrades honey to shimmer. The `.map`
                // palette stores the same four slots in that order, so `liquidKind` is the
                // palette offset and the raw amount decides whether the cell is worth painting.
                val shimmer = ((f3 shr 7) and 1) != 0
                var liquidKind = 0
                if (shimmer) liquidKind = 3 else if (liquid != 0) liquidKind = liquid - 1
                val liquidAmount = if (liquid != 0 || shimmer) r.u8() else 0
                if (((f3 shr 6) and 1) != 0) wallId = wallId or (r.u8() shl 8)      // wall id high byte

                var repeat = 1
                if (rle == 2) repeat = r.u16() + 1
                else if (rle == 1) repeat = r.u8() + 1

                // RLE never crosses a column boundary: every column holds exactly `height` tiles.
                if (repeat > height - column) repeat = height - column

                val base = x * height + column
                for (i in 0 until repeat) {
                    tiles[base + i] = tileId.toShort()
                    walls[base + i] = wallId.toShort()
                    if (liquidAmount != 0) {
                        liquidAmounts[base + i] = liquidAmount.toByte()
                        liquidKinds[base + i] = liquidKind.toByte()
                    }
                }

                column += repeat
            }
        }

        if (r.position != end) {
            throw TerrariaFormatException("tile stream ended at ${r.position}, expected section 2 at $end")
        }

        return World(name, worldId, width, height, surface, rockLayer, tiles, walls, liquidAmounts, liquidKinds)
    }

    fun decodeCreationTime(raw: Long): Instant {
        val kind = ((raw ushr 62) and 0x3)
        val ticks = raw and 0x3FFFFFFFFFFFFFFFL
        val millisFromTicks = ticks / 10_000L
        // ticks are 100ns units since 0001-01-01; unix epoch starts 621355968000000000 ticks in.
        val unixMillis = (ticks - 621355968000000000L) / 10_000L
        return Instant.ofEpochMilli(unixMillis)
    }

    fun decodeCreationTimeLocal(raw: Long): String =
        decodeCreationTime(raw).atZone(ZoneId.systemDefault()).toString()

    // ------------------------------------------------------------------ helpers

    private fun readBooleans(r: SpanReader, count: Int): BooleanArray =
        BooleanArray(count) { r.u8() != 0 }

    private fun matchesMagic(data: ByteArray): Boolean {
        if (data.size < 11) return false
        val magic = byteArrayOf(0x72, 0x65, 0x6C, 0x6F, 0x67, 0x69, 0x63)  // "relogic"
        for (i in magic.indices) if (data[4 + i] != magic[i]) return false
        return true
    }

    private fun leadInt(data: ByteArray, at: Int): Int =
        (data[at].toInt() and 0xFF) or
            ((data[at + 1].toInt() and 0xFF) shl 8) or
            ((data[at + 2].toInt() and 0xFF) shl 16) or
            ((data[at + 3].toInt() and 0xFF) shl 24)

    private fun leadShort(data: ByteArray, at: Int): Int =
        (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)

    /**
     * Reads worldSurface and rockLayer straight out of section 1 — they follow the spawn point, which
     * is exactly where the game writes them. Used by [load]; the heuristic below is only a fallback.
     */
    fun readLayers(data: ByteArray, section1: Int): Pair<Double, Double> {
        val h = SpanReader(data)
        h.skip(section1)
        h.string7()                        // name
        h.string7()                        // seed
        h.skip(8)                          // worldGenVersion
        h.skip(16)                         // guid
        h.skip(4)                          // worldID
        h.skip(16)                         // pixel bounds
        h.skip(4)                          // maxTilesY
        h.skip(4)                          // maxTilesX
        h.skip(4)                          // gameMode
        h.skip(9)                          // seed flags
        h.skip(8)                          // creation time
        h.skip(8)                          // last played
        h.skip(1)                          // moon type
        h.skip(12 + 16 + 12 + 16 + 12)     // tree / cave / background styles
        h.skip(4)                          // spawnTileX
        h.skip(4)                          // spawnTileY
        return h.f64() to h.f64()
    }

    /**
     * Locates the two doubles that separate surface / underground / cavern. They sit right after the
     * spawn point and are roughly 264 tiles apart; if the pattern is missing we fall back to the
     * fractions the generator itself uses.
     */
    fun findLayerFloats(section1: ByteArray, height: Int): Pair<Double, Double> {
        val fallback = (height * 0.274) to (height * 0.42)
        var bestScore = Int.MAX_VALUE
        var best: Pair<Double, Double>? = null

        var at = 0
        while (at + 16 <= section1.size) {
            val a = readDouble(section1, at)
            val b = readDouble(section1, at + 8)
            if (a > 50.0 && a < height - 100 &&
                b > a + 20 && b < height - 50
            ) {
                val score = kotlin.math.abs((b - a) - 264.0).toInt()
                if (score < 90 && score < bestScore) {
                    bestScore = score
                    best = a to b
                }
            }
            at += 4
        }

        return best ?: fallback
    }

    private fun readDouble(data: ByteArray, at: Int): Double {
        var bits = 0L
        for (i in 7 downTo 0) bits = (bits shl 8) or (data[at + i].toLong() and 0xFFL)
        return java.lang.Double.longBitsToDouble(bits)
    }
}

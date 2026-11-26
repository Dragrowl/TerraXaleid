// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.android

import io.terraxaleid.android.data.Templates
import io.terraxaleid.core.MapData
import io.terraxaleid.core.MapFile
import io.terraxaleid.core.MapHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * The donor-header path re-serialises a `.map` header with another world's identity. If that
 * round-trip is wrong the game refuses the file or files it under the wrong world, so it is pinned
 * here with plain JUnit instead of a device test.
 */
class HeaderRebuildTest {

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    private fun le32(v: Int) = ByteArray(4) { ((v shr (8 * it)) and 0xFF).toByte() }

    private fun le64(v: Long) = ByteArray(8) { ((v shr (8 * it)) and 0xFF).toByte() }

    private fun string7(value: String): ByteArray {
        val raw = value.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        var length = raw.size
        while (true) {
            val low = length and 0x7F
            length = length ushr 7
            if (length == 0) {
                out.write(low)
                break
            }
            out.write(low or 0x80)
        }
        out.write(raw)
        return out.toByteArray()
    }

    /** Header with no variant flags at all: every tile/wall has exactly one palette entry. */
    private fun syntheticHeader(width: Int, height: Int, maxTile: Int, maxWall: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(le32(MapHeader.FILE_VERSION))
        out.write(byteArrayOf(0x72, 0x65, 0x6C, 0x6F, 0x67, 0x69, 0x63))
        out.write(MapHeader.FILE_TYPE)
        out.write(le32(68))
        out.write(le64(0))
        out.write(string7("donor"))
        out.write(le32(1234))
        out.write(le32(height))
        out.write(le32(width))
        out.write(le16(maxTile))
        out.write(le16(maxWall))
        out.write(le16(4))
        out.write(le16(256))
        out.write(le16(256))
        out.write(le16(256))
        out.write(ByteArray((maxTile + 7) / 8))
        out.write(ByteArray((maxWall + 7) / 8))
        return out.toByteArray()
    }

    @Test
    fun rebuildKeepsPaletteAndSwapsIdentity() {
        val donor = MapHeader.parse(syntheticHeader(70, 70, 8, 4))
        val rebuilt = Templates.rebuild(
            header = donor,
            name = "新的世界",
            worldId = 750061081,
            width = 70,
            height = 70,
        )

        assertEquals("新的世界", rebuilt.worldName)
        assertEquals(750061081, rebuilt.worldId)
        assertEquals(70, rebuilt.width)
        assertEquals(70, rebuilt.height)
        assertEquals(donor.maxTileId, rebuilt.maxTileId)
        assertEquals(donor.wallBase, rebuilt.wallBase)
        assertEquals(donor.hellBase, rebuilt.hellBase)
        assertTrue("header must stay small", rebuilt.raw.size < 4096)
    }

    @Test
    fun rebuiltHeaderSurvivesAMapRoundTrip() {
        val header = Templates.rebuild(
            header = MapHeader.parse(syntheticHeader(70, 70, 8, 4)),
            name = "round trip",
            worldId = 99,
            width = 70,
            height = 70,
        )
        val total = 70 * 70
        val data = MapData(
            header = header,
            types = ShortArray(total) { (it % 9).toShort() },
            lights = ByteArray(total) { 255.toByte() },
            colors = ByteArray(total),
        )
        val back = MapFile.read(MapFile.write(header, data))
        assertEquals(70, back.header.width)
        assertEquals(70, back.header.height)
        assertEquals("round trip", back.header.worldName)
        assertEquals(total, back.exploredCount())
    }
}

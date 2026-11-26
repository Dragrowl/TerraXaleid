// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.core

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Runnable check for the Kotlin L1 port. Uses synthetic bytes unless `--with-samples WORLD MAP` is
 * passed; either way it never writes a file and never talks to a device.
 */
private var allOk = true

private fun check(condition: Boolean, message: String) {
    println((if (condition) "ok   " else "FAIL ") + message)
    if (!condition) allOk = false
}

private fun le(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value shr 8) and 0xFF).toByte(),
    ((value shr 16) and 0xFF).toByte(),
    ((value shr 24) and 0xFF).toByte(),
)

private fun leDouble(value: Double): ByteArray {
    val bits = java.lang.Double.doubleToLongBits(value)
    return ByteArray(8) { i -> ((bits ushr (8 * i)) and 0xFF).toByte() }
}

private fun leShort(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value shr 8) and 0xFF).toByte(),
)

private fun relogic(): ByteArray = byteArrayOf(0x72, 0x65, 0x6C, 0x6F, 0x67, 0x69, 0x63)

fun main(args: Array<String>) {
    paletteMath()
    chunkGridMath()
    string7()
    difficultyTable()
    roundTripSynthetic()
    revealOverwritesEverything()
    importantFrameSkip()
    playerMapNaming()
    largeWorldBuild()

    if (args.size >= 3 && args[0] == "--with-samples") {
        realFiles(
            java.io.File(args[1]).readBytes(),
            java.io.File(args[2]).readBytes(),
            if (args.size >= 4) args[3] else null,
        )
    }

    println()
    println("RESULT: " + (if (allOk) "PASS" else "FAIL"))
    if (!allOk) kotlin.system.exitProcess(1)
}

private fun paletteMath() {
    val header = MapHeader(
        raw = ByteArray(0),
        worldName = "t",
        worldId = 1,
        width = 128,
        height = 128,
        maxTileId = 4,
        maxWallId = 3,
        tileCounts = intArrayOf(10, 20, 30, 40),
        wallCounts = intArrayOf(7, 8, 9),
        revision = 68,
    )

    check(header.tileBase == 1, "tile base is 1 (got ${header.tileBase})")
    check(header.tileType(0) == 1 && header.tileType(1) == 11, "tile prefix is 1-based")
    check(header.wallBase == 101, "wall base (${header.wallBase})")
    check(header.liquidBase == 125, "liquid base (${header.liquidBase})")
    check(header.skyBase == 129, "sky base (${header.skyBase})")
    check(header.dirtBase == 385, "dirt base (${header.dirtBase})")
    check(header.rockBase == 641, "rock base (${header.rockBase})")
    check(header.hellBase == 897, "hell base (${header.hellBase})")
    check(header.skyType(0, 500.0) == 129, "sky index at y=0")
    check(header.skyType(250, 500.0) == 129 + 127, "sky index at half of surface")
    check(header.skyType(1000, 500.0) == 129 + 254, "sky index wraps through the byte cast at 2x surface")
}

private fun chunkGridMath() {
    check(MapHeader.chunkGrid(70, 70) == (2 to 2), "chunk grid for 70x70 is 2x2")
    check(MapHeader.chunkGrid(6400, 1800) == (100 to 29), "chunk grid for 6400x1800 is 100x29")
    check(MapHeader.chunkGrid(8400, 2400) == (132 to 38), "chunk grid for 8400x2400 is 132x38")
    check(MapHeader.chunkGrid(4200, 1200) == (66 to 19), "chunk grid for 4200x1200 is 66x19")
}

private fun string7() {
    val short = byteArrayOf(0x05) + "hello".toByteArray()
    val r = SpanReader(short)
    check(r.string7() == "hello" && r.position == short.size, "7-bit string, single-byte length")

    val payload = "a".repeat(129)
    val long = byteArrayOf(0x81.toByte(), 0x01) + payload.toByteArray()
    val r2 = SpanReader(long)
    val decoded = r2.string7()
    check(decoded.length == 129 && r2.position == long.size, "7-bit string, two-byte length")
}

private fun difficultyTable() {
    val cases = listOf(
        Triple(0, false, "普通"), Triple(1, false, "专家"),
        Triple(2, false, "大师"), Triple(3, false, "旅行"),
        Triple(0, true, "专家"), Triple(1, true, "大师"),
        Triple(2, true, "传奇"), Triple(3, true, "旅行"),
    )
    for ((mode, ftw, expected) in cases) {
        val actual = WorldMeta.difficultyLabel(mode, ftw)
        check(actual == expected, "difficulty GameMode=$mode FTW=${if (ftw) 1 else 0} -> $expected (got $actual)")
    }
}

private const val SYNTHETIC_SIZE = 70          // deliberately not a multiple of 64

private fun syntheticHeader(width: Int, height: Int, maxTile: Int, maxWall: Int): MapHeader {
    val raw = ByteArrayOutputStream()
    raw.write(le(MapHeader.FILE_VERSION))
    raw.write(relogic())
    raw.write(MapHeader.FILE_TYPE)
    raw.write(le(68))                            // revision
    raw.write(ByteArray(8))                      // favorite
    val name = "synthetic".toByteArray()
    raw.write(name.size)
    raw.write(name)
    raw.write(le(7))                             // worldID
    raw.write(le(height))
    raw.write(le(width))
    raw.write(leShort(maxTile))
    raw.write(leShort(maxWall))
    raw.write(leShort(4))
    raw.write(leShort(256))
    raw.write(leShort(256))
    raw.write(leShort(256))
    // every option flag false -> every count is the implicit 1; the reader consumes ceil(n/8) bytes
    raw.write(ByteArray((maxTile + 7) / 8))
    raw.write(ByteArray((maxWall + 7) / 8))

    return MapHeader(
        raw = raw.toByteArray(),
        worldName = "synthetic",
        worldId = 7,
        width = width,
        height = height,
        maxTileId = maxTile,
        maxWallId = maxWall,
        tileCounts = IntArray(maxTile) { 1 },
        wallCounts = IntArray(maxWall) { 1 },
        revision = 68,
    )
}

private fun roundTripSynthetic() {
    val header = syntheticHeader(SYNTHETIC_SIZE, SYNTHETIC_SIZE, 8, 4)
    val total = SYNTHETIC_SIZE * SYNTHETIC_SIZE

    val types = ShortArray(total) { (it % 12).toShort() }
    val lights = ByteArray(total) { (it % 255).toByte() }
    val colors = ByteArray(total) { 2 }

    val image = MapFile.write(header, MapData(header, types, lights, colors))
    check(image.isNotEmpty(), "wrote ${image.size} bytes")

    val back = MapFile.read(image)
    check(back.header.width == SYNTHETIC_SIZE && back.header.height == SYNTHETIC_SIZE, "header round-trips size")
    check(back.header.worldName == "synthetic" && back.header.worldId == 7, "header round-trips name and worldID")
    check(back.header.tileBase == 1 && back.header.wallBase == 9, "palette survives round-trip")
    check(back.types.contentEquals(types), "types round-trip")
    check(back.lights.contentEquals(lights), "lights round-trip")
    check(back.colors.contentEquals(colors), "colors round-trip")
}

private fun revealOverwritesEverything() {
    val header = syntheticHeader(SYNTHETIC_SIZE, SYNTHETIC_SIZE, 8, 4)
    val size = SYNTHETIC_SIZE
    val total = size * size

    val tiles = ShortArray(total) { -1 }
    val walls = ShortArray(total) { -1 }
    for (i in 0 until total) {
        when (i % 3) {
            0 -> tiles[i] = (i % 5).toShort()
            1 -> walls[i] = (i % 4).toShort()
        }
    }

    val world = World("synthetic", 7, size, size, 20.0, 40.0, tiles, walls)
    val revealed = Reveal.build(header, world)

    check(revealed.lights.all { (it.toInt() and 0xFF) == 255 }, "every cell is lit")
    check(revealed.types.none { it.toInt() == 999 }, "no stale template type survives the reveal")

    val solidTypes = revealed.types.count { it.toInt() in header.tileBase until header.wallBase }
    check(solidTypes > 0, "solid cells carried over ($solidTypes cells mapped to the tile palette)")

    val emptyTiles = ShortArray(total) { -1 }
    val emptyWorld = World("synthetic", 7, size, size, 20.0, 40.0, emptyTiles, emptyTiles.copyOf())
    val bg = Reveal.build(header, emptyWorld)
    check(bg.types[0].toInt() == header.skyType(0, 20.0), "row 0 background is the sky gradient")
    check(bg.types[total - 1].toInt() == header.hellBase, "last row background is hell")
}

/**
 * A 3x3 world whose first cell uses an "important" tile id, so the reader must consume the extra
 * 4 frame bytes and still land exactly on the tile section end.
 */
private fun importantFrameSkip() {
    val width = 3
    val height = 3
    val section1Offset = 167
    val maxImportant = 754

    val bytes = ByteArrayOutputStream()
    bytes.write(le(326))
    bytes.write(relogic())
    bytes.write(2)                                        // fileType
    while (bytes.size() < 24) bytes.write(0)
    bytes.write(leShort(3))                               // section count

    val section1 = ByteArrayOutputStream()
    section1.write(1); section1.write('t'.code)           // name
    section1.write(1); section1.write('s'.code)           // seed
    section1.write(ByteArray(8))                          // worldGenVersion
    section1.write(ByteArray(16))                         // guid
    section1.write(le(9))                                 // worldID
    section1.write(ByteArray(16))                         // pixel bounds
    section1.write(le(height))
    section1.write(le(width))
    // pad up to the spawn point, then write the two layer dividers load() reads
    while (section1.size() < 162) section1.write(0)
    section1.write(leDouble(2.0))
    section1.write(leDouble(3.0))
    while (section1.size() < 178) section1.write(0)

    val tileStart = section1Offset + section1.size()

    // column 0: tile id 1 (important -> 4 frame bytes), then two empty cells
    val tileStream = ByteArrayOutputStream()
    tileStream.write(byteArrayOf(0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
    repeat(2) { tileStream.write(byteArrayOf(0x00, 0x00, 0x00)) }

    val tileEnd = tileStart + tileStream.size()

    bytes.write(le(section1Offset))
    bytes.write(le(tileStart))
    bytes.write(le(tileEnd))
    while (bytes.size() < 70) bytes.write(0)
    bytes.write(leShort(maxImportant))

    val bitmap = ByteArray((maxImportant / 8) + 1)
    bitmap[0] = (bitmap[0].toInt() or 0x02).toByte()      // tile id 1 is "important"
    bytes.write(bitmap)

    while (bytes.size() < section1Offset) bytes.write(0)
    bytes.write(section1.toByteArray())
    bytes.write(tileStream.toByteArray())

    val world = WorldFile.load(bytes.toByteArray())
    check(world.width == width && world.height == height, "synthetic .wld size parsed")
    check(world.tileAt(0, 0) == 1, "important tile decoded")
    check(world.tileAt(0, 1) == -1 && world.tileAt(2, 2) == -1, "empty cells decoded as -1")
    check(world.solidCount() == 1, "exactly one solid tile (got ${world.solidCount()})")
}

private fun playerMapNaming() {
    val guid = ByteArray(16) { i -> ("e957d5c397d37942a7b46e8dc32adff1".substring(i * 2, i * 2 + 2).toInt(16)).toByte() }
    check(
        PlayerMaps.mapFileNameForWorld(guid) == "c3d557e9-d397-4279-a7b4-6e8dc32adff1.map",
        "world guid becomes the .map file name",
    )
    check(
        PlayerMaps.guidFromMapFileName("c3d557e9-d397-4279-a7b4-6e8dc32adff1.map").contentEquals(guid),
        ".map file name round-trips to the stored guid",
    )
    check(
        PlayerMaps.playerNameFromDirectory("""C:\x\Players\Marina""") == "Marina",
        "character name comes from the directory",
    )
}

private fun largeWorldBuild() {
    val width = 6400
    val height = 1800
    val header = syntheticHeader(width, height, 8, 4)

    val total = width * height
    val tiles = ShortArray(total) { -1 }
    val walls = ShortArray(total) { -1 }
    var i = 0
    while (i < total) {
        tiles[i] = 1
        i += 7
    }

    val world = World("big", 1, width, height, 493.0, 757.0, tiles, walls)
    val runtime = Runtime.getRuntime()
    System.gc()
    val before = runtime.totalMemory() - runtime.freeMemory()
    val data = Reveal.build(header, world)
    val after = runtime.totalMemory() - runtime.freeMemory()

    check(data.cellCount == total, "large world built all $total cells")
    check((data.lights[total - 1].toInt() and 0xFF) == 255, "large world is fully lit")
    println("info large-world heap delta ~${(after - before) / (1024 * 1024)} MB")
}

private fun realFiles(worldBytes: ByteArray, mapBytes: ByteArray, dumpPath: String?) {
    val world = WorldFile.load(worldBytes)
    val meta = WorldFile.parseMeta(worldBytes)
    val map = MapFile.read(mapBytes)

    check(world.worldId == map.header.worldId, "worldID matches (${world.worldId})")
    check(world.width == map.header.width && world.height == map.header.height,
        "size matches (${world.width}x${world.height})")
    println("info ${meta.name} | ${meta.difficulty} | ${meta.size} | ${meta.evil} | " +
        "hardmode=${meta.hardMode} moonlord=${meta.defeatedMoonLord}")

    val revealed = Reveal.write(map.header, world)
    val back = MapFile.read(revealed)
    check(back.exploredCount() == world.width * world.height, "revealed payload is fully explored")

    val digest = MessageDigest.getInstance("SHA-256").digest(revealed)
        .joinToString("") { "%02X".format(it) }
    println("info reveal size ${revealed.size} bytes, sha256 $digest")
    println("info decoded-grid sha256 ${gridDigest(back)}")
    if (dumpPath != null) { java.io.File(dumpPath).writeBytes(revealed); println("info wrote $dumpPath") }
}

/**
 * Digest of the decoded grid, independent of the compressor: two implementations that encode the
 * same map in a different number of bytes still agree here.
 */
private fun gridDigest(data: MapData): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(4)
    for (i in 0 until data.cellCount) {
        val t = data.types[i].toInt()
        buffer[0] = (t and 0xFF).toByte()
        buffer[1] = ((t shr 8) and 0xFF).toByte()
        md.update(buffer, 0, 2)
        buffer[0] = data.lights[i]
        buffer[1] = data.colors[i]
        md.update(buffer, 0, 2)
    }
    return md.digest().joinToString("") { "%02X".format(it) }
}

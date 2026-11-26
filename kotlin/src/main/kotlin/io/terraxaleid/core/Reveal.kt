// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.core

/** Knobs for the reveal step. The default is a full overwrite, which is what was asked for. */
data class RevealOptions(
    val light: Int = 255,
    val overwriteExplored: Boolean = true,
    val compressionLevel: Int = 9,
) {
    init {
        require(light in 0..255) { "light must fit in a byte" }
        require(compressionLevel in 0..9) { "compression level must be 0..9" }
    }
}

/**
 * Rebuilds a complete .map payload from a world.
 *
 * Every cell gets a type and a full-brightness light value. Priority follows
 * `MapHelper.CreateMapTile`: a block wins, then a block-less cell that carries enough liquid shows
 * the liquid colour, then the wall, then the depth background. Nothing keeps its previous state;
 * partial reveal would need the previous payload, so it is rejected rather than silently degrading
 * into a full overwrite.
 */
object Reveal {

    fun build(
        header: MapHeader,
        world: World,
        options: RevealOptions = RevealOptions(),
    ): MapData {
        require(!options.overwriteExplored.not()) {
            "partial reveal needs the previous payload; the reveal path is full-overwrite by design"
        }
        require(header.width == world.width && header.height == world.height) {
            "map header is ${header.width}x${header.height} but the world is ${world.width}x${world.height}"
        }

        val width = world.width
        val height = world.height
        val total = width * height

        // Background depends only on the row, so it is computed once per y instead of per cell.
        val background = IntArray(height) { y ->
            when {
                y < world.surface -> header.skyType(y, world.surface)
                y < world.rockLayer -> header.dirtBase
                y < world.underworldLayer -> header.rockBase
                else -> header.hellBase
            }
        }

        val types = ShortArray(total)
        val lights = ByteArray(total) { options.light.toByte() }
        val colors = ByteArray(total)

        for (y in 0 until height) {
            val bg = background[y]
            for (x in 0 until width) {
                val index = x * height + y
                val tile = world.tiles[index].toInt()
                val wall = world.walls[index].toInt()
                val liquidAmount = world.liquidAmounts[index].toInt() and 0xFF
                types[index] = when {
                    tile >= 0 -> header.tileType(tile)
                    liquidAmount > World.LIQUID_PAINT_THRESHOLD ->
                        header.liquidBase + (world.liquidKinds[index].toInt() and 0xFF)
                    wall >= 0 -> header.wallType(wall)
                    else -> bg
                }.toShort()
            }
        }

        return MapData(header, types, lights, colors)
    }

    fun write(
        header: MapHeader,
        world: World,
        options: RevealOptions = RevealOptions(),
    ): ByteArray = MapFile.write(header, build(header, world, options), options.compressionLevel)
}

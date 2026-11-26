// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.core

/**
 * Decoded world grid. Column-major, exactly like the .wld tile stream:
 * `index = x * height + y`, and `-1` means "no tile" / "no wall".
 *
 * Liquid is kept next to the blocks because the game paints it into the map as its own colour:
 * [liquidAmounts] holds the 0..255 fill level (the map only draws a cell above
 * [LIQUID_PAINT_THRESHOLD]) and [liquidKinds] holds 0 water / 1 lava / 2 honey / 3 shimmer,
 * matching the `.map` liquid palette order.
 */
class World(
    val name: String,
    val worldId: Int,
    val width: Int,
    val height: Int,
    val surface: Double,
    val rockLayer: Double,
    val tiles: ShortArray,
    val walls: ShortArray,
    val liquidAmounts: ByteArray = ByteArray(tiles.size),
    val liquidKinds: ByteArray = ByteArray(tiles.size),
) {
    init {
        require(tiles.size == width * height) { "tile array is ${tiles.size}, expected ${width * height}" }
        require(walls.size == width * height) { "wall array is ${walls.size}, expected ${width * height}" }
        require(liquidAmounts.size == tiles.size) { "liquid array is ${liquidAmounts.size}, expected ${tiles.size}" }
        require(liquidKinds.size == tiles.size) { "liquid kind array is ${liquidKinds.size}, expected ${tiles.size}" }
    }

    val cellCount: Int get() = width * height
    val underworldLayer: Int get() = height - 200

    fun index(x: Int, y: Int): Int = x * height + y
    fun tileAt(x: Int, y: Int): Int = tiles[index(x, y)].toInt()
    fun wallAt(x: Int, y: Int): Int = walls[index(x, y)].toInt()
    fun liquidAmountAt(x: Int, y: Int): Int = liquidAmounts[index(x, y)].toInt() and 0xFF
    fun liquidKindAt(x: Int, y: Int): Int = liquidKinds[index(x, y)].toInt() and 0xFF
    fun solidCount(): Int = tiles.count { it.toInt() >= 0 }
    fun wallOnlyCount(): Int = tiles.indices.count { tiles[it].toInt() < 0 && walls[it].toInt() >= 0 }
    fun liquidCount(): Int = liquidAmounts.count { (it.toInt() and 0xFF) > LIQUID_PAINT_THRESHOLD }

    companion object {
        /** `MapHelper.GetWallType` only shows liquid above this fill level. */
        const val LIQUID_PAINT_THRESHOLD = 32
    }
}

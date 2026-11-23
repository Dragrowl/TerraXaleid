// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.core

/**
 * Decoded world grid. Column-major, exactly like the .wld tile stream:
 * `index = x * height + y`, and `-1` means "no tile" / "no wall".
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
) {
    init {
        require(tiles.size == width * height) { "tile array is ${tiles.size}, expected ${width * height}" }
        require(walls.size == width * height) { "wall array is ${walls.size}, expected ${width * height}" }
    }

    val cellCount: Int get() = width * height
    val underworldLayer: Int get() = height - 200

    fun index(x: Int, y: Int): Int = x * height + y
    fun tileAt(x: Int, y: Int): Int = tiles[index(x, y)].toInt()
    fun wallAt(x: Int, y: Int): Int = walls[index(x, y)].toInt()
    fun solidCount(): Int = tiles.count { it.toInt() >= 0 }
    fun wallOnlyCount(): Int = tiles.indices.count { tiles[it].toInt() < 0 && walls[it].toInt() >= 0 }
}

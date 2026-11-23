// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.core

/**
 * Everything the save list needs, read from section 1 of a .wld without decoding tiles.
 */
data class WorldMeta(
    val name: String,
    val seed: String,
    val worldId: Int,
    val guidHex: String,
    val width: Int,
    val height: Int,
    val gameMode: Int,
    val forTheWorthy: Boolean,
    val crimson: Boolean,
    val hardMode: Boolean,
    val defeatedMoonLord: Boolean,
    val spawnX: Int,
    val spawnY: Int,
    val worldSurface: Double,
    val rockLayer: Double,
    val dungeonX: Int,
    val dungeonY: Int,
    val moonPhase: Int,
    val dayTime: Boolean,
    val creationTime: Long,
    val seedFlags: List<String>,
) {
    val difficulty: String get() = difficultyLabel(gameMode, forTheWorthy)
    val size: String get() = sizeLabel(width)
    val evil: String get() = if (crimson) "猩红" else "腐化"

    companion object {
        /** 4200/6400/8400 plus 17600, matching the game's own labels. */
        fun sizeLabel(width: Int): String = when {
            width <= 4200 -> "小世界"
            width <= 6400 -> "中世界"
            width <= 8400 -> "大世界"
            else -> "超大世界"
        }

        /**
         * For the Worthy shifts the difficulty up one notch, except Journey which never moves.
         * 传奇 is not a stored value — it is Master + FTW.
         */
        fun difficultyLabel(gameMode: Int, forTheWorthy: Boolean): String {
            if (gameMode == 3) return "旅行"
            val gm = gameMode.coerceIn(0, 2)
            return if (forTheWorthy) {
                listOf("普通", "专家", "大师", "传奇")[(gm + 1).coerceAtMost(3)]
            } else {
                listOf("普通", "专家", "大师")[gm]
            }
        }
    }
}

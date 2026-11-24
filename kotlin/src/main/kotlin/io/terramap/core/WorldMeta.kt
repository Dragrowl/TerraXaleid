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

    // The string properties above are the L1 default wording. UI layers that translate must read the
    // kinds instead, so a Chinese label never leaks into another language.
    val difficultyKind: Difficulty get() = difficultyKindOf(gameMode, forTheWorthy)
    val sizeKind: WorldSize get() = sizeKindOf(width)
    val evilKind: WorldEvil get() = if (crimson) WorldEvil.Crimson else WorldEvil.Corruption

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
        fun sizeKindOf(width: Int): WorldSize = when {
            width <= 4200 -> WorldSize.Small
            width <= 6400 -> WorldSize.Medium
            width <= 8400 -> WorldSize.Large
            else -> WorldSize.Huge
        }

        fun difficultyKindOf(gameMode: Int, forTheWorthy: Boolean): Difficulty {
            if (gameMode == 3) return Difficulty.Journey
            val gm = gameMode.coerceIn(0, 2)
            return if (forTheWorthy) {
                val ladder = listOf(
                    Difficulty.Classic,
                    Difficulty.Expert,
                    Difficulty.Master,
                    Difficulty.Legendary,
                )
                ladder[(gm + 1).coerceAtMost(3)]
            } else {
                listOf(Difficulty.Classic, Difficulty.Expert, Difficulty.Master)[gm]
            }
        }

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

/** Difficulty as a value, not as a word: the UI decides how to say it. */
enum class Difficulty { Classic, Expert, Master, Legendary, Journey }

/** World size as a value; [WorldMeta.sizeLabel] keeps the long-standing Chinese wording. */
enum class WorldSize { Small, Medium, Large, Huge }

/** World evil as a value. */
enum class WorldEvil { Corruption, Crimson }

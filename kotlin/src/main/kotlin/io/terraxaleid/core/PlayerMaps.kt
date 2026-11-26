// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.core

/**
 * Naming rules for the per-character map files.
 *
 * Terraria keeps exploration per (world, character), so one world has as many map files as the
 * characters that have visited it:
 *
 *     Players/<character>.plr           the character
 *     Players/<character>/<world>.map   what that character has explored in that world
 *
 * The `<world>` part is the world GUID printed the way .NET prints a Guid: the first three groups
 * of the stored bytes are byte-swapped, the last two are not. For the world whose stored bytes are
 * `e9 57 d5 c3 97 d3 79 42 a7 b4 6e 8d c3 2a df f1` the file is called
 * `c3d557e9-d397-4279-a7b4-6e8dc32adff1.map`.
 */
object PlayerMaps {
    const val MAP_EXTENSION: String = ".map"

    /** The `.map` file name a character's directory uses for this world. */
    fun mapFileNameForWorld(guid: ByteArray): String {
        require(guid.size == 16) { "a GUID is 16 bytes, got ${guid.size}" }
        val b = guid
        fun hex(i: Int): String = ((b[i].toInt() and 0xFF) + 0x100).toString(16).substring(1)
        return buildString {
            append(hex(3)); append(hex(2)); append(hex(1)); append(hex(0)); append('-')
            append(hex(5)); append(hex(4)); append('-')
            append(hex(7)); append(hex(6)); append('-')
            append(hex(8)); append(hex(9)); append('-')
            append(hex(10)); append(hex(11)); append(hex(12))
            append(hex(13)); append(hex(14)); append(hex(15))
            append(MAP_EXTENSION)
        }
    }

    /** The stored 16 GUID bytes behind a `.map` file name (extension optional). */
    fun guidFromMapFileName(fileName: String): ByteArray {
        val stem = if (fileName.endsWith(MAP_EXTENSION, ignoreCase = true)) {
            fileName.dropLast(MAP_EXTENSION.length)
        } else {
            fileName
        }
        val hex = stem.replace("-", "")
        require(hex.length == 32 && hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            "not a GUID file name: $fileName"
        }
        val b = ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return byteArrayOf(b[3], b[2], b[1], b[0], b[5], b[4], b[7], b[6]) + b.copyOfRange(8, 16)
    }

    /**
     * The character name behind a map directory. Terraria uses the directory name as the character
     * name, so the list needs no parsing of the `.plr`.
     */
    fun playerNameFromDirectory(directoryPath: String): String {
        val trimmed = directoryPath.trimEnd('/', '\\')
        val cut = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        return if (cut >= 0) trimmed.substring(cut + 1) else trimmed
    }
}

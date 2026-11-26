// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.core

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** One 4-byte `MapTile` per cell: `ushort Type`, `byte Light`, `byte Color`. */
class MapData(
    val header: MapHeader,
    val types: ShortArray,
    val lights: ByteArray,
    val colors: ByteArray,
) {
    init {
        require(types.size == lights.size && types.size == colors.size) {
            "type/light/color arrays must be the same length"
        }
    }

    val cellCount: Int get() = types.size

    /** The game treats light <= 18 as unexplored, which is why a black map is really a light issue. */
    fun exploredCount(): Int = lights.count { (it.toInt() and 0xFF) > 18 }

    fun typeAt(x: Int, y: Int): Int = types[x * header.height + y].toInt()
}

object MapFile {
    const val CHUNK_SIZE = 64
    const val CHUNK_CELLS = CHUNK_SIZE * CHUNK_SIZE      // 4096
    const val CHUNK_BYTES = CHUNK_CELLS * 4              // 16384

    fun read(data: ByteArray): MapData {
        val header = MapHeader.parse(data)
        val (columns, rows) = MapHeader.chunkGrid(header.width, header.height)

        val total = Math.multiplyExact(header.width, header.height)
        val types = ShortArray(total)
        val lights = ByteArray(total)
        val colors = ByteArray(total)

        var cursor = header.raw.size

        for (cy in 0 until rows) {
            for (cx in 0 until columns) {
                if (cursor + 4 > data.size) {
                    throw TerrariaFormatException("chunk $cx,$cy has no length prefix (offset $cursor)")
                }
                val length = (data[cursor].toInt() and 0xFF) or
                    ((data[cursor + 1].toInt() and 0xFF) shl 8) or
                    ((data[cursor + 2].toInt() and 0xFF) shl 16) or
                    ((data[cursor + 3].toInt() and 0xFF) shl 24)
                cursor += 4

                // A zero-length record is the game's way of saying "this chunk was never explored".
                if (length <= 0) continue
                if (cursor + length > data.size) {
                    throw TerrariaFormatException(
                        "chunk $cx,$cy declares $length bytes at offset $cursor, past the end of the file"
                    )
                }

                val block = inflate(data.copyOfRange(cursor, cursor + length))
                cursor += length
                if (block.size != CHUNK_BYTES) {
                    throw TerrariaFormatException(
                        "chunk $cx,$cy inflated to ${block.size} bytes, expected $CHUNK_BYTES"
                    )
                }

                for (n in 0 until CHUNK_CELLS) {
                    val x = cx * CHUNK_SIZE + (n % CHUNK_SIZE)
                    val y = cy * CHUNK_SIZE + (n / CHUNK_SIZE)
                    if (x >= header.width || y >= header.height) continue
                    val at = n * 4
                    val index = x * header.height + y
                    types[index] = ((block[at].toInt() and 0xFF) or ((block[at + 1].toInt() and 0xFF) shl 8)).toShort()
                    lights[index] = block[at + 2]
                    colors[index] = block[at + 3]
                }
            }
        }

        return MapData(header, types, lights, colors)
    }

    fun write(header: MapHeader, data: MapData, level: Int = 9): ByteArray {
        require(data.header.width == header.width && data.header.height == header.height) {
            "map payload size does not match the header"
        }

        val (columns, rows) = MapHeader.chunkGrid(header.width, header.height)
        val out = ByteArrayOutputStream()
        out.write(header.raw)

        val block = ByteArray(CHUNK_BYTES)

        for (cy in 0 until rows) {
            for (cx in 0 until columns) {
                block.fill(0)
                var any = false

                for (n in 0 until CHUNK_CELLS) {
                    val x = cx * CHUNK_SIZE + (n % CHUNK_SIZE)
                    val y = cy * CHUNK_SIZE + (n / CHUNK_SIZE)
                    if (x >= header.width || y >= header.height) continue
                    val index = x * header.height + y
                    val at = n * 4
                    val type = data.types[index].toInt()
                    block[at] = (type and 0xFF).toByte()
                    block[at + 1] = ((type shr 8) and 0xFF).toByte()
                    block[at + 2] = data.lights[index]
                    block[at + 3] = data.colors[index]
                    any = true
                }

                if (!any) {
                    out.write(byteArrayOf(0, 0, 0, 0))
                    continue
                }

                val compressed = deflate(block, level)
                out.write(byteArrayOf(
                    (compressed.size and 0xFF).toByte(),
                    ((compressed.size shr 8) and 0xFF).toByte(),
                    ((compressed.size shr 16) and 0xFF).toByte(),
                    ((compressed.size shr 24) and 0xFF).toByte(),
                ))
                out.write(compressed)
            }
        }

        return out.toByteArray()
    }

    fun inflate(source: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(source)
        val out = ByteArray(CHUNK_BYTES)
        var produced = 0
        while (produced < out.size && !inflater.finished()) {
            val n = inflater.inflate(out, produced, out.size - produced)
            if (n == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            }
            produced += n
        }
        val result = if (produced == out.size) out else out.copyOf(produced)
        inflater.end()
        return result
    }

    fun deflate(source: ByteArray, level: Int = 9): ByteArray {
        val deflater = Deflater(level, false)             // false = zlib wrapper, same as the game
        deflater.setInput(source)
        deflater.finish()
        val out = ByteArrayOutputStream(source.size)
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            if (n > 0) out.write(buffer, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

package io.terraxaleid.core

/** Thrown when a .wld/.map stream does not match the documented layout. */
class TerrariaFormatException(message: String) : RuntimeException(message)

/**
 * Little-endian cursor over a byte array.
 *
 * L1 never touches the filesystem: callers hand in bytes, and every parse either
 * succeeds completely or throws — no silent truncation, no "best effort" decoding.
 */
class SpanReader(private val data: ByteArray, private var offset: Int = 0) {

    val position: Int get() = offset
    val length: Int get() = data.size
    val remaining: Int get() = data.size - offset

    private fun need(count: Int) {
        if (count < 0 || remaining < count) {
            throw TerrariaFormatException(
                "unexpected end of data: wanted $count byte(s) at offset $offset, only $remaining left"
            )
        }
    }

    fun u8(): Int {
        need(1)
        return data[offset++].toInt() and 0xFF
    }

    fun i16(): Int {
        need(2)
        val v = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
        offset += 2
        return v.toShort().toInt()
    }

    fun u16(): Int {
        need(2)
        val v = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
        offset += 2
        return v
    }

    fun i32(): Int {
        need(4)
        val v = (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
        offset += 4
        return v
    }

    fun u32(): Long = i32().toLong() and 0xFFFFFFFFL

    fun i64(): Long {
        need(8)
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (data[offset + i].toLong() and 0xFFL)
        offset += 8
        return v
    }

    fun f64(): Double = java.lang.Double.longBitsToDouble(i64())

    fun skip(count: Int) {
        need(count)
        offset += count
    }

    fun take(count: Int): ByteArray {
        need(count)
        val out = data.copyOfRange(offset, offset + count)
        offset += count
        return out
    }

    /** 7-bit varint length prefix followed by UTF-8 payload. */
    fun string7(): String {
        var length = 0
        var shift = 0
        while (true) {
            val b = u8()
            length = length or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift > 28) throw TerrariaFormatException("7-bit string length overflow at offset $offset")
        }
        if (length < 0 || length > remaining) {
            throw TerrariaFormatException("7-bit string length $length exceeds $remaining remaining byte(s)")
        }
        val bytes = take(length)
        return String(bytes, Charsets.UTF_8)
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Buffers.Binary;
using System.Text;

namespace TerraXaleid.Core;

/// <summary>
/// Little-endian cursor over a byte span, mirroring the Python reader one call at a time.
/// Every read is bounds-checked and turns into <see cref="TerrariaFormatException"/>.
/// </summary>
public ref struct SpanReader
{
    private readonly ReadOnlySpan<byte> _data;

    public SpanReader(ReadOnlySpan<byte> data)
    {
        _data = data;
        Position = 0;
    }

    public int Position { get; private set; }

    public readonly int Length => _data.Length;

    public readonly int Remaining => _data.Length - Position;

    private readonly void Need(int count)
    {
        if (count < 0) throw new TerrariaFormatException($"negative read length {count}");
        if (Position + count > _data.Length)
        {
            throw new TerrariaFormatException(
                $"unexpected end of data: wanted {count} byte(s) at offset {Position}, " +
                $"only {_data.Length - Position} left");
        }
    }

    public byte Byte()
    {
        Need(1);
        return _data[Position++];
    }

    public short Int16()
    {
        Need(2);
        short v = BinaryPrimitives.ReadInt16LittleEndian(_data.Slice(Position));
        Position += 2;
        return v;
    }

    public ushort UInt16()
    {
        Need(2);
        ushort v = BinaryPrimitives.ReadUInt16LittleEndian(_data.Slice(Position));
        Position += 2;
        return v;
    }

    public int Int32()
    {
        Need(4);
        int v = BinaryPrimitives.ReadInt32LittleEndian(_data.Slice(Position));
        Position += 4;
        return v;
    }

    public uint UInt32()
    {
        Need(4);
        uint v = BinaryPrimitives.ReadUInt32LittleEndian(_data.Slice(Position));
        Position += 4;
        return v;
    }

    public long Int64()
    {
        Need(8);
        long v = BinaryPrimitives.ReadInt64LittleEndian(_data.Slice(Position));
        Position += 8;
        return v;
    }

    public double Double()
    {
        Need(8);
        double v = BinaryPrimitives.ReadDoubleLittleEndian(_data.Slice(Position));
        Position += 8;
        return v;
    }

    public float Single()
    {
        Need(4);
        float v = BinaryPrimitives.ReadSingleLittleEndian(_data.Slice(Position));
        Position += 4;
        return v;
    }

    public void Skip(int count)
    {
        Need(count);
        Position += count;
    }

    public ReadOnlySpan<byte> Take(int count)
    {
        Need(count);
        ReadOnlySpan<byte> slice = _data.Slice(Position, count);
        Position += count;
        return slice;
    }

    /// <summary>Terraria's 7-bit-encoded length prefix followed by UTF-8 bytes.</summary>
    public string String7()
    {
        int length = 0;
        int shift = 0;
        while (true)
        {
            byte b = Byte();
            length |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
            if (shift > 28) throw new TerrariaFormatException("7-bit string length prefix is too long");
        }

        return Encoding.UTF8.GetString(Take(length));
    }
}

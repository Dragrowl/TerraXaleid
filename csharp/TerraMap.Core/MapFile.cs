// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Buffers.Binary;
using System.Collections.Generic;
using System.IO;

namespace TerraMap.Core;

/// <summary>Decoded map payload: one 4-byte <c>MapTile</c> per cell, split into three arrays.</summary>
public sealed class MapData
{
    public MapData(MapHeader header, ushort[] types, byte[] lights, byte[] colors)
    {
        if (types.Length != lights.Length || types.Length != colors.Length)
            throw new ArgumentException("type/light/color arrays must be the same length");

        Header = header;
        Types = types;
        Lights = lights;
        Colors = colors;
    }

    public MapHeader Header { get; }

    /// <summary>Palette index per cell, column-major: <c>x * Height + y</c>.</summary>
    public ushort[] Types { get; }

    public byte[] Lights { get; }

    public byte[] Colors { get; }

    public int CellCount => Types.Length;

    public int ExploredCount()
    {
        int n = 0;
        foreach (byte light in Lights)
        {
            if (light > 18) n++;
        }

        return n;
    }
}

/// <summary>
/// <c>.map</c> container: header, then one record per 64x64 chunk in row-major chunk order.
/// Each record is <c>int32 length</c> followed by <c>length</c> bytes of zlib data that inflates
/// to 16384 bytes (4096 entries x {ushort Type, byte Light, byte Color}). A non-positive length
/// means "this whole chunk is untouched" and the loader fills it with empty tiles.
///
/// Inside a chunk the order is column-major: <c>n -> x = chunkX*64 + n%64</c>,
/// <c>y = chunkY*64 + n/64</c>.
/// </summary>
public static class MapFile
{
    public const int ChunkSize = 64;
    public const int ChunkCells = ChunkSize * ChunkSize;
    public const int ChunkBytes = ChunkCells * 4;

    public static MapData Read(ReadOnlySpan<byte> data)
    {
        MapHeader header = MapHeader.Parse(data, out int pos);
        int width = header.Width;
        int height = header.Height;
        int total = checked(width * height);

        var types = new ushort[total];
        var lights = new byte[total];
        var colors = new byte[total];

        (int columns, int rows) = MapHeader.ChunkGrid(width, height);
        int chunkCount = columns * rows;

        for (int chunk = 0; chunk < chunkCount; chunk++)
        {
            if (pos + 4 > data.Length)
                throw new TerrariaFormatException($"map truncated before chunk {chunk} (offset {pos})");

            int length = BinaryPrimitives.ReadInt32LittleEndian(data.Slice(pos));
            pos += 4;

            if (length <= 0) continue;                  // untouched chunk: all Type 0 / Light 0

            if (pos + length > data.Length)
                throw new TerrariaFormatException(
                    $"chunk {chunk} declares {length} bytes at offset {pos}, past the end of the file");

            byte[] raw = Zlib.Decompress(data.Slice(pos, length));
            pos += length;

            if (raw.Length != ChunkBytes)
                throw new TerrariaFormatException(
                    $"chunk {chunk} inflated to {raw.Length} bytes, expected {ChunkBytes}");

            int chunkX = chunk % columns;
            int chunkY = chunk / columns;

            for (int n = 0; n < ChunkCells; n++)
            {
                int x = (chunkX * ChunkSize) + (n % ChunkSize);
                int y = (chunkY * ChunkSize) + (n / ChunkSize);
                if (x >= width || y >= height) continue;

                int src = n * 4;
                int dst = (x * height) + y;
                types[dst] = (ushort)(raw[src] | (raw[src + 1] << 8));
                lights[dst] = raw[src + 2];
                colors[dst] = raw[src + 3];
            }
        }

        return new MapData(header, types, lights, colors);
    }

    /// <summary>
    /// Serialises the arrays back into a <c>.map</c> byte image. The header is emitted verbatim
    /// (<see cref="MapHeader.Raw"/>) so the option tables that the palette indices depend on stay
    /// byte-identical to the template.
    /// </summary>
    public static byte[] Write(MapHeader header, MapData data, int level = 9)
    {
        if (data.Header.Width != header.Width || data.Header.Height != header.Height)
            throw new ArgumentException("data does not belong to this header", nameof(data));

        int width = header.Width;
        int height = header.Height;
        (int columns, int rows) = MapHeader.ChunkGrid(width, height);

        var block = new byte[ChunkBytes];
        using var output = new MemoryStream(header.Raw.Length + (columns * rows * 64));

        if (header.Raw.Length > 0) output.Write(header.Raw);

        for (int chunk = 0; chunk < columns * rows; chunk++)
        {
            int chunkX = chunk % columns;
            int chunkY = chunk / columns;

            Array.Clear(block);
            bool any = false;

            for (int n = 0; n < ChunkCells; n++)
            {
                int x = (chunkX * ChunkSize) + (n % ChunkSize);
                int y = (chunkY * ChunkSize) + (n / ChunkSize);
                if (x >= width || y >= height) continue;

                int src = (x * height) + y;
                ushort type = data.Types[src];
                int dst = n * 4;
                block[dst] = (byte)(type & 0xFF);
                block[dst + 1] = (byte)(type >> 8);
                block[dst + 2] = data.Lights[src];
                block[dst + 3] = data.Colors[src];
                any = true;
            }

            if (!any)
            {
                // Fully outside the world (can only happen on a size mismatch) — keep the record
                // shape valid instead of writing a zero-length marker.
                output.Write(new byte[4]);
                continue;
            }

            byte[] compressed = Zlib.Compress(block, level);
            byte[] lengthPrefix = new byte[4];
            BinaryPrimitives.WriteInt32LittleEndian(lengthPrefix, compressed.Length);
            output.Write(lengthPrefix);
            output.Write(compressed);
        }

        return output.ToArray();
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Buffers.Binary;

namespace TerraMap.Core;

/// <summary>
/// Header of a <c>.map</c> file plus the palette maths that turns raw tile/wall ids into the
/// 1-based indices the map renderer uses.
///
/// The palette is cumulative and therefore depends on this file's own option tables:
/// <code>
/// tile   = 1
/// wall   = 1 + sum(tile counts)
/// liquid = wall + sum(wall counts)          (+4 entries)
/// sky    = liquid + 4                       (+256 gradients)
/// dirt   = sky + 256                        (+256, 255 = snow)
/// rock   = dirt + 256                       (+256, 255 = snow)
/// hell   = rock + 256                       (single entry)
/// </code>
/// Getting the 1-based offset wrong shifts every colour by one slot, which looks like
/// "the palette is inverted" rather than like an off-by-one.
/// </summary>
public sealed class MapHeader
{
    public const int FileVersion = 33094;
    public const int FileType = 1;

    private readonly int[] _tilePrefix;
    private readonly int[] _wallPrefix;
    private readonly int _tileTotal;
    private readonly int _wallTotal;

    public MapHeader(
        byte[] raw,
        string worldName,
        int worldId,
        int width,
        int height,
        int maxTileId,
        int maxWallId,
        int[] tileCounts,
        int[] wallCounts,
        uint revision = 0)
    {
        Raw = raw ?? Array.Empty<byte>();
        WorldName = worldName;
        WorldId = worldId;
        Width = width;
        Height = height;
        MaxTileId = maxTileId;
        MaxWallId = maxWallId;
        TileCounts = tileCounts;
        WallCounts = wallCounts;
        Revision = revision;

        _tilePrefix = new int[maxTileId + 1];
        for (int i = 0; i < tileCounts.Length && i + 1 < _tilePrefix.Length; i++)
        {
            _tilePrefix[i + 1] = _tilePrefix[i] + tileCounts[i];
            _tileTotal += tileCounts[i];
        }

        _wallPrefix = new int[maxWallId + 1];
        for (int i = 0; i < wallCounts.Length && i + 1 < _wallPrefix.Length; i++)
        {
            _wallPrefix[i + 1] = _wallPrefix[i] + wallCounts[i];
            _wallTotal += wallCounts[i];
        }
    }

    public byte[] Raw { get; }

    public string WorldName { get; }

    public int WorldId { get; }

    public int Width { get; }

    public int Height { get; }

    public int MaxTileId { get; }

    public int MaxWallId { get; }

    public int[] TileCounts { get; }

    public int[] WallCounts { get; }

    public uint Revision { get; }

    public int TileBase => 1;

    public int WallBase => TileBase + _tileTotal;

    public int LiquidBase => WallBase + _wallTotal;

    public int SkyBase => LiquidBase + 4;

    public int DirtBase => SkyBase + 256;

    public int RockBase => DirtBase + 256;

    public int HellBase => RockBase + 256;

    public int TileType(int tileId) =>
        tileId >= 0 && tileId < _tilePrefix.Length ? TileBase + _tilePrefix[tileId] : TileBase;

    public int WallType(int wallId) =>
        wallId >= 0 && wallId < _wallPrefix.Length ? WallBase + _wallPrefix[wallId] : WallBase;

    /// <summary>Sky gradient index: <c>byte(255 * y / surface)</c>, matching CalcSkyGradient.</summary>
    public int SkyType(int y, double surface)
    {
        if (surface <= 0) return SkyBase;
        int gradient = (int)(255.0 * y / surface) & 0xFF;
        return SkyBase + gradient;
    }

    public static (int Columns, int Rows) ChunkGrid(int width, int height) =>
        ((width + 63) / 64, (height + 63) / 64);

    public static MapHeader Parse(ReadOnlySpan<byte> data, out int consumed)
    {
        var r = new SpanReader(data);

        int version = r.Int32();
        if (version != FileVersion)
            throw new TerrariaFormatException($"unsupported map file version {version} (expected {FileVersion})");

        if (!r.Take(7).SequenceEqual("relogic"u8))
            throw new TerrariaFormatException("not a Terraria map file (magic mismatch)");

        byte fileType = r.Byte();
        if (fileType != FileType)
            throw new TerrariaFormatException($"unexpected map fileType {fileType}");

        uint revision = r.UInt32();
        r.Skip(8);                                    // favorite (uint64)
        string worldName = r.String7();
        int worldId = r.Int32();
        int height = r.Int32();
        int width = r.Int32();
        short maxTileId = r.Int16();
        short maxWallId = r.Int16();
        r.Skip(8);                                    // liquid / sky / dirt / rock gradient counts

        bool[] tileFlags = ReadBits(ref r, maxTileId);
        bool[] wallFlags = ReadBits(ref r, maxWallId);

        var tileCounts = new int[maxTileId];
        for (int i = 0; i < tileCounts.Length; i++)
        {
            tileCounts[i] = tileFlags[i] ? r.Byte() : 1;
        }

        var wallCounts = new int[maxWallId];
        for (int i = 0; i < wallCounts.Length; i++)
        {
            wallCounts[i] = wallFlags[i] ? r.Byte() : 1;
        }

        consumed = r.Position;
        byte[] raw = data.Slice(0, consumed).ToArray();

        return new MapHeader(raw, worldName, worldId, width, height, maxTileId, maxWallId,
            tileCounts, wallCounts, revision);
    }

    /// <summary>
    /// Option bitmaps are LSB-first within each byte (bit 0 of the first byte is entry 0),
    /// with a fresh byte fetched whenever the mask wraps. Verified against real files.
    /// </summary>
    private static bool[] ReadBits(ref SpanReader r, int count)
    {
        var bits = new bool[count];
        byte cur = 0;
        int mask = 128;
        for (int i = 0; i < count; i++)
        {
            if (mask == 128)
            {
                cur = r.Byte();
                mask = 1;
            }
            else
            {
                mask <<= 1;
            }

            bits[i] = (cur & mask) == mask;
        }

        return bits;
    }
}

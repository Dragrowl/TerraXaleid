// SPDX-License-Identifier: GPL-3.0-or-later
using System;
using System.Collections.Generic;

namespace TerraXaleid.Core;

/// <summary>
/// Per-tile / per-wall "option" counts, i.e. how many palette variants the game keeps for each id,
/// plus the absolute palette base each id starts at.
/// </summary>
/// <remarks>
/// A map file stores, for tiles and walls, the <b>absolute palette index minus one</b>
/// (<c>Type - tilePosition</c>); liquids, sky and the dirt/rock gradients store a raw option index
/// instead. The loader rebuilds the absolute position from the counts written in the file, so the
/// file's per-id counts have to match the game's own table, otherwise every tile shifts by the
/// difference.
///
/// That table is a property of the game version, not of a world, and the game writes a count byte
/// for every id whose count is not 1 — so any map file the game itself saved carries the complete
/// table in its header.
/// </remarks>
public sealed class TileOptions
{
    private readonly int[] _tileCounts;
    private readonly int[] _wallCounts;
    private readonly int[] _tileBase;
    private readonly int[] _wallBase;

    public TileOptions(int[] tileCounts, int[] wallCounts)
    {
        _tileCounts = tileCounts;
        _wallCounts = wallCounts;
        _tileBase = new int[tileCounts.Length];
        var running = 1;
        for (var i = 0; i < tileCounts.Length; i++)
        {
            _tileBase[i] = running;
            running += tileCounts[i];
        }

        TileTotal = running - 1;
        _wallBase = new int[wallCounts.Length];
        var wallRunning = 1 + TileTotal;
        for (var i = 0; i < wallCounts.Length; i++)
        {
            _wallBase[i] = wallRunning;
            wallRunning += wallCounts[i];
        }

        WallTotal = wallRunning - 1 - TileTotal;
    }

    public int[] TileCounts => _tileCounts;

    public int[] WallCounts => _wallCounts;

    public int TileTotal { get; }

    public int WallTotal { get; }

    /// <summary>Absolute palette index of tile <paramref name="id"/>'s option 0.</summary>
    public int TileBase(int id) => id >= 0 && id < _tileBase.Length ? _tileBase[id] : 1;

    /// <summary>Absolute palette index of wall <paramref name="id"/>'s option 0.</summary>
    public int WallBase(int id) => id >= 0 && id < _wallBase.Length ? _wallBase[id] : 1 + TileTotal;

    /// <summary>
    /// Value to store for a wall cell. The loader adds the wall compact base
    /// (<c>1 + TileTotal</c>) to it, so what goes into the file is the offset inside the
    /// wall段, not the absolute palette index.
    /// </summary>
    public int WallStored(int id) => WallBase(id) - 1 - TileTotal;

    /// <summary>Value to store for a tile cell: the loader adds 1, so store absolute − 1.</summary>
    public int TileStored(int id) => TileBase(id) - 1;

    /// <summary>Reads both tables from any map file the game itself wrote.</summary>
    public static TileOptions FromMapHeader(ReadOnlySpan<byte> data)
    {
        var header = MapHeader.Parse(data, out _);
        return new TileOptions(header.TileCounts, header.WallCounts);
    }

    /// <summary>Reads the tables from a file on disk.</summary>
    public static TileOptions FromFile(string path) => FromMapHeader(System.IO.File.ReadAllBytes(path));

    /// <summary>First readable map among <paramref name="paths"/>; records which one was used.</summary>
    public static TileOptions? FromFirstReadable(IEnumerable<string> paths, out string? source)
    {
        source = null;
        foreach (var path in paths)
        {
            try
            {
                var options = FromFile(path);
                source = path;
                return options;
            }
            catch (Exception)
            {
                // not a map this build understands; try the next one
            }
        }

        return null;
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later

using System;

namespace TerraMap.Core;

/// <summary>
/// The tile/wall grid of a world. Arrays are stored column-major (<c>index = x * Height + y</c>)
/// so that walking one column is sequential, which is also the order the .wld tile stream uses.
/// <c>-1</c> means "no tile" / "no wall".
///
/// Liquid rides along with the blocks because the game paints it as its own map colour:
/// <see cref="LiquidAmounts"/> is the 0..255 fill level (only cells above
/// <see cref="LiquidPaintThreshold"/> are drawn) and <see cref="LiquidKinds"/> is the palette offset
/// — 0 water, 1 lava, 2 honey, 3 shimmer.
/// </summary>
public sealed class World
{
    public World(
        string name,
        int worldId,
        int width,
        int height,
        double surface,
        double rockLayer,
        short[] tiles,
        short[] walls,
        byte[]? liquidAmounts = null,
        byte[]? liquidKinds = null)
    {
        if (width <= 0 || height <= 0)
            throw new ArgumentOutOfRangeException(nameof(width), $"invalid world size {width}x{height}");

        int expected = checked(width * height);
        if (tiles.Length != expected)
            throw new ArgumentException($"tiles has {tiles.Length} entries, expected {expected}", nameof(tiles));
        if (walls.Length != expected)
            throw new ArgumentException($"walls has {walls.Length} entries, expected {expected}", nameof(walls));

        LiquidAmounts = liquidAmounts ?? new byte[expected];
        LiquidKinds = liquidKinds ?? new byte[expected];
        if (LiquidAmounts.Length != expected)
            throw new ArgumentException(
                $"liquids has {LiquidAmounts.Length} entries, expected {expected}", nameof(liquidAmounts));
        if (LiquidKinds.Length != expected)
            throw new ArgumentException(
                $"liquid kinds has {LiquidKinds.Length} entries, expected {expected}", nameof(liquidKinds));

        Name = name;
        WorldId = worldId;
        Width = width;
        Height = height;
        Surface = surface;
        RockLayer = rockLayer;
        Tiles = tiles;
        Walls = walls;
    }

    /// <summary><c>MapHelper.GetWallType</c> only shows liquid above this fill level.</summary>
    public const int LiquidPaintThreshold = 32;

    public string Name { get; }

    public int WorldId { get; }

    public int Width { get; }

    public int Height { get; }

    public double Surface { get; }

    public double RockLayer { get; }

    public short[] Tiles { get; }

    public short[] Walls { get; }

    public byte[] LiquidAmounts { get; }

    public byte[] LiquidKinds { get; }

    /// <summary>Depth where the underworld (hell) background starts: <c>height - 200</c>.</summary>
    public int UnderworldLayer => Height - 200;

    public int Index(int x, int y) => x * Height + y;

    public short TileAt(int x, int y) => Tiles[Index(x, y)];

    public short WallAt(int x, int y) => Walls[Index(x, y)];

    public int LiquidAmountAt(int x, int y) => LiquidAmounts[Index(x, y)];

    public int LiquidKindAt(int x, int y) => LiquidKinds[Index(x, y)];

    public int SolidCount()
    {
        int n = 0;
        foreach (short t in Tiles)
        {
            if (t >= 0) n++;
        }

        return n;
    }

    public int WallOnlyCount()
    {
        int n = 0;
        for (int i = 0; i < Tiles.Length; i++)
        {
            if (Tiles[i] < 0 && Walls[i] >= 0) n++;
        }

        return n;
    }

    public int LiquidCount()
    {
        int n = 0;
        foreach (byte amount in LiquidAmounts)
        {
            if (amount > LiquidPaintThreshold) n++;
        }

        return n;
    }
}

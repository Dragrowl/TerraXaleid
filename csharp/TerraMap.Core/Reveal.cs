// SPDX-License-Identifier: GPL-3.0-or-later

using System;

namespace TerraMap.Core;

/// <summary>
/// Options for the reveal pass. The default is the only behaviour the product needs:
/// every cell of the world is written from the world itself with full light, ignoring whatever
/// the template's old map said. <see cref="OverwriteExplored"/> exists so callers can be explicit,
/// but the reveal API never receives the old payload — with <c>false</c> it throws, which keeps
/// "accidentally kept stale tiles" out of the realm of possibility.
/// </summary>
public sealed record RevealOptions
{
    public int Light { get; init; } = 255;

    public bool OverwriteExplored { get; init; } = true;

    public int CompressionLevel { get; init; } = 9;
}

/// <summary>
/// Turns a world grid into a fully revealed map payload.
///
/// Per cell the Type is chosen exactly like HERO's Mod / MapHelper does it:
/// tile (if any) else wall (if any) else the depth-based background gradient. Light is written
/// as <see cref="RevealOptions.Light"/> for every cell, which is what makes the black areas
/// disappear rather than merely becoming "explored but dark".
/// </summary>
public static class Reveal
{
    public static MapData Build(MapHeader header, World world, RevealOptions? options = null)
    {
        RevealOptions opt = options ?? new RevealOptions();
        if (!opt.OverwriteExplored)
        {
            throw new NotSupportedException(
                "partial reveal needs the previous payload; the reveal path is full-overwrite by design");
        }

        if (world.Width != header.Width || world.Height != header.Height)
        {
            throw new ArgumentException(
                $"world is {world.Width}x{world.Height} but map header says {header.Width}x{header.Height}",
                nameof(world));
        }

        int width = world.Width;
        int height = world.Height;
        int total = width * height;

        // Background gradient per row — 256 sky steps above ground, then flat bands.
        var background = new ushort[height];
        for (int y = 0; y < height; y++)
        {
            if (y < world.Surface) background[y] = (ushort)header.SkyType(y, world.Surface);
            else if (y < world.RockLayer) background[y] = (ushort)header.DirtBase;
            else if (y < world.UnderworldLayer) background[y] = (ushort)header.RockBase;
            else background[y] = (ushort)header.HellBase;
        }

        var tileLut = new ushort[Math.Max(1, header.MaxTileId)];
        for (int id = 0; id < tileLut.Length; id++) tileLut[id] = (ushort)header.TileType(id);

        var wallLut = new ushort[Math.Max(1, header.MaxWallId)];
        for (int id = 0; id < wallLut.Length; id++) wallLut[id] = (ushort)header.WallType(id);

        var types = new ushort[total];
        var lights = new byte[total];
        var colors = new byte[total];

        byte light = (byte)opt.Light;

        for (int x = 0; x < width; x++)
        {
            int columnBase = x * height;
            for (int y = 0; y < height; y++)
            {
                int index = columnBase + y;
                short tile = world.Tiles[index];

                ushort type;
                if (tile >= 0)
                {
                    type = tile < tileLut.Length ? tileLut[tile] : (ushort)header.TileBase;
                }
                else
                {
                    short wall = world.Walls[index];
                    type = wall >= 0
                        ? (wall < wallLut.Length ? wallLut[wall] : (ushort)header.WallBase)
                        : background[y];
                }

                types[index] = type;
                lights[index] = light;
                colors[index] = 0;
            }
        }

        return new MapData(header, types, lights, colors);
    }

    /// <summary>Build + serialise in one call: the whole pipeline, no file access.</summary>
    public static byte[] Write(MapHeader header, World world, RevealOptions? options = null)
    {
        RevealOptions opt = options ?? new RevealOptions();
        MapData data = Build(header, world, opt);
        return MapFile.Write(header, data, opt.CompressionLevel);
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
using System;
using System.IO;
using System.IO.Compression;

namespace TerraXaleid.Core;

/// <summary>
/// Desktop (PC) map format, file version 326 — the layout 1.4.5 writes through
/// <c>MapHelper.SaveMap</c>: a plain header, then one raw deflate stream holding
/// row-major cells encoded by <c>MapHelper.SaveMap</c>'s per-cell writer.
///
/// A tile or wall cell stores an <b>absolute palette index minus one</b>
/// (<c>Type - tilePosition</c>), not a tile id: the loader adds the compact base of the
/// layer and then maps through <c>array5</c>, which is the identity when the file's
/// per-id option counts match the game's own table. So the writer must emit that table
/// (see <see cref="TileOptions"/>) and store <c>Base(id) + option - 1</c>.
///
/// Liquids, sky and the dirt/rock gradients store a raw option index instead — the
/// loader adds the matching layer base itself.
/// </summary>
public static class PcMapFormat
{
    public const int FileVersion = 326;
    public const byte FileType = 1;

    public const int LiquidCount = 4;
    public const int SkyGradients = 256;
    public const int DirtGradients = 256;
    public const int RockGradients = 256;

    /// <summary>
    /// Encodes a fully explored map: every cell gets light 255 and the tile/wall/background
    /// that belongs there. <paramref name="options"/> supplies the per-id counts, which must
    /// match the game's own table or every tile shifts.
    /// </summary>
    public static void Write(
        Stream output,
        TileOptions options,
        string worldName,
        int worldId,
        int width,
        int height,
        int maxTileId,
        int maxWallId,
        short[] tiles,
        short[] walls,
        byte[] liquidAmounts,
        byte[] liquidKinds,
        float worldSurface,
        float rockLayer,
        int underworldLayer)
    {
        using var writer = new BinaryWriter(output, System.Text.Encoding.UTF8, leaveOpen: true);
        WriteHeader(writer, options, worldName, worldId, width, height, maxTileId, maxWallId);
        writer.Flush();

        using var deflate = new DeflateStream(output, CompressionLevel.SmallestSize, leaveOpen: true);
        var cells = new CellWriter(deflate);
        cells.Reset();

        for (var y = 0; y < height; y++)
        {
            var background = BackgroundFor(y, worldSurface, rockLayer, underworldLayer);
            for (var x = 0; x < width; x++)
            {
                var index = (x * height) + y;
                var tile = tiles[index];
                var wall = walls[index];
                var liquid = liquidAmounts[index];

                // Priority copied from World.CreateMapTile: a block wins, then a full enough
                // liquid (the game itself only paints liquid above 32), then the wall, then the
                // depth background. Skipping the liquid layer is what left oceans cyan: with no
                // liquid cell the map falls back to the sky background.
                if (tile >= 0)
                {
                    cells.Push(CellLayer.Tile, options.TileStored(tile), 255, 0);
                }
                else if (liquid > World.LiquidPaintThreshold)
                {
                    var kind = liquidKinds[index];
                    // Layer 3/4/5 are water/lava/honey. Shimmer needs the b4 0x40 bit, which this
                    // encoder does not emit yet, so it falls back to the water colour.
                    var layer = kind switch
                    {
                        1 => CellLayer.Lava,
                        2 => CellLayer.Honey,
                        _ => CellLayer.Water,
                    };
                    cells.Push(layer, 0, 255, 0);
                }
                else if (wall >= 0)
                {
                    cells.Push(CellLayer.Wall, options.WallStored(wall), 255, 0);
                }
                else
                {
                    // Air with no wall: paint the background the game itself would paint, so the
                    // cell reads as explored sky/dirt instead of "explored but empty" (that left a
                    // 1px seam against the still-unexplored neighbours).
                    if (background.Layer == CellLayer.Sky)
                    {
                        var gradient = (int)(255.0 * y / worldSurface);
                        background = new CellKind(CellLayer.Sky, Math.Clamp(gradient, 0, 255));
                    }

                    cells.Push(background.Layer, background.Option, 255, 0);
                }
            }

            // A run never spans two rows: the loader walks one row at a time.
            cells.Flush();
        }

        cells.Flush();
    }

    /// <summary>
    /// Background for a cell with no tile and no wall. Above the surface the sky gradient is
    /// picked by height (that is what <c>CalcSkyGradient</c> does with the stored option, so a
    /// flat 0 puts a hard band at every row); below it the dirt/rock gradient uses option 0.
    /// </summary>
    private static CellKind BackgroundFor(int y, float worldSurface, float rockLayer, int underworldLayer)
    {
        if (worldSurface > 1f && y < worldSurface)
        {
            var gradient = (int)(255.0 * y / worldSurface);
            return new CellKind(CellLayer.Sky, Math.Clamp(gradient, 0, 255));
        }

        // Depth below the surface: the dirt/rock gradient. Option 0 is the plain dirt fill the
        // game itself uses for air cells it has drawn but which have no wall.
        return new CellKind(CellLayer.DirtOrRock, 0);
    }

    private static void WriteHeader(
        BinaryWriter writer,
        TileOptions options,
        string worldName,
        int worldId,
        int width,
        int height,
        int maxTileId,
        int maxWallId)
    {
        writer.Write(FileVersion);
        writer.Write("relogic"u8);
        writer.Write(FileType);
        writer.Write(1u);
        writer.Write(0UL);
        WriteString7(writer, worldName);
        writer.Write(worldId);
        writer.Write(height);
        writer.Write(width);
        writer.Write((short)maxTileId);
        writer.Write((short)maxWallId);
        writer.Write((short)LiquidCount);
        writer.Write((short)SkyGradients);
        writer.Write((short)DirtGradients);
        writer.Write((short)RockGradients);

        WriteOptionBitmaps(writer, options.TileCounts, maxTileId, options.WallCounts, maxWallId);
        WriteOptionCounts(writer, options.TileCounts, maxTileId);
        WriteOptionCounts(writer, options.WallCounts, maxWallId);
    }

    /// <summary>
    /// Two bitmaps ("this id has more than one option") are written back to back, each byte
    /// filled LSB-first; <c>SaveMap</c> flushes a byte every eighth id. Any trailing partial
    /// byte is still written.
    /// </summary>
    private static void WriteOptionBitmaps(BinaryWriter writer, int[] tileCounts, int maxTileId, int[] wallCounts, int maxWallId)
    {
        WriteBitmap(writer, tileCounts, maxTileId);
        WriteBitmap(writer, wallCounts, maxWallId);
    }

    private static void WriteBitmap(BinaryWriter writer, int[] counts, int maxId)
    {
        var bits = new byte[(maxId + 7) / 8];
        for (var id = 0; id < maxId; id++)
        {
            var count = id < counts.Length ? counts[id] : 1;
            if (count != 1)
            {
                bits[id >> 3] |= (byte)(1 << (id & 7));
            }
        }

        writer.Write(bits);
    }

    /// <summary>
    /// Reads back a map produced by <see cref="Write"/> and checks that it holds exactly
    /// <paramref name="width"/> × <paramref name="height"/> cells with no bytes left over.
    /// Used by the CLI's self-test and by the Android/Windows front ends before they report
    /// success.
    /// </summary>
    public static bool TryVerify(byte[] image, int width, int height, out long cells, out string? error)
    {
        cells = 0;
        error = null;

        try
        {
            using var stream = new MemoryStream(image, writable: false);
            using var reader = new BinaryReader(stream, System.Text.Encoding.UTF8, leaveOpen: true);

            int version = reader.ReadInt32();
            if (version != FileVersion)
            {
                error = $"header version {version}, expected {FileVersion}";
                return false;
            }

            reader.ReadBytes(7);
            reader.ReadByte();
            reader.ReadInt32();
            reader.ReadInt64();

            int length = 0;
            var shift = 0;
            byte b;
            do
            {
                b = reader.ReadByte();
                length |= (b & 0x7F) << shift;
                shift += 7;
            }
            while ((b & 0x80) != 0);
            reader.ReadBytes(length);

            int worldId = reader.ReadInt32();
            int fileHeight = reader.ReadInt32();
            int fileWidth = reader.ReadInt32();
            if (fileWidth != width || fileHeight != height)
            {
                error = $"header says {fileWidth} x {fileHeight}, expected {width} x {height} (world {worldId})";
                return false;
            }

            int maxTileId = reader.ReadInt16();
            int maxWallId = reader.ReadInt16();
            reader.ReadInt16();
            reader.ReadInt16();
            reader.ReadInt16();
            reader.ReadInt16();

            var tileBits = reader.ReadBytes((maxTileId + 7) / 8);
            var wallBits = reader.ReadBytes((maxWallId + 7) / 8);
            reader.ReadBytes(CountSetBits(tileBits, maxTileId));
            reader.ReadBytes(CountSetBits(wallBits, maxWallId));

            stream.Position = image.Length - (image.Length - stream.Position);
            var bodyStart = (int)stream.Position;
            using var inflate = new DeflateStream(new MemoryStream(image, bodyStart, image.Length - bodyStart), CompressionMode.Decompress);
            var body = new MemoryStream();
            inflate.CopyTo(body);
            var data = body.ToArray();

            var pos = 0;
            long count = 0;
            while (pos < data.Length)
            {
                byte b3 = data[pos++];
                var b4 = (byte)0;
                if ((b3 & 1) != 0) b4 = data[pos++];
                if ((b4 & 1) != 0) pos++;
                var layer = (b3 >> 1) & 7;
                if (layer is 1 or 2 or 7)
                {
                    if ((b3 & 0x10) != 0) pos += 2;
                    else pos++;
                }

                var hasLight = (b3 & 0x20) != 0;
                if (hasLight) pos++;

                var runBits = (b3 >> 6) & 3;
                var run = 0;
                if (runBits == 1) run = data[pos++];
                else if (runBits == 2) { run = data[pos] | (data[pos + 1] << 8); pos += 2; }

                count += run + 1;
            }

            cells = count;
            var expected = (long)width * height;
            if (count != expected)
            {
                error = $"decoded {count} cells, expected {expected}";
                return false;
            }

            if (pos != data.Length)
            {
                error = $"decoder stopped at {pos} of {data.Length} bytes";
                return false;
            }

            return true;
        }
        catch (Exception ex)
        {
            error = ex.Message;
            return false;
        }
    }

    private static int CountSetBits(ReadOnlySpan<byte> bits, int maxId)
    {
        var set = 0;
        for (var id = 0; id < maxId; id++)
        {
            if ((bits[id / 8] & (0x80 >> (id % 8))) != 0) set++;
        }

        return set;
    }

    /// <summary>One count byte per id whose bit is set, in id order.</summary>
    private static void WriteOptionCounts(BinaryWriter writer, int[] counts, int maxId)
    {
        for (var id = 0; id < maxId; id++)
        {
            var count = id < counts.Length ? counts[id] : 1;
            if (count != 1)
            {
                writer.Write((byte)count);
            }
        }
    }

    private static void WriteString7(BinaryWriter writer, string value)
    {
        var bytes = System.Text.Encoding.UTF8.GetBytes(value);
        var length = bytes.Length;
        while (length >= 0x80)
        {
            writer.Write((byte)((length & 0x7F) | 0x80));
            length >>= 7;
        }

        writer.Write((byte)length);
        writer.Write(bytes);
    }

    public readonly record struct CellKind(CellLayer Layer, int Option);

    public enum CellLayer
    {
        None = 0,
        Tile = 1,
        Wall = 2,
        Water = 3,
        Lava = 4,
        Honey = 5,
        Sky = 6,
        DirtOrRock = 7,
    }

    /// <summary>
    /// Per-cell encoder, mirroring <c>MapHelper.SaveMap</c>: keeps the previous cell to
    /// fold equal neighbours into one run, and gives every cell of a run its own light
    /// byte when the light is not 255.
    /// </summary>
    private sealed class CellWriter(Stream stream)
    {
        private bool _hasPrevious;
        private CellLayer _previousLayer;
        private int _previousOption;
        private byte _previousLight;
        private byte _previousColor;

        public void Reset() => _hasPrevious = false;

        public void Push(CellLayer layer, int option, byte light, byte color)
        {
            if (_hasPrevious
                && _previousLayer == layer
                && _previousOption == option
                && _previousColor == color
                && _previousLight == light)
            {
                _run++;
                return;
            }

            Flush();
            _hasPrevious = true;
            _previousLayer = layer;
            _previousOption = option;
            _previousLight = light;
            _previousColor = color;
            _run = 0;
        }

        private int _run;

        public void Flush()
        {
            if (!_hasPrevious)
            {
                return;
            }

            var layer = (int)_previousLayer;
            var type = _previousOption;
            var flags = (byte)(layer << 1);

            if (_previousColor != 0)
            {
                flags |= 0x01;
            }

            if (type > 255)
            {
                flags |= 0x10;
            }

            if (_previousLight != 255)
            {
                flags |= 0x20;
            }

            if (_run > 0)
            {
                flags |= _run <= 255 ? (byte)0x40 : (byte)0x80;
            }

            stream.WriteByte(flags);

            if (_previousColor != 0)
            {
                stream.WriteByte((byte)(_previousColor << 1));
            }

            if (layer is 1 or 2 or 7)
            {
                stream.WriteByte((byte)type);
                if (type > 255)
                {
                    stream.WriteByte((byte)(type >> 8));
                }
            }

            if (_previousLight != 255)
            {
                stream.WriteByte(_previousLight);
            }

            if (_run > 0 && _run <= 255)
            {
                stream.WriteByte((byte)_run);
            }
            else if (_run > 255)
            {
                stream.WriteByte((byte)(_run & 0xFF));
                stream.WriteByte((byte)(_run >> 8));
            }

            _hasPrevious = false;
            _run = 0;
        }
    }
}

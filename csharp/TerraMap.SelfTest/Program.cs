// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Buffers.Binary;
using System.Collections.Generic;
using System.IO;
using TerraMap.Core;

namespace TerraMap.SelfTest;

/// <summary>
/// Runnable check for the L1 core. Everything below works on synthetic bytes unless
/// <c>--with-samples</c> is passed, so it needs no game files and never writes to disk.
/// </summary>
internal static class Program
{
    private static bool _allOk = true;

    private static int Main(string[] args)
    {
        Console.OutputEncoding = System.Text.Encoding.UTF8;

        PaletteMath();
        ChunkGridMath();
        String7();
        DifficultyTable();
        RoundTripSynthetic();
        RevealOverwritesEverything();
        ImportantFrameSkip();
        PlayerMapNaming();
        LargeWorldBuild();

        if (args.Length >= 3 && args[0] == "--with-samples") RealFiles(args[1], args[2], args.Length >= 4 ? args[3] : null);

        Console.WriteLine();
        Console.WriteLine("RESULT: " + (_allOk ? "PASS" : "FAIL"));
        return _allOk ? 0 : 1;
    }

    private static void Check(bool condition, string message)
    {
        Console.WriteLine((condition ? "ok   " : "FAIL ") + message);
        if (!condition) _allOk = false;
    }

    private static byte[] Le(int value)
    {
        var b = new byte[4];
        BinaryPrimitives.WriteInt32LittleEndian(b, value);
        return b;
    }

    private static byte[] Le(short value)
    {
        var b = new byte[2];
        BinaryPrimitives.WriteInt16LittleEndian(b, value);
        return b;
    }

    private static byte[] Le(uint value)
    {
        var b = new byte[4];
        BinaryPrimitives.WriteUInt32LittleEndian(b, value);
        return b;
    }

    // ---------------------------------------------------------------- palette

    private static void PaletteMath()
    {
        var header = new MapHeader(
            raw: Array.Empty<byte>(),
            worldName: "t", worldId: 1, width: 128, height: 128,
            maxTileId: 4, maxWallId: 3,
            tileCounts: new[] { 10, 20, 30, 40 },
            wallCounts: new[] { 7, 8, 9 });

        Check(header.TileBase == 1, $"tile base is 1 (got {header.TileBase})");
        Check(header.TileType(0) == 1 && header.TileType(1) == 11,
            $"tile prefix is 1-based ({header.TileType(0)}, {header.TileType(1)})");
        Check(header.WallBase == 101, $"wall base ({header.WallBase})");
        Check(header.LiquidBase == 125, $"liquid base ({header.LiquidBase})");
        Check(header.SkyBase == 129, $"sky base ({header.SkyBase})");
        Check(header.DirtBase == 385, $"dirt base ({header.DirtBase})");
        Check(header.RockBase == 641, $"rock base ({header.RockBase})");
        Check(header.HellBase == 897, $"hell base ({header.HellBase})");
        Check(header.SkyType(0, 500.0) == 129, "sky index at y=0");
        Check(header.SkyType(250, 500.0) == 129 + 127, "sky index at half of surface");
        Check(header.SkyType(1000, 500.0) == 129 + 254, "sky index wraps through byte cast at 2x surface");
    }

    private static void ChunkGridMath()
    {
        Check(MapHeader.ChunkGrid(70, 70) == (2, 2), "chunk grid for 70x70 is 2x2 (not a multiple of 64)");
        Check(MapHeader.ChunkGrid(6400, 1800) == (100, 29), "chunk grid for 6400x1800 is 100x29");
        Check(MapHeader.ChunkGrid(8400, 2400) == (132, 38), "chunk grid for 8400x2400 is 132x38");
        Check(MapHeader.ChunkGrid(4200, 1200) == (66, 19), "chunk grid for 4200x1200 is 66x19");
    }

    private static void String7()
    {
        byte[] shortOne = { 0x05, (byte)'h', (byte)'e', (byte)'l', (byte)'l', (byte)'o' };
        var r = new SpanReader(shortOne);
        Check(r.String7() == "hello" && r.Position == shortOne.Length, "7-bit string, single-byte length");

        const int count = 129;                       // 0x81 0x01
        var longOne = new List<byte> { 0x81, 0x01 };
        for (int i = 0; i < count; i++) longOne.Add((byte)'a');
        byte[] longBytes = longOne.ToArray();
        var r2 = new SpanReader(longBytes);
        string decoded = r2.String7();
        Check(decoded.Length == count && r2.Position == longBytes.Length, "7-bit string, two-byte length");
    }

    private static void DifficultyTable()
    {
        var cases = new (int Mode, bool Ftw, string Expected)[]
        {
            (0, false, "普通"), (1, false, "专家"), (2, false, "大师"), (3, false, "旅行"),
            (0, true, "专家"), (1, true, "大师"), (2, true, "传奇"), (3, true, "旅行"),
        };

        foreach ((int mode, bool ftw, string expected) in cases)
        {
            string actual = WorldMeta.DifficultyLabel(mode, ftw);
            Check(actual == expected,
                $"difficulty GameMode={mode} FTW={(ftw ? 1 : 0)} -> {expected} (got {actual})");
        }
    }

    // ------------------------------------------------------------ synthetic map

    private const int SyntheticSize = 70;            // deliberately not a multiple of 64

    private static MapHeader SyntheticHeader(int width, int height, int maxTile, int maxWall)
    {
        var raw = new List<byte>();
        raw.AddRange(Le(MapHeader.FileVersion));
        raw.AddRange("relogic"u8.ToArray());
        raw.Add(MapHeader.FileType);
        raw.AddRange(Le(68u));
        raw.AddRange(new byte[8]);                   // favorite
        byte[] name = "synthetic"u8.ToArray();
        raw.Add((byte)name.Length);
        raw.AddRange(name);
        raw.AddRange(Le(7));                         // worldID
        raw.AddRange(Le(height));
        raw.AddRange(Le(width));
        raw.AddRange(Le((short)maxTile));
        raw.AddRange(Le((short)maxWall));
        raw.AddRange(Le((short)4));
        raw.AddRange(Le((short)256));
        raw.AddRange(Le((short)256));
        raw.AddRange(Le((short)256));

        // all option flags false -> every count is the implicit 1.
        // The reader consumes ceil(count/8) bytes: a fresh byte is fetched whenever the mask wraps.
        raw.AddRange(new byte[(maxTile + 7) / 8]);
        raw.AddRange(new byte[(maxWall + 7) / 8]);

        return new MapHeader(raw.ToArray(), "synthetic", 7, width, height, maxTile, maxWall,
            Filled(maxTile, 1), Filled(maxWall, 1), revision: 68);
    }

    private static int[] Filled(int count, int value)
    {
        var result = new int[count];
        Array.Fill(result, value);
        return result;
    }

    private static void RoundTripSynthetic()
    {
        MapHeader header = SyntheticHeader(SyntheticSize, SyntheticSize, 8, 4);
        int total = SyntheticSize * SyntheticSize;

        var types = new ushort[total];
        var lights = new byte[total];
        var colors = new byte[total];
        for (int i = 0; i < total; i++)
        {
            types[i] = (ushort)(i % 12);
            lights[i] = (byte)(i % 255);
            colors[i] = 2;
        }

        byte[] image = MapFile.Write(header, new MapData(header, types, lights, colors));
        Check(image.Length > 0, $"wrote {image.Length} bytes");

        MapData back = MapFile.Read(image);
        Check(back.Header.Width == SyntheticSize && back.Header.Height == SyntheticSize,
            "header round-trips size");
        Check(back.Header.WorldName == "synthetic" && back.Header.WorldId == 7,
            "header round-trips name and worldID");
        Check(back.Header.TileBase == 1 && back.Header.WallBase == 9,
            $"palette survives round-trip (tile {back.Header.TileBase}, wall {back.Header.WallBase})");

        bool typesEqual = true;
        bool lightsEqual = true;
        bool colorsEqual = true;
        for (int i = 0; i < total; i++)
        {
            if (back.Types[i] != types[i]) typesEqual = false;
            if (back.Lights[i] != lights[i]) lightsEqual = false;
            if (back.Colors[i] != colors[i]) colorsEqual = false;
        }

        Check(typesEqual, "types round-trip");
        Check(lightsEqual, "lights round-trip");
        Check(colorsEqual, "colors round-trip");
    }

    private static void RevealOverwritesEverything()
    {
        MapHeader header = SyntheticHeader(SyntheticSize, SyntheticSize, 8, 4);
        int size = SyntheticSize;
        int total = size * size;

        var tiles = new short[total];
        var walls = new short[total];
        Array.Fill(tiles, (short)-1);
        Array.Fill(walls, (short)-1);
        for (int i = 0; i < total; i++)
        {
            if (i % 3 == 0) tiles[i] = (short)(i % 5);
            else if (i % 3 == 1) walls[i] = (short)(i % 4);
        }

        var world = new World("synthetic", 7, size, size, surface: 20.0, rockLayer: 40.0, tiles, walls);
        MapData revealed = Reveal.Build(header, world);

        int unlit = 0;
        int sentinel = 0;
        for (int i = 0; i < total; i++)
        {
            if (revealed.Lights[i] != 255) unlit++;
            if (revealed.Types[i] == 999) sentinel++;
        }

        Check(unlit == 0, $"every cell is lit (unlit {unlit})");
        Check(sentinel == 0, "no stale template type survives the reveal");

        int solidTypes = 0;
        for (int i = 0; i < total; i++)
        {
            if (revealed.Types[i] >= header.TileBase && revealed.Types[i] < header.WallBase) solidTypes++;
        }

        Check(solidTypes > 0, $"solid cells carried over ({solidTypes} cells mapped to tile palette)");

        // A cell with neither tile nor wall must fall back to the depth gradient.
        var probe = new short[total];
        Array.Fill(probe, (short)-1);
        var emptyWorld = new World("synthetic", 7, size, size, 20.0, 40.0, probe, (short[])probe.Clone());
        MapData bg = Reveal.Build(header, emptyWorld);
        Check(bg.Types[0] == header.SkyType(0, 20.0), "row 0 background is the sky gradient");
        Check(bg.Types[total - 1] == header.HellBase, "last row background is hell");
    }

    // ------------------------------------------------------------- synthetic wld

    /// <summary>
    /// Builds a 3x3 world whose first cell is a tile id that lives in the important set, so the
    /// reader must consume the extra 4 frame bytes and still land exactly on the tile section end.
    /// </summary>
    private static void ImportantFrameSkip()
    {
        const int width = 3;
        const int height = 3;
        const int section1Offset = 167;
        const int maxImportant = 754;

        var bytes = new List<byte>();
        bytes.AddRange(Le(326));
        bytes.AddRange("relogic"u8.ToArray());
        bytes.Add(2);                                      // fileType
        while (bytes.Count < 24) bytes.Add(0);
        bytes.AddRange(Le((short)3));                      // section count

        var section1 = new List<byte>();
        section1.Add(1); section1.Add((byte)'t');          // name
        section1.Add(1); section1.Add((byte)'s');          // seed
        section1.AddRange(new byte[8]);                    // worldGenVersion
        section1.AddRange(new byte[16]);                   // guid
        section1.AddRange(Le(9));                          // worldID
        section1.AddRange(new byte[16]);                   // pixel bounds
        section1.AddRange(Le(height));
        section1.AddRange(Le(width));
        // pad up to the spawn point, then write the two layer dividers Load() reads
        while (section1.Count < 162) section1.Add(0);
        var layerBytes = new byte[8];
        BinaryPrimitives.WriteDoubleLittleEndian(layerBytes, 2.0);
        section1.AddRange(layerBytes);
        BinaryPrimitives.WriteDoubleLittleEndian(layerBytes, 3.0);
        section1.AddRange(layerBytes);
        while (section1.Count < 178) section1.Add(0);

        int tileStart = section1Offset + section1.Count;

        // column 0: tile id 1 (important -> 4 frame bytes), then two empty cells
        // columns 1 and 2: three empty cells each
        var tileStream = new List<byte> { 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
        for (int i = 0; i < 2; i++) tileStream.AddRange(new byte[] { 0x00, 0x00, 0x00 });

        int tileEnd = tileStart + tileStream.Count;

        bytes.AddRange(Le(section1Offset));
        bytes.AddRange(Le(tileStart));
        bytes.AddRange(Le(tileEnd));
        while (bytes.Count < 70) bytes.Add(0);
        bytes.AddRange(Le((short)maxImportant));

        var bitmap = new byte[(maxImportant / 8) + 1];
        bitmap[0] |= 0x02;                                 // tile id 1 is "important"
        bytes.AddRange(bitmap);

        while (bytes.Count < section1Offset) bytes.Add(0);
        bytes.AddRange(section1);
        bytes.AddRange(tileStream);

        byte[] image = bytes.ToArray();
        World world = WorldFile.Load(image);

        Check(world.Width == width && world.Height == height, "synthetic .wld size parsed");
        Check(world.TileAt(0, 0) == 1, "important tile decoded");
        Check(world.TileAt(0, 1) == -1 && world.TileAt(2, 2) == -1, "empty cells decoded as -1");
        Check(world.SolidCount() == 1, $"exactly one solid tile (got {world.SolidCount()})");
    }

    private static void PlayerMapNaming()
    {
        byte[] guid = Convert.FromHexString("e957d5c397d37942a7b46e8dc32adff1");
        Check(PlayerMaps.MapFileNameForWorld(guid) == "c3d557e9-d397-4279-a7b4-6e8dc32adff1.map",
            "world guid becomes the .map file name");
        Check(PlayerMaps.GuidFromMapFileName("c3d557e9-d397-4279-a7b4-6e8dc32adff1.map")
                .AsSpan().SequenceEqual(guid),
            ".map file name round-trips to the stored guid");
        Check(PlayerMaps.PlayerNameFromDirectory(@"C:\x\Players\Marina") == "Marina",
            "character name comes from the directory");
    }

    private static void LargeWorldBuild()
    {
        const int width = 6400;
        const int height = 1800;
        MapHeader header = SyntheticHeader(width, height, 8, 4);

        int total = width * height;
        var tiles = new short[total];
        var walls = new short[total];
        Array.Fill(tiles, (short)-1);
        Array.Fill(walls, (short)-1);
        for (int i = 0; i < total; i += 7) tiles[i] = 1;

        var world = new World("big", 1, width, height, 493.0, 757.0, tiles, walls);
        long before = GC.GetTotalMemory(forceFullCollection: true);
        MapData data = Reveal.Build(header, world);
        long after = GC.GetTotalMemory(forceFullCollection: false);

        Check(data.CellCount == total, $"large world built all {total} cells");
        Check(data.Lights[total - 1] == 255, "large world is fully lit");
        Console.WriteLine($"info large-world managed memory ~{(after - before) / (1024 * 1024)} MB");
    }

    // ------------------------------------------------------------- optional real files

    private static void RealFiles(string worldPath, string mapPath, string? dumpPath)
    {
        byte[] worldBytes = File.ReadAllBytes(worldPath);
        byte[] mapBytes = File.ReadAllBytes(mapPath);

        World world = WorldFile.Load(worldBytes);
        WorldMeta meta = WorldFile.ParseMeta(worldBytes);
        MapData map = MapFile.Read(mapBytes);

        Check(world.WorldId == map.Header.WorldId, $"worldID matches ({world.WorldId})");
        Check(world.Width == map.Header.Width && world.Height == map.Header.Height,
            $"size matches ({world.Width}x{world.Height})");
        Console.WriteLine($"info {meta.Name} | {meta.Difficulty} | {meta.Size} | {meta.Evil} | " +
                          $"hardmode={meta.HardMode} moonlord={meta.DefeatedMoonLord}");

        byte[] revealed = Reveal.Write(map.Header, world);
        MapData back = MapFile.Read(revealed);
        Check(back.ExploredCount() == world.Width * world.Height, "revealed payload is fully explored");
        string digest = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(revealed));
        Console.WriteLine($"info reveal size {revealed.Length} bytes, sha256 {digest}");
        Console.WriteLine($"info decoded-grid sha256 {GridDigest(back)}");
        if (dumpPath is not null) { File.WriteAllBytes(dumpPath, revealed); Console.WriteLine($"info wrote {dumpPath}"); }
    }

    /// <summary>
    /// Digest of the decoded grid, independent of the compressor: two implementations that encode the
    /// same map in different byte counts still produce the same value here.
    /// </summary>
    private static string GridDigest(MapData data)
    {
        using var sha = System.Security.Cryptography.SHA256.Create();
        var buffer = new byte[4];
        for (int i = 0; i < data.CellCount; i++)
        {
            BinaryPrimitives.WriteUInt16LittleEndian(buffer, data.Types[i]);
            sha.TransformBlock(buffer, 0, 2, null, 0);
            buffer[0] = data.Lights[i];
            buffer[1] = data.Colors[i];
            sha.TransformBlock(buffer, 0, 2, null, 0);
        }

        sha.TransformFinalBlock(Array.Empty<byte>(), 0, 0);
        return Convert.ToHexString(sha.Hash!);
    }
}


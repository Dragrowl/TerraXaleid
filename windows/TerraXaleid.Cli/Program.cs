// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Diagnostics;
using System.IO;
using TerraXaleid.Core;

namespace TerraXaleid.Cli;

internal static class Program
{
    private static int Main(string[] args)
    {
        if (args.Length == 0 || Has(args, "--help") || Has(args, "-h"))
        {
            Usage();
            return args.Length == 0 ? 2 : 0;
        }

        try
        {
            if (Has(args, "--inspect")) return Inspect(Value(args, "--inspect") ?? args[args.Length - 1]);
            if (Has(args, "--reveal")) return Reveal(args);
            if (Has(args, "--reveal-pc")) return RevealPc(args);
            if (Has(args, "--auto-test")) return AutoTest(args);
            if (Has(args, "--list")) return ListSaves();
        }
        catch (TerrariaFormatException ex)
        {
            Console.Error.WriteLine($"format: {ex.Message}");
            return 3;
        }
        catch (IOException ex)
        {
            Console.Error.WriteLine($"io: {ex.Message}");
            return 4;
        }

        Usage();
        return 2;
    }

    private static void Usage()
    {
        Console.WriteLine("""
            terraxaleid-cli - Terraria map diagnostics and reveal (L1 reference implementation)

              terraxaleid-cli --inspect <file.map>                read a .map and print what it contains
              terraxaleid-cli --reveal --world <file.wld> --out <file.map>
                             [--template <file.map>] [--no-backup] [--dry-run]
              terraxaleid-cli --reveal-pc --world <file.wld> --out <file.map> [--dry-run]
                                                               write the desktop (326) format
              terraxaleid-cli --auto-test --world <file.wld|name> --player <character> [--dry-run]
                             writes Players/<character>/<world guid>.map and reads it back
              terraxaleid-cli --list                              discover save folders
            """);
    }

    /// <summary>Reads a .map and prints header facts plus the explored-cell count.</summary>
    private static int Inspect(string path)
    {
        byte[] bytes = File.ReadAllBytes(path);
        MapData map = MapFile.Read(bytes);
        MapHeader h = map.Header;
        (int columns, int rows) = MapHeader.ChunkGrid(h.Width, h.Height);
        long cells = (long)h.Width * h.Height;

        Console.WriteLine($"file        {path}");
        Console.WriteLine($"version     {h.Version}");
        Console.WriteLine($"world       {h.WorldName} (id {h.WorldId})");
        Console.WriteLine($"size        {h.Width} x {h.Height}");
        Console.WriteLine($"chunks      {columns} x {rows} = {columns * rows}");
        Console.WriteLine($"option ids  tile {h.MaxTileId}, wall {h.MaxWallId}");
        Console.WriteLine($"palette     tile {h.TileBase}, wall {h.WallBase}, liquid {h.LiquidBase}, sky {h.SkyBase}");
        Console.WriteLine($"cells       {cells}");
        Console.WriteLine($"explored    {map.ExploredCount()} ({100.0 * map.ExploredCount() / cells:F1}%)");
        return 0;
    }

    /// <summary>Reveals a world: reads .wld, builds every cell, writes a .map (with .bak).</summary>
    private static int Reveal(string[] args)
    {
        string? worldPath = Value(args, "--world");
        string outPath = Value(args, "--out") ?? throw new ArgumentException("--out is required");
        string? templatePath = Value(args, "--template");
        bool dryRun = Has(args, "--dry-run");
        bool backup = !Has(args, "--no-backup");

        if (worldPath is null) throw new ArgumentException("--world is required");

        var watch = Stopwatch.StartNew();
        World world = WorldFile.Load(File.ReadAllBytes(worldPath));
        Console.WriteLine($"world       {world.Name} {world.Width} x {world.Height} (surface {world.Surface:F1}, rock {world.RockLayer:F1})");

        MapHeader header;
        if (templatePath is not null && File.Exists(templatePath))
        {
            MapHeader parsed = MapHeader.Parse(File.ReadAllBytes(templatePath), out _);
            Console.WriteLine($"template    {templatePath} (version {parsed.Version}, {parsed.Width} x {parsed.Height})");
            if (parsed.Width != world.Width || parsed.Height != world.Height)
                throw new TerrariaFormatException(
                    $"template is {parsed.Width}x{parsed.Height} but the world is {world.Width}x{world.Height}");
            header = parsed;
        }
        else
        {
            int maxTileId = Value(args, "--max-tile") is { } t ? int.Parse(t) : 754;
            int maxWallId = Value(args, "--max-wall") is { } w ? int.Parse(w) : 367;
            header = MapHeader.Create(world.Name, world.WorldId, world.Width, world.Height, maxTileId, maxWallId);
            Console.WriteLine($"template    generated {header.Version} header (tile {maxTileId}, wall {maxWallId})");
        }

        MapData revealed = TerraXaleid.Core.Reveal.Build(header, world);
        Console.WriteLine($"built       {revealed.ExploredCount()} explored cells in {watch.ElapsedMilliseconds} ms");

        byte[] image = MapFile.Write(header, revealed);
        MapData check = MapFile.Read(image);
        if (check.ExploredCount() != (long)world.Width * world.Height)
            throw new TerrariaFormatException($"round-trip lost cells: {check.ExploredCount()}");

        Console.WriteLine($"encoded     {image.Length} bytes, round-trip ok");

        if (dryRun)
        {
            Console.WriteLine("dry-run     nothing written");
            return 0;
        }

        if (backup && File.Exists(outPath))
        {
            string bak = outPath + ".bak";
            if (File.Exists(bak)) File.Copy(bak, outPath + ".bak.1", overwrite: true);
            File.Copy(outPath, bak, overwrite: true);
            Console.WriteLine($"backup      {bak}");
        }

        string tmp = outPath + ".tmp";
        File.WriteAllBytes(tmp, image);
        File.Move(tmp, outPath, overwrite: true);
        Console.WriteLine($"written     {outPath}");
        return 0;
    }

    /// <summary>Writes the desktop map format (version 326) that 1.4.5 saves use.</summary>
    private static int RevealPc(string[] args)
    {
        string worldPath = Value(args, "--world") ?? throw new ArgumentException("--world is required");
        string outPath = Value(args, "--out") ?? throw new ArgumentException("--out is required");
        bool dryRun = Has(args, "--dry-run");

        var watch = Stopwatch.StartNew();
        World world = WorldFile.Load(File.ReadAllBytes(worldPath));
        int maxTileId = Value(args, "--max-tile") is { } t ? int.Parse(t) : 754;
        int maxWallId = Value(args, "--max-wall") is { } w ? int.Parse(w) : 367;
        Console.WriteLine($"world       {world.Name} {world.Width} x {world.Height} (surface {world.Surface:F1}, rock {world.RockLayer:F1})");

        // The per-id option counts are a property of the game version, and any map the game
        // itself saved carries the complete table in its header.
        TileOptions options;
        if (Value(args, "--template") is { } template)
        {
            options = TileOptions.FromFile(template);
            Console.WriteLine($"options     from {template}");
        }
        else if (Value(args, "--options-dir") is { } optionsDir)
        {
            var found = Directory.Exists(optionsDir)
                ? Directory.GetFiles(optionsDir, "*.map", SearchOption.AllDirectories)
                : Array.Empty<string>();
            options = TileOptions.FromFirstReadable(found, out string? picked) ?? new TileOptions(new int[maxTileId], new int[maxWallId]);
            Console.WriteLine($"options     from {picked ?? "<none readable>"} ({found.Length} candidates)");
        }
        else
        {
            string dir = Path.GetDirectoryName(Path.GetFullPath(outPath)) ?? ".";
            var candidates = Directory.Exists(dir)
                ? Array.FindAll(Directory.GetFiles(dir, "*.map"), f => !f.EndsWith(".bak", StringComparison.OrdinalIgnoreCase))
                : Array.Empty<string>();
            options = TileOptions.FromFirstReadable(candidates, out string? source) ?? new TileOptions(new int[maxTileId], new int[maxWallId]);
            Console.WriteLine(source is null
                ? "options     none found in the output folder; assuming one option per id"
                : $"options     from {source}");
        }


        using var buffer = new MemoryStream();
        PcMapFormat.Write(
            buffer,
            options,
            world.Name,
            world.WorldId,
            world.Width,
            world.Height,
            maxTileId,
            maxWallId,
            world.Tiles,
            world.Walls,
            world.LiquidAmounts,
            world.LiquidKinds,
            (float)world.Surface,
            (float)world.RockLayer,
            world.UnderworldLayer);

        byte[] image = buffer.ToArray();
        Console.WriteLine($"encoded     {image.Length} bytes in {watch.ElapsedMilliseconds} ms (version {PcMapFormat.FileVersion})");

        if (dryRun)
        {
            Console.WriteLine("dry-run     nothing written");
            return 0;
        }

        if (File.Exists(outPath))
        {
            string bak = outPath + ".bak";
            if (File.Exists(bak)) File.Copy(bak, outPath + ".bak.1", overwrite: true);
            File.Copy(outPath, bak, overwrite: true);
            Console.WriteLine($"backup      {bak}");
        }

        string tmp = outPath + ".tmp";
        File.WriteAllBytes(tmp, image);
        File.Move(tmp, outPath, overwrite: true);
        Console.WriteLine($"written     {outPath}");
        return 0;
    }

    /// <summary>
    /// End-to-end check of the desktop path: read a world, write the map that belongs to one
    /// character, then read the result back. Prints one line per stage and returns 1 on any
    /// mismatch, so it can be used as a smoke test after a format change.
    /// </summary>
    private static int AutoTest(string[] args)
    {
        string worldArg = Value(args, "--world") ?? throw new ArgumentException("--world is required");
        string playerArg = Value(args, "--player") ?? throw new ArgumentException("--player is required");
        bool dryRun = Has(args, "--dry-run");

        string worldPath = File.Exists(worldArg)
            ? Path.GetFullPath(worldArg)
            : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "My Games", "Terraria", "Worlds", worldArg.EndsWith(".wld") ? worldArg : worldArg + ".wld");
        if (!File.Exists(worldPath))
        {
            Console.WriteLine($"fail        world not found: {worldPath}");
            return 1;
        }

        string saveRoot = Directory.GetParent(Path.GetDirectoryName(worldPath)!)?.FullName ?? ".";
        string playerDir = playerArg.Contains('\\') || playerArg.Contains('/')
            ? Path.GetFullPath(playerArg)
            : Path.Combine(saveRoot, "Players", playerArg);

        var bytes = File.ReadAllBytes(worldPath);
        WorldMeta meta = WorldFile.ParseMeta(bytes);
        string mapName = PlayerMaps.MapFileNameForWorld(Convert.FromHexString(meta.GuidHex));
        string mapPath = Path.Combine(playerDir, mapName);

        Console.WriteLine($"world       {meta.Name} {meta.Width} x {meta.Height} ({Path.GetFileName(worldPath)})");
        Console.WriteLine($"character   {Path.GetFileName(playerDir)} -> {mapName}");
        Console.WriteLine($"target      {mapPath}");

        World world = WorldFile.Load(bytes);
        TileOptions options = TileOptions.FromFirstReadable(
                Directory.Exists(playerDir) ? Directory.GetFiles(playerDir, "*.map") : Array.Empty<string>(), out string? template)
            ?? TileOptions.FromFirstReadable(Directory.Exists(Path.Combine(saveRoot, "Players")) ? Directory.GetFiles(Path.Combine(saveRoot, "Players"), "*.map", SearchOption.AllDirectories) : Array.Empty<string>(), out template)
            ?? new TileOptions(new int[754], new int[367]);
        Console.WriteLine(template is null ? "options     no template found; one option per id" : $"options     from {template}");

        using var buffer = new MemoryStream();
        PcMapFormat.Write(buffer, options, world.Name, world.WorldId, world.Width, world.Height, 754, 367,
            world.Tiles, world.Walls, world.LiquidAmounts, world.LiquidKinds,
            (float)world.Surface, (float)world.RockLayer, world.UnderworldLayer);
        byte[] image = buffer.ToArray();

        if (!PcMapFormat.TryVerify(image, world.Width, world.Height, out long cells, out string? error))
        {
            Console.WriteLine($"verify      FAIL {image.Length} bytes: {error}");
            return 1;
        }

        Console.WriteLine($"verify      ok, {cells:N0} cells, {image.Length:N0} bytes");

        if (dryRun)
        {
            Console.WriteLine("dry-run     nothing written");
            return 0;
        }

        Directory.CreateDirectory(playerDir);
        if (File.Exists(mapPath))
        {
            string bak = mapPath + ".bak";
            if (File.Exists(bak)) File.Copy(bak, mapPath + ".bak.1", overwrite: true);
            File.Copy(mapPath, bak, overwrite: true);
        }

        string tmp = mapPath + ".tmp";
        File.WriteAllBytes(tmp, image);
        File.Move(tmp, mapPath, overwrite: true);
        Console.WriteLine($"written     {mapPath} ({new FileInfo(mapPath).Length:N0} bytes)");

        byte[] back = File.ReadAllBytes(mapPath);
        bool same = back.AsSpan().SequenceEqual(image);
        Console.WriteLine($"readback    {(same ? "identical" : "DIFFERENT")}");
        return same ? 0 : 1;
    }

    private static int ListSaves()
    {
        foreach (SaveFolder folder in SaveDiscovery.Candidates())
            Console.WriteLine($"{(folder.Exists ? "+" : "-")} {folder.Label,-22} {folder.WorldsDir}");
        return 0;
    }

    private static bool Has(string[] args, string name) =>
        Array.Exists(args, a => string.Equals(a, name, StringComparison.OrdinalIgnoreCase));

    private static string? Value(string[] args, string name)
    {
        for (int i = 0; i < args.Length - 1; i++)
            if (string.Equals(args[i], name, StringComparison.OrdinalIgnoreCase))
                return args[i + 1];
        return null;
    }
}

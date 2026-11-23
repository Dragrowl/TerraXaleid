// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Buffers.Binary;
using System.Collections.Generic;
using System.Globalization;

namespace TerraMap.Core;

/// <summary>
/// Reader for <c>.wld</c> files (format 326 / Terraria 1.4.5.x). Read-only by construction:
/// every entry point takes bytes and returns objects, nothing here can touch a file.
/// </summary>
public static class WorldFile
{
    public const int KnownFormatLow = 200;

    private static readonly byte[] Magic = "relogic"u8.ToArray();

    // ---------------------------------------------------------------- metadata

    /// <summary>
    /// Parses only the header a world list needs (no tile stream), which is what the picker
    /// screen shows: name / size / difficulty / evil / hardmode / bosses / creation time.
    /// </summary>
    public static WorldMeta ParseMeta(ReadOnlySpan<byte> data)
    {
        int format = BinaryPrimitives.ReadInt32LittleEndian(data);
        if (format < KnownFormatLow || !data.Slice(4, 7).SequenceEqual(Magic))
            throw new TerrariaFormatException($"not a Terraria world file (format={format})");

        short sectionCount = BinaryPrimitives.ReadInt16LittleEndian(data.Slice(24));
        var r = new SpanReader(data);
        r.Skip(26);
        int section1 = r.Int32();          // section offsets[0] — where the world header lives

        var h = new SpanReader(data);
        h.Skip(section1);

        string name = h.String7();
        string seed = h.String7();
        h.Skip(8);                          // worldGenVersion (int64)
        string guidHex = Convert.ToHexStringLower(h.Take(16));
        int worldId = h.Int32();
        h.Skip(16);                         // left / right / top / bottom (pixel bounds)
        int height = h.Int32();
        int width = h.Int32();
        int gameMode = h.Int32();

        bool[] seedFlags = ReadBooleans(ref h, 9);
        long creation = h.Int64();
        h.Skip(8);                          // last played (int64)
        h.Skip(1);                          // moon type (byte)
        h.Skip(12);                         // treeX[3]
        h.Skip(16);                         // treeStyle[4]
        h.Skip(12);                         // caveBackX[3]
        h.Skip(16);                         // caveBackStyle[4]
        h.Skip(12);                         // ice / jungle / hell background styles
        int spawnX = h.Int32();
        int spawnY = h.Int32();
        double worldSurface = h.Double();
        double rockLayer = h.Double();
        h.Skip(8);                          // time of day (double)
        bool dayTime = h.Byte() != 0;
        int moonPhase = h.Int32();
        ReadBooleans(ref h, 2);             // blood moon, eclipse
        int dungeonX = h.Int32();
        int dungeonY = h.Int32();
        bool crimson = h.Byte() != 0;

        // boss / progression flags, in save order
        bool[] flags = new bool[18];
        ReadBooleans(ref h, 4).CopyTo(flags, 0);    // king slime, eye, eater, queen bee
        ReadBooleans(ref h, 5).CopyTo(flags, 4);    // mech 1..3 + any, plant
        ReadBooleans(ref h, 9).CopyTo(flags, 9);    // golem .. pirates

        ReadBooleans(ref h, 2);             // shadow orb smashed, meteor spawned
        h.Skip(1);                          // shadow orb count (byte)
        h.Skip(4);                          // altar count (int32)
        bool hardMode = h.Byte() != 0;
        h.Skip(1);                          // after party of doom
        h.Skip(12);                         // invasion delay / size / type
        h.Skip(8);                          // invasion x (double)
        h.Skip(8);                          // slime rain time (double)
        h.Skip(1);                          // sundial cooldown (byte)
        h.Skip(1);                          // raining (bool)
        h.Skip(4);                          // rain time (int32)
        h.Skip(4);                          // max rain (float)
        h.Skip(12);                         // cobalt / mythril / adamantite
        h.Skip(8);                          // 8 background style bytes
        h.Skip(4);                          // cloud bg active
        h.Skip(2);                          // cloud count
        h.Skip(4);                          // wind speed target
        int anglerCount = h.Int32();
        for (int i = 0; i < Math.Max(0, anglerCount); i++) h.String7();
        h.Skip(1);                          // saved angler (bool)
        h.Skip(4);                          // angler quest (int32)
        ReadBooleans(ref h, 3);             // stylist, tax collector, golfer
        h.Skip(4);                          // invasion size start
        h.Skip(4);                          // cultist delay

        // BannerSystem.Load: int16 count + int32[count], then (version >= 289) int16 count + uint16[count].
        // Getting this wrong shifts every following flag — it is why a finished world once read as
        // "moon lord not defeated".
        short bannerCount = h.Int16();
        h.Skip(4 * Math.Max(0, (int)bannerCount));
        short claimCount = h.Int16();
        h.Skip(2 * Math.Max(0, (int)claimCount));

        h.Skip(1);                          // fast forward to dawn (bool)
        bool[] endFlags = ReadBooleans(ref h, 4);
        bool downedMoonLord = endFlags[3];

        var seedFlagNames = new[]
        {
            "drunk", "get_good", "tenth_anniversary", "dont_starve", "not_the_bees",
            "remix", "no_traps", "zenith", "skyblock",
        };
        var seedFlagMap = new Dictionary<string, bool>(seedFlagNames.Length, StringComparer.Ordinal);
        for (int i = 0; i < seedFlagNames.Length; i++) seedFlagMap[seedFlagNames[i]] = seedFlags[i];

        var bossNames = new[]
        {
            "downed_king_slime", "downed_eye", "downed_eater", "downed_queen_bee",
            "downed_skeletron", "downed_mech_1", "downed_mech_2", "downed_mech_any",
            "downed_plant", "downed_golem", "downed_slime_king", "saved_goblin",
            "saved_wizard", "saved_mech", "downed_goblins", "downed_clown",
            "downed_frost", "downed_pirates",
        };
        var progression = new Dictionary<string, bool>(flags.Length, StringComparer.Ordinal);
        for (int i = 0; i < bossNames.Length; i++) progression[bossNames[i]] = flags[i];

        return new WorldMeta(
            Name: name,
            Seed: seed,
            WorldId: worldId,
            GuidHex: guidHex,
            Width: width,
            Height: height,
            GameMode: gameMode,
            ForTheWorthy: seedFlags[1],
            Crimson: crimson,
            HardMode: hardMode,
            DefeatedMoonLord: downedMoonLord,
            SpawnX: spawnX,
            SpawnY: spawnY,
            WorldSurface: worldSurface,
            RockLayer: rockLayer,
            DungeonX: dungeonX,
            DungeonY: dungeonY,
            MoonPhase: moonPhase,
            DayTime: dayTime,
            CreationTime: DecodeCreationTime(creation),
            SeedFlags: seedFlagMap,
            Progression: progression);
    }

    /// <summary>
    /// <c>DateTime.ToBinary()</c>: top two bits are <c>Kind</c>, the low 62 bits are ticks.
    /// Kind 1/2 are stored as UTC ticks and must be converted to local time, otherwise the
    /// timestamp prints hours early.
    /// </summary>
    private static DateTimeOffset? DecodeCreationTime(long binary)
    {
        try
        {
            long kind = (binary >> 62) & 3;
            long ticks = binary & 0x3FFF_FFFF_FFFF_FFFFL;
            if (ticks <= 0 || ticks > DateTime.MaxValue.Ticks) return null;

            var value = new DateTime(ticks, DateTimeKind.Unspecified);
            if (kind is 1 or 2)
            {
                value = DateTime.SpecifyKind(value, DateTimeKind.Utc).ToLocalTime();
            }

            return new DateTimeOffset(value);
        }
        catch (ArgumentOutOfRangeException)
        {
            return null;
        }
    }

    // ------------------------------------------------------------------ tiles

    /// <summary>Loads the full world grid. The tile stream must land exactly on section 2's offset.</summary>
    public static World Load(ReadOnlySpan<byte> data)
    {
        int format = BinaryPrimitives.ReadInt32LittleEndian(data);
        if (format < KnownFormatLow || !data.Slice(4, 7).SequenceEqual(Magic))
            throw new TerrariaFormatException($"not a Terraria world file (format={format})");

        short sectionCount = BinaryPrimitives.ReadInt16LittleEndian(data.Slice(24));
        if (sectionCount < 3)
            throw new TerrariaFormatException($".wld has {sectionCount} sections, need at least 3");

        var offsets = new int[sectionCount];
        for (int i = 0; i < sectionCount; i++)
        {
            offsets[i] = BinaryPrimitives.ReadInt32LittleEndian(data.Slice(26 + (i * 4)));
        }

        short maxImportant = BinaryPrimitives.ReadInt16LittleEndian(data.Slice(70));
        ReadOnlySpan<byte> importantBits = data.Slice(72, (maxImportant / 8) + 1);
        var important = new bool[maxImportant + 1];
        for (int i = 0; i < important.Length; i++)
        {
            important[i] = ((importantBits[i / 8] >> (i % 8)) & 1) != 0;
        }

        var hdr = new SpanReader(data);
        hdr.Skip(offsets[0]);
        string name = hdr.String7();
        hdr.String7();                      // seed
        hdr.Skip(8);                        // worldGenVersion
        hdr.Skip(16);                       // guid
        int worldId = hdr.Int32();
        hdr.Skip(16);                       // pixel bounds
        int height = hdr.Int32();
        int width = hdr.Int32();

        // The two layer dividers sit at a fixed position in section 1; reading them from there is
        // exact, while scanning for a plausible pair can land on a neighbouring double and shift the
        // sky gradient by one.
        (double surface, double rockLayer) = ReadLayers(data, offsets[0]);

        int total = checked(width * height);
        var tiles = new short[total];
        var walls = new short[total];
        var liquidAmounts = new byte[total];
        var liquidKinds = new byte[total];
        Array.Fill(tiles, (short)-1);
        Array.Fill(walls, (short)-1);

        int start = offsets[1];
        int end = offsets[2];
        var r = new SpanReader(data);
        r.Skip(start);

        for (int x = 0; x < width; x++)
        {
            int column = 0;
            while (column < height)
            {
                byte f1 = r.Byte();
                byte f2 = 0;
                byte f3 = 0;
                if ((f1 & 1) != 0)
                {
                    f2 = r.Byte();
                    if ((f2 & 1) != 0)
                    {
                        f3 = r.Byte();
                        if ((f3 & 1) != 0) r.Skip(1);   // 4th flags byte is legal but carries nothing we use
                    }
                }

                bool hasTile = ((f1 >> 1) & 1) != 0;
                bool hasWall = ((f1 >> 2) & 1) != 0;
                int liquid = (f1 >> 3) & 3;
                bool wideId = ((f1 >> 5) & 1) != 0;
                int rle = (((f1 >> 7) & 1) * 2) + ((f1 >> 6) & 1);

                int tileId = -1;
                int wallId = -1;

                if (hasTile)
                {
                    tileId = wideId ? r.UInt16() : r.Byte();
                    if (tileId <= maxImportant && important[tileId]) r.Skip(4);   // frame u, v
                    if (((f3 >> 3) & 1) != 0) r.Skip(1);                         // tile paint
                }

                if (hasWall)
                {
                    wallId = r.Byte();
                    if (((f3 >> 4) & 1) != 0) r.Skip(1);                         // wall paint
                }

                // 1..3 = water / lava / honey, and bit 23 upgrades honey to shimmer. The `.map`
                // liquid palette stores the same four slots in that order, so this is the palette
                // offset while the raw amount decides whether the cell is worth painting.
                bool shimmer = ((f3 >> 7) & 1) != 0;
                int liquidKind = shimmer ? 3 : (liquid != 0 ? liquid - 1 : 0);
                int liquidAmount = (liquid != 0 || shimmer) ? r.Byte() : 0;
                if (((f3 >> 6) & 1) != 0) wallId |= r.Byte() << 8;               // wall id high byte

                int repeat = 1;
                if (rle == 2) repeat = r.UInt16() + 1;
                else if (rle == 1) repeat = r.Byte() + 1;

                // RLE never crosses a column boundary: every column holds exactly `height` tiles.
                if (repeat > height - column) repeat = height - column;

                int baseIndex = x * height + column;
                for (int i = 0; i < repeat; i++)
                {
                    tiles[baseIndex + i] = (short)tileId;
                    walls[baseIndex + i] = (short)wallId;
                    if (liquidAmount != 0)
                    {
                        liquidAmounts[baseIndex + i] = (byte)liquidAmount;
                        liquidKinds[baseIndex + i] = (byte)liquidKind;
                    }
                }

                column += repeat;
            }
        }

        if (r.Position != end)
        {
            throw new TerrariaFormatException(
                $"tile stream ended at {r.Position}, expected section 2 at {end}");
        }

        return new World(name, worldId, width, height, surface, rockLayer, tiles, walls, liquidAmounts, liquidKinds);
    }

    /// <summary>
    /// Reads <c>worldSurface</c> and <c>rockLayer</c> straight out of section 1 (they follow the spawn
    /// point), which is exactly where SaveWorld writes them. Used by [Load]; the heuristic below is
    /// only a fallback for files whose section 1 does not match the documented order.
    /// </summary>
    public static (double Surface, double RockLayer) ReadLayers(ReadOnlySpan<byte> data, int section1)
    {
        var h = new SpanReader(data);
        h.Skip(section1);
        h.String7();                        // name
        h.String7();                        // seed
        h.Skip(8);                          // worldGenVersion
        h.Skip(16);                         // guid
        h.Skip(4);                          // worldID
        h.Skip(16);                         // pixel bounds
        h.Skip(4);                          // maxTilesY
        h.Skip(4);                          // maxTilesX
        h.Skip(4);                          // gameMode
        h.Skip(9);                          // seed flags
        h.Skip(8);                          // creation time
        h.Skip(8);                          // last played
        h.Skip(1);                          // moon type
        h.Skip(12 + 16 + 12 + 16 + 12);     // tree / cave / background styles
        h.Skip(4);                          // spawnTileX
        h.Skip(4);                          // spawnTileY
        return (h.Double(), h.Double());
    }

    /// <summary>
    /// The exact offset of <c>worldSurface</c> / <c>rockLayer</c> depends on a long run of
    /// booleans that has changed between releases, so instead of hard-coding it we look for the
    /// only two consecutive doubles that both look like layer depths.
    /// </summary>
    private static (double Surface, double RockLayer) FindLayerFloats(ReadOnlySpan<byte> section1, int height)
    {
        double bestA = double.NaN;
        double bestB = double.NaN;
        double bestScore = double.MaxValue;

        for (int off = 0; off + 16 <= section1.Length; off++)
        {
            double a = BinaryPrimitives.ReadDoubleLittleEndian(section1.Slice(off));
            double b = BinaryPrimitives.ReadDoubleLittleEndian(section1.Slice(off + 8));

            if (!(a > 50.0 && a < height - 100)) continue;
            if (!(b > a + 20 && b < height - 50)) continue;

            double score = Math.Abs((b - a) - 264.0);
            if (score > 90) continue;
            if (score >= bestScore) continue;

            bestScore = score;
            bestA = a;
            bestB = b;
        }

        if (!double.IsNaN(bestA)) return (bestA, bestB);

        // Fallback only shifts the sky gradient above ground; tile data is unaffected.
        return (height * 0.274, height * 0.42);
    }

    private static bool[] ReadBooleans(ref SpanReader r, int count)
    {
        var result = new bool[count];
        for (int i = 0; i < count; i++) result[i] = r.Byte() != 0;
        return result;
    }

    internal static string FormatDouble(double value) =>
        value.ToString("0.###", CultureInfo.InvariantCulture);
}

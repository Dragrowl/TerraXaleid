// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Collections.Generic;

namespace TerraXaleid.Core;

/// <summary>
/// The subset of a world header the in-game world list shows. Field order and decoding
/// follow <c>Terraria.IO.WorldFile.SaveWorld</c> of 1.4.5.x verbatim — see
/// <see cref="WorldFile.ParseMeta"/> for the byte walk.
/// </summary>
public sealed record WorldMeta(
    string Name,
    string Seed,
    int WorldId,
    string GuidHex,
    int Width,
    int Height,
    int GameMode,
    bool ForTheWorthy,
    bool Crimson,
    bool HardMode,
    bool DefeatedMoonLord,
    int SpawnX,
    int SpawnY,
    double WorldSurface,
    double RockLayer,
    int DungeonX,
    int DungeonY,
    int MoonPhase,
    bool DayTime,
    DateTimeOffset? CreationTime,
    IReadOnlyDictionary<string, bool> SeedFlags,
    IReadOnlyDictionary<string, bool> Progression)
{
    private static readonly string[] DifficultyTiers = { "普通", "专家", "大师", "旅行" };
    private static readonly string[] ForTheWorthyTiers = { "普通", "专家", "大师", "传奇" };

    /// <summary>Width -> the label the world list prints.</summary>
    public static string SizeLabel(int width) => width switch
    {
        4200 => "小世界",
        6400 => "中世界",
        8400 => "大世界",
        17600 => "大世界",
        _ => $"{width} 宽",
    };

    /// <summary>
    /// For the Worthy bumps the *displayed* tier one step, but the GameMode field itself stays
    /// untouched (master + FTW is written as GameMode 2 and only then reads as 传奇). Journey is
    /// its own axis and is never bumped.
    /// </summary>
    public static string DifficultyLabel(int gameMode, bool forTheWorthy)
    {
        if (gameMode == 3) return "旅行";
        if (forTheWorthy)
        {
            int idx = Math.Min(3, Math.Max(0, gameMode) + 1);
            return ForTheWorthyTiers[idx];
        }

        return gameMode >= 0 && gameMode < DifficultyTiers.Length
            ? DifficultyTiers[gameMode]
            : $"未知({gameMode})";
    }

    public string Difficulty => DifficultyLabel(GameMode, ForTheWorthy);

    public string Size => SizeLabel(Width);

    public string Evil => Crimson ? "猩红" : "腐化";
}

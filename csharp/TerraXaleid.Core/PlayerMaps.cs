// SPDX-License-Identifier: GPL-3.0-or-later

using System;

namespace TerraXaleid.Core;

/// <summary>
/// Naming rules for the per-character map files.
/// </summary>
/// <remarks>
/// Terraria keeps exploration per (world, character), so one world has as many map files as the
/// characters that have visited it:
/// <code>
/// Players/&lt;character&gt;.plr          the character
/// Players/&lt;character&gt;/&lt;world&gt;.map  what that character has explored in that world
/// </code>
/// The <c>&lt;world&gt;</c> part is the world GUID as .NET prints a <see cref="Guid"/>: the first three
/// groups of the stored bytes are byte-swapped, the last two are not. For the world whose stored
/// bytes are <c>e9 57 d5 c3 97 d3 79 42 a7 b4 6e 8d c3 2a df f1</c> the file is called
/// <c>c3d557e9-d397-4279-a7b4-6e8dc32adff1.map</c>.
/// </remarks>
public static class PlayerMaps
{
    public const string MapExtension = ".map";

    /// <summary>The `.map` file name a character's directory uses for this world.</summary>
    public static string MapFileNameForWorld(ReadOnlySpan<byte> guid)
    {
        if (guid.Length != 16)
        {
            throw new ArgumentException($"a GUID is 16 bytes, got {guid.Length}", nameof(guid));
        }

        return new Guid(guid).ToString("D") + MapExtension;
    }

    /// <summary>The stored 16 GUID bytes behind a `.map` file name (no extension).</summary>
    public static byte[] GuidFromMapFileName(string fileName)
    {
        string stem = fileName.EndsWith(MapExtension, StringComparison.OrdinalIgnoreCase)
            ? fileName[..^MapExtension.Length]
            : fileName;
        if (!Guid.TryParse(stem, out Guid guid))
        {
            throw new ArgumentException($"not a GUID file name: {fileName}", nameof(fileName));
        }

        return guid.ToByteArray();
    }

    /// <summary>
    /// The character name behind a map directory. Terraria uses the directory name as the
    /// character name, so no parsing of the <c>.plr</c> is needed to build the list.
    /// </summary>
    public static string PlayerNameFromDirectory(string directoryPath)
    {
        string trimmed = directoryPath.TrimEnd('/', '\\');
        int cut = trimmed.LastIndexOfAny(new[] { '/', '\\' });
        return cut >= 0 ? trimmed[(cut + 1)..] : trimmed;
    }
}

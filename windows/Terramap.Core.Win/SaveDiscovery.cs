// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Collections.Generic;
using System.IO;

namespace TerraMap.Core;

/// <summary>One candidate save root: the folder that contains <c>Worlds</c> and <c>Players</c>.</summary>
public sealed record SaveFolder(string Label, string Root, string WorldsDir, string PlayersDir)
{
    public bool Exists => Directory.Exists(WorldsDir) && Directory.Exists(PlayersDir);
}

/// <summary>
/// Finds the Terraria save folder on Windows. The game always uses
/// <c>%USERPROFILE%\Documents\My Games\Terraria</c>, but "Documents" can be redirected to OneDrive
/// or localized, so several spellings are tried before giving up and asking the user.
/// </summary>
public static class SaveDiscovery
{
    public const string Relative = @"My Games\Terraria";

    public static IReadOnlyList<SaveFolder> Candidates()
    {
        var list = new List<SaveFolder>();
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        void Add(string label, string? root)
        {
            if (string.IsNullOrWhiteSpace(root)) return;

            string full;
            try { full = Path.GetFullPath(root!); }
            catch (Exception) { return; }
            if (!seen.Add(full)) return;

            list.Add(new SaveFolder(label, full, Path.Combine(full, "Worlds"), Path.Combine(full, "Players")));
        }

        string profile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        Add("documents", Path.Combine(profile, "Documents", Relative));

        // Shell-resolved Documents (handles redirection to OneDrive and localized names).
        string shellDocuments = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
        if (!string.IsNullOrWhiteSpace(shellDocuments))
            Add("shell-documents", Path.Combine(shellDocuments, Relative));

        string? oneDrive = Environment.GetEnvironmentVariable("OneDrive")
                           ?? Environment.GetEnvironmentVariable("OneDriveConsumer");
        if (!string.IsNullOrWhiteSpace(oneDrive))
        {
            Add("onedrive", Path.Combine(oneDrive!, "Documents", Relative));
            Add("onedrive-zh", Path.Combine(oneDrive!, "文档", Relative));
        }

        return list;
    }

    /// <summary>First candidate whose <c>Worlds</c> and <c>Players</c> folders both exist.</summary>
    public static SaveFolder? Locate()
    {
        foreach (SaveFolder folder in Candidates())
            if (folder.Exists) return folder;

        return null;
    }
}

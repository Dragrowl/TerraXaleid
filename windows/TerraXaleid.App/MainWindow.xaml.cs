// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using TerraXaleid.Core;

namespace TerraXaleid.App;

/// <summary>
/// Windows shell. It reads the save folder, lists worlds, and carries the same three actions the
/// phone keeps in its overflow menu (refresh / backups / settings) on a left icon rail.
/// The map writer stays in <see cref="TerraXaleid.Core"/>; this window only drives files.
/// </summary>
public partial class MainWindow : Window
{
    private sealed record WorldRow(string Name, string Difficulty, string Size, string Evil, string Progress, string Guid);

    private sealed record BackupRow(string Title, string Detail, string Path, string Target);

    private readonly Dictionary<string, string> _worldNames = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, string> _worldByGuid = new(StringComparer.OrdinalIgnoreCase);

    private List<WorldRow> _all = new();
    private SaveFolder? _folder;

    public MainWindow()
    {
        InitializeComponent();
        ApplyTheme(DesktopConfig.Load().Theme, save: false);
        AboutText.Text = $"版本 0.1.0-dev · GPL-3.0-or-later\n三语言核心：Python / C# / Kotlin，读取 .wld 后生成合法的 .map。";
        Reload();
        ShowPage(0);
    }

    // ── navigation ────────────────────────────────────────────────────────────

    private void OnNavWorlds(object sender, RoutedEventArgs e) => ShowPage(0);

    private void OnNavBackups(object sender, RoutedEventArgs e)
    {
        ShowPage(1);
        ReloadBackups();
    }

    private void OnNavSettings(object sender, RoutedEventArgs e) => ShowPage(2);

    private void ShowPage(int index)
    {
        PageWorlds.Visibility = index == 0 ? Visibility.Visible : Visibility.Collapsed;
        PageBackups.Visibility = index == 1 ? Visibility.Visible : Visibility.Collapsed;
        PageSettings.Visibility = index == 2 ? Visibility.Visible : Visibility.Collapsed;

        var active = (Style)FindResource("RailButtonActive");
        var idle = (Style)FindResource("RailButton");
        NavWorlds.Style = index == 0 ? active : idle;
        NavBackups.Style = index == 1 ? active : idle;
        NavSettings.Style = index == 2 ? active : idle;
    }

    // ── worlds ────────────────────────────────────────────────────────────────

    private void OnRefresh(object sender, RoutedEventArgs e)
    {
        Reload();
        if (PageBackups.Visibility == Visibility.Visible)
        {
            ReloadBackups();
        }
    }

    private void OnSearchChanged(object sender, TextChangedEventArgs e)
    {
        SearchHint.Visibility = string.IsNullOrEmpty(SearchBox.Text) ? Visibility.Visible : Visibility.Collapsed;
        ApplyFilter();
    }

    private void Reload()
    {
        _all = new List<WorldRow>();
        _folder = SaveDiscovery.Locate();

        if (_folder is null)
        {
            StatusText.Text = @"找不到存档目录（%USERPROFILE%\Documents\My Games\Terraria）。";
            WorldList.ItemsSource = null;
            return;
        }

        string? lastError = null;
        foreach (string file in Directory.EnumerateFiles(_folder.WorldsDir, "*.wld").OrderBy(f => f))
        {
            try
            {
                WorldMeta meta = WorldFile.ParseMeta(File.ReadAllBytes(file));
                string progress = meta.HardMode
                    ? meta.DefeatedMoonLord ? "已击败月亮领主" : "困难模式"
                    : "困难模式之前";

                string guid = Path.GetFileNameWithoutExtension(file);
                _worldNames[guid] = meta.Name;
                _worldByGuid[meta.GuidHex] = meta.Name;
                _all.Add(new WorldRow(meta.Name, meta.Difficulty, meta.Size, meta.Evil, progress, guid));
            }
            catch (Exception ex)
            {
                lastError = $"跳过 {Path.GetFileName(file)}：{ex.Message}";
            }
        }

        StatusText.Text = lastError ?? $"存档目录：{_folder.Root} · 世界 {_all.Count} 个";
        ApplyFilter();
    }

    private void ApplyFilter()
    {
        string query = SearchBox.Text?.Trim() ?? string.Empty;
        WorldList.ItemsSource = query.Length == 0
            ? _all
            : _all.Where(w => w.Name.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();
    }

    // ── backups ───────────────────────────────────────────────────────────────

    private void ReloadBackups()
    {
        var rows = new List<BackupRow>();
        _folder ??= SaveDiscovery.Locate();

        if (_folder is null)
        {
            BackupStatus.Text = "找不到存档目录。";
            BackupList.ItemsSource = null;
            return;
        }

        try
        {
            foreach (string characterDir in Directory.EnumerateDirectories(_folder.PlayersDir))
            {
                string character = Path.GetFileName(characterDir);
                foreach (string file in Directory.EnumerateFiles(characterDir, "*.map.bak*").OrderByDescending(File.GetLastWriteTime))
                {
                    string mapName = Path.GetFileName(file);
                    string guid = mapName.Split('.')[0];

                    // The map file is named after the world's *internal* GUID, which is not always
                    // the .wld file name, so resolve through the guid stored in the world header.
                    // System.Guid keeps the first three groups little-endian in memory, so a
                    // filename guid has to be round-tripped through Guid before comparing bytes.
                    string flatGuid = Guid.TryParse(guid, out Guid parsedGuid)
                        ? Convert.ToHexStringLower(parsedGuid.ToByteArray())
                        : guid.Replace("-", string.Empty).ToLowerInvariant();
                    if (!_worldByGuid.TryGetValue(flatGuid, out string? world) || world is null)
                    {
                        world = _worldNames.TryGetValue(guid, out string? byFile) && byFile is not null ? byFile : guid;
                    }

                    var info = new FileInfo(file);
                    string kind = mapName.EndsWith(".bak", StringComparison.OrdinalIgnoreCase) ? "最新备份" : mapName[(mapName.LastIndexOf(".bak", StringComparison.OrdinalIgnoreCase) + 4)..];
                    rows.Add(new BackupRow(
                        $"{world} · {character}",
                        $"{kind} · {info.Length / 1024.0:F0} KB · {info.LastWriteTime:yyyy-MM-dd HH:mm}",
                        file,
                        Path.Combine(characterDir, guid + ".map")));
                }
            }
        }
        catch (Exception ex)
        {
            BackupStatus.Text = $"读取备份失败：{ex.Message}";
        }

        BackupList.ItemsSource = rows;
        BackupStatus.Text = rows.Count == 0
            ? "还没有任何备份。用桌面版点亮地图时，覆盖前会自动留一份 .map.bak。"
            : $"共 {rows.Count} 份备份";
    }

    private void OnRestoreBackup(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is not BackupRow row)
        {
            return;
        }

        try
        {
            if (File.Exists(row.Target))
            {
                RotateBackups(row.Target);
            }

            File.Copy(row.Path, row.Target, overwrite: true);
            BackupStatus.Text = $"已恢复：{Path.GetFileName(row.Target)}";
            ReloadBackups();
        }
        catch (Exception ex)
        {
            BackupStatus.Text = $"恢复失败：{ex.Message}";
        }
    }

    private void OnDeleteBackup(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is not BackupRow row)
        {
            return;
        }

        try
        {
            File.Delete(row.Path);
            BackupStatus.Text = $"已删除备份：{Path.GetFileName(row.Path)}";
            ReloadBackups();
        }
        catch (Exception ex)
        {
            BackupStatus.Text = $"删除失败：{ex.Message}";
        }
    }

    /// <summary>.bak → .bak.1 → .bak.2, the same rotation the Android build uses.</summary>
    private static void RotateBackups(string mapPath)
    {
        string bak = mapPath + ".bak";
        string bak1 = bak + ".1";
        string bak2 = bak + ".2";

        if (File.Exists(bak2))
        {
            File.Delete(bak2);
        }

        if (File.Exists(bak1))
        {
            File.Move(bak1, bak2);
        }

        if (File.Exists(bak))
        {
            File.Move(bak, bak1);
        }

        File.Move(mapPath, bak);
    }

    // ── settings ──────────────────────────────────────────────────────────────

    private void OnThemeClick(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is not string theme)
        {
            return;
        }

        ApplyTheme(theme, save: true);
    }

    private void ApplyTheme(string theme, bool save)
    {
        bool dark = theme switch
        {
            "dark" => true,
            "light" => false,
            _ => IsSystemDark(),
        };

        var palette = dark ? DarkPalette : LightPalette;
        foreach ((string key, string colour) in palette)
        {
            var colourValue = (Color)ColorConverter.ConvertFromString(colour);
            Application.Current.Resources[key] = new SolidColorBrush(colourValue);
        }

        if (save)
        {
            DesktopConfig.Save(theme);
        }

        var active = (Style)FindResource("MaterialDesignOutlinedButton");
        _ = active;
        ThemeSystem.FontWeight = theme == "system" ? FontWeights.SemiBold : FontWeights.Normal;
        ThemeLight.FontWeight = theme == "light" ? FontWeights.SemiBold : FontWeights.Normal;
        ThemeDark.FontWeight = theme == "dark" ? FontWeights.SemiBold : FontWeights.Normal;
    }

    private static bool IsSystemDark()
    {
        try
        {
            using var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(
                @"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            return key?.GetValue("AppsUseLightTheme") is int value && value == 0;
        }
        catch
        {
            return false;
        }
    }

    private static readonly (string Key, string Colour)[] LightPalette =
    {
        ("App.Background", "#F7F7F5"),
        ("App.Surface", "#FFFFFF"),
        ("App.Rail", "#F1F1EC"),
        ("App.Border", "#E4E4DF"),
        ("App.Chip", "#EFEFEA"),
        ("App.Text", "#1B1B1B"),
        ("App.TextSoft", "#6A6A64"),
        ("App.Accent", "#3F5B45"),
        ("App.AccentSoft", "#E3E7DD"),
    };

    private static readonly (string Key, string Colour)[] DarkPalette =
    {
        ("App.Background", "#121212"),
        ("App.Surface", "#1E1E1E"),
        ("App.Rail", "#171717"),
        ("App.Border", "#2C2C2C"),
        ("App.Chip", "#262626"),
        ("App.Text", "#E8E8E6"),
        ("App.TextSoft", "#A2A29C"),
        ("App.Accent", "#A9C3A9"),
        ("App.AccentSoft", "#2C3A2E"),
    };
}

/// <summary>Tiny json config in %APPDATA%\TerraXaleid\config.json (theme only for now).</summary>
internal sealed record DesktopConfig(string Theme)
{
    private static string FilePath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "TerraXaleid", "config.json");

    public static DesktopConfig Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                using var doc = JsonDocument.Parse(File.ReadAllText(FilePath));
                if (doc.RootElement.TryGetProperty("theme", out JsonElement theme) && theme.ValueKind == JsonValueKind.String)
                {
                    return new DesktopConfig(theme.GetString() ?? "system");
                }
            }
        }
        catch
        {
            // A broken config must never stop the app from starting.
        }

        return new DesktopConfig("system");
    }

    public static void Save(string theme)
    {
        try
        {
            string? dir = Path.GetDirectoryName(FilePath);
            if (dir is not null)
            {
                Directory.CreateDirectory(dir);
            }

            File.WriteAllText(FilePath, JsonSerializer.Serialize(new Dictionary<string, string> { ["theme"] = theme },
                new JsonSerializerOptions { WriteIndented = true }));
        }
        catch
        {
            // Ignore: the theme simply will not persist.
        }
    }
}

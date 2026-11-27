// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.ComponentModel;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using TerraXaleid.Core;

namespace TerraXaleid.App;

/// <summary>
/// Windows shell. Worlds on the left rail, backups and settings behind it, and a character page
/// that mirrors the phone's two-level flow. The map writer stays in <see cref="TerraXaleid.Core"/>;
/// this window only drives files.
/// </summary>
public partial class MainWindow : Window
{
    private const string BilibiliUrl = "https://space.bilibili.com/3461573882415902";
    private const string GithubUrl = "https://github.com/Dragrowl/TerraXaleid";

    private sealed class WorldRow : INotifyPropertyChanged
    {
        private bool _selected;

        public WorldRow(string name, string difficulty, string size, string evil, string progress, string guid, string mapGuid, int maps)
        {
            Name = name;
            Difficulty = difficulty;
            Size = size;
            Evil = evil;
            Progress = progress;
            Guid = guid;
            MapGuid = mapGuid;
            Maps = maps;
        }

        public string Name { get; }

        public string Difficulty { get; }

        public string Size { get; }

        public string Evil { get; }

        public string Progress { get; }

        public string Guid { get; }

        public string MapGuid { get; }

        /// <summary>How many characters already carry a revealed map for this world.</summary>
        public int Maps { get; }

        public string MapsText => Maps == 0 ? Str("Worlds.NoMaps") : string.Format(Str("Worlds.Maps"), Maps);

        public bool Selected
        {
            get => _selected;
            set
            {
                if (_selected == value) return;
                _selected = value;
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Selected)));
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(TickVisibility)));
            }
        }

        public Visibility TickVisibility => _selected ? Visibility.Visible : Visibility.Hidden;

        public event PropertyChangedEventHandler? PropertyChanged;
    }

    private sealed class CharacterRow : INotifyPropertyChanged
    {
        private bool _selected;

        public CharacterRow(string name, string detail, string guid)
        {
            Name = name;
            Detail = detail;
            Guid = guid;
        }

        public string Name { get; }

        public string Detail { get; }

        public string Guid { get; }

        public bool Selected
        {
            get => _selected;
            set
            {
                if (_selected == value) return;
                _selected = value;
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Selected)));
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(TickVisibility)));
            }
        }

        public Visibility TickVisibility => _selected ? Visibility.Visible : Visibility.Hidden;

        public event PropertyChangedEventHandler? PropertyChanged;
    }

    private sealed record BackupRow(string Title, string Detail, string Path, string Target);

    private readonly Dictionary<string, string> _worldNames = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, string> _worldByGuid = new(StringComparer.OrdinalIgnoreCase);

    private readonly HashSet<string> _picked = new();

    /// <summary>What the last reveal wrote, so the user can take it back in one click.</summary>
    private readonly List<string> _lastTargets = new();

    /// <summary>Worlds ticked for a batch reveal (Android has the same multi-select).</summary>
    private readonly HashSet<WorldRow> _selectedWorlds = new();
    private WorldRow? _world;
    private List<WorldRow> _all = new();
    private SaveFolder? _folder;
    private string _theme = "system";

    /// <summary>Dark look: "standard" charcoal or "oled" true black (phone parity).</summary>
    private string _darkVariant = "standard";
    private string _language = "sc";

    public MainWindow()
    {
        InitializeComponent();

        DesktopConfig config = DesktopConfig.Load();
        _theme = config.Theme;
        _darkVariant = config.DarkVariant;
        _language = config.Language;

        ApplyLanguage(_language, save: false);
        ApplyTheme(_theme, save: false);
        SelectLanguageItem(_language);
        Reload();
        ShowPage(0);

        // A WindowStyle=None window otherwise maximises over the taskbar, which pushes both the
        // rail's bottom button and the page footers off screen. Clamp to the work area exactly:
        // any positive slack puts the last row where hit testing can no longer reach it.
        Loaded += (_, _) =>
        {
            Rect work = SystemParameters.WorkArea;
            MaxHeight = work.Height;
            MaxWidth = work.Width;
        };

        StateChanged += (_, _) => UpdateMaximizeGlyph();
        UpdateMaximizeGlyph();
    }

    // ── window chrome ─────────────────────────────────────────────────────────

    private void OnMinimize(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void OnMaximize(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    }

    private void OnClose(object sender, RoutedEventArgs e) => Close();

    private void UpdateMaximizeGlyph()
    {
        bool maximized = WindowState == WindowState.Maximized;
        BtnMaximize.ToolTip = new ToolTip
        {
            Style = (Style)FindResource("RailTip"),
            Content = FindResource(maximized ? "Win.Restore" : "Win.Maximize"),
        };
    }

    // ── navigation ────────────────────────────────────────────────────────────

    private int _page;

    /// <summary>Ctrl+1..4 jumps between the four sections, the way desktop apps do it.</summary>
    private void OnWindowKeyDown(object sender, KeyEventArgs e)
    {
        if (Keyboard.Modifiers != ModifierKeys.Control) return;
        switch (e.Key)
        {
            case Key.D1:
            case Key.NumPad1:
                ShowPage(0);
                e.Handled = true;
                break;
            case Key.D2:
            case Key.NumPad2:
                ShowPage(2);
                ReloadBackups();
                e.Handled = true;
                break;
            case Key.D3:
            case Key.NumPad3:
                ShowPage(3);
                e.Handled = true;
                break;
            case Key.B:
                OnBatchPickClick(this, new RoutedEventArgs());
                e.Handled = true;
                break;
        }
    }

    private void OnNavWorlds(object sender, RoutedEventArgs e) => ShowPage(0);

    private void OnNavBackups(object sender, RoutedEventArgs e)
    {
        ShowPage(2);
        ReloadBackups();
    }

    private void OnNavSettings(object sender, RoutedEventArgs e) => ShowPage(3);

    private void ShowPage(int index)
    {
        // 1 is the character page: it is only reachable from a world card.
        _page = index;
        PageWorlds.Visibility = index == 0 ? Visibility.Visible : Visibility.Collapsed;
        PageCharacters.Visibility = index == 1 ? Visibility.Visible : Visibility.Collapsed;
        PageBackups.Visibility = index == 2 ? Visibility.Visible : Visibility.Collapsed;
        PageSettings.Visibility = index == 3 ? Visibility.Visible : Visibility.Collapsed;

        var active = (Style)FindResource("RailButtonActive");
        var idle = (Style)FindResource("RailButton");
        NavWorlds.Style = index == 0 || index == 1 ? active : idle;
        NavBackups.Style = index == 2 ? active : idle;
        NavSettings.Style = index == 3 ? active : idle;

        FrameworkElement page = index switch
        {
            1 => PageCharacters,
            2 => PageBackups,
            3 => PageSettings,
            _ => PageWorlds,
        };
        FadeInPage(page);
    }

    /// <summary>Material style page change: a short fade plus a 10 px slide from the right.</summary>
    private static void FadeInPage(FrameworkElement page)
    {
        var slide = page.RenderTransform as TranslateTransform;
        if (slide is null)
        {
            slide = new TranslateTransform();
            page.RenderTransform = slide;
        }

        var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
        var fade = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(160)) { EasingFunction = ease };
        var move = new DoubleAnimation(10, 0, TimeSpan.FromMilliseconds(190)) { EasingFunction = ease };
        page.BeginAnimation(OpacityProperty, fade);
        slide.BeginAnimation(TranslateTransform.XProperty, move);
    }

    private void OnBackToWorlds(object sender, RoutedEventArgs e) => ShowPage(0);

    // ── worlds ────────────────────────────────────────────────────────────────

    private void OnRefreshCommand(object sender, ExecutedRoutedEventArgs e) => OnRefresh(sender, e);

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
        if (PageWorlds.Visibility == Visibility.Visible)
        {
            ApplyFilter();
        }
    }

    private void Reload()
    {
        _all = new List<WorldRow>();
        _worldNames.Clear();
        _worldByGuid.Clear();
        _folder = SaveDiscovery.Locate();

        if (_folder is null)
        {
            StatusText.Text = Str("Worlds.Missing");
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
                    ? meta.DefeatedMoonLord ? Str("Worlds.MoonLord") : Str("Worlds.HardMode")
                    : Str("Worlds.PreHardMode");

                string guid = Path.GetFileNameWithoutExtension(file);
                _worldNames[guid] = meta.Name;
                _worldByGuid[meta.GuidHex] = meta.Name;
                // Map files are named after the world's internal GUID (dashed form), which is
                // usually different from the .wld file name.
                string mapGuid = new Guid(Convert.FromHexString(meta.GuidHex)).ToString();
                int maps = 0;
                foreach (string characterDir in Directory.GetDirectories(_folder.PlayersDir))
                {
                    if (File.Exists(Path.Combine(characterDir, mapGuid + ".map")))
                    {
                        maps++;
                    }
                }

                _all.Add(new WorldRow(meta.Name, meta.Difficulty, meta.Size, meta.Evil, progress, guid, mapGuid, maps));
            }
            catch (Exception ex)
            {
                lastError = $"跳过 {Path.GetFileName(file)}：{ex.Message}";
            }
        }

        if (_all.Count == 0)
        {
            StatusText.Text = Str("Worlds.Empty");
        }
        else
        {
            StatusText.Text = lastError ?? string.Format(Str("Worlds.Count"), _folder.Root, _all.Count);
        }

        ApplyFilter();
    }

    private void ApplyFilter()
    {
        string query = SearchBox.Text?.Trim() ?? string.Empty;
        List<WorldRow> rows = query.Length == 0
            ? _all
            : _all.Where(w => w.Name.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();
        WorldList.ItemsSource = rows;
        WorldEmpty.Visibility = rows.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private void OnWorldMenuOpen(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is WorldRow world)
        {
            ShowCharacters(world);
        }
    }

    private void OnWorldMenuTick(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is not WorldRow row) return;

        row.Selected = !row.Selected;
        if (row.Selected)
        {
            _selectedWorlds.Add(row);
        }
        else
        {
            _selectedWorlds.Remove(row);
        }

        StatusText.Text = _selectedWorlds.Count == 0
            ? (_folder is null ? string.Empty : string.Format(Str("Worlds.Count"), _folder.Root, _all.Count))
            : string.Format(Str("Worlds.BatchHint"), _selectedWorlds.Count);
    }

    private void OnWorldMenuFolder(object sender, RoutedEventArgs e)
    {
        if (_folder is null) return;

        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = "explorer.exe",
                Arguments = $"\"{_folder.WorldsDir}\"",
                UseShellExecute = true,
            });
        }
        catch (Exception ex)
        {
            StatusText.Text = ex.Message;
        }
    }

    private void OnWorldCardKeyDown(object sender, KeyEventArgs e)
    {
        if (sender is not FrameworkElement element || element.DataContext is not WorldRow row) return;

        switch (e.Key)
        {
            case Key.Space:
                ToggleWorldSelection(row);
                e.Handled = true;
                break;
            case Key.Enter:
                ShowCharacters(row);
                e.Handled = true;
                break;
        }
    }

    private void OnWorldTickClick(object sender, MouseButtonEventArgs e)
    {
        // ticking means "include in the batch"; the card itself still navigates on a plain click
        if (sender is not FrameworkElement element || element.DataContext is not WorldRow row) return;

        ToggleWorldSelection(row);
        e.Handled = true;
    }

    // keyboard path: Tab to the tick button, press Space
    private void OnWorldTickButton(object sender, RoutedEventArgs e)
    {
        if (sender is not FrameworkElement element || element.DataContext is not WorldRow row) return;

        ToggleWorldSelection(row);
        e.Handled = true;
    }

    private void ToggleWorldSelection(WorldRow row)
    {
        row.Selected = !row.Selected;
        if (row.Selected)
        {
            _selectedWorlds.Add(row);
        }
        else
        {
            _selectedWorlds.Remove(row);
        }

        // the batch action runs on Ctrl+B; the status line keeps it discoverable
        StatusText.Text = _selectedWorlds.Count == 0
            ? (_folder is null ? string.Empty : string.Format(Str("Worlds.Count"), _folder.Root, _all.Count))
            : string.Format(Str("Worlds.BatchHint"), _selectedWorlds.Count);
    }

    private void OnBatchPickClick(object sender, RoutedEventArgs e)
    {
        if (_selectedWorlds.Count == 0) return;

        _lastTargets.Clear();
        int worlds = 0, written = 0, failed = 0;
        string? firstError = null;
        string? summary = null;

        try
        {
            try
            {
            foreach (WorldRow row in _selectedWorlds.ToList())
            {
                byte[]? image = BuildRevealedMap(row, out string? error);
                if (image is null)
                {
                    failed++;
                    firstError ??= error;
                    continue;
                }

                worlds++;
                foreach (string characterDir in Directory.GetDirectories(_folder!.PlayersDir))
                {
                    string target = Path.Combine(characterDir, row.MapGuid + ".map");
                    // only refresh maps that already exist: a batch should never invent new ones
                    if (!File.Exists(target)) continue;

                    try
                    {
                        RotateBackups(target);
                        string temp = target + ".tmp";
                        File.WriteAllBytes(temp, image);
                        File.Move(temp, target, overwrite: true);
                        _lastTargets.Add(target);
                        written++;
                    }
                    catch (Exception ex)
                    {
                        failed++;
                        firstError ??= ex.Message;
                    }
                }
            }

            summary = string.Format(Str("Worlds.BatchDone"), worlds, written, failed)
                + (firstError is null ? string.Empty : $"（{firstError}）");
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.ToString(), "batch diag", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }
        finally
        {
            foreach (WorldRow row in _selectedWorlds) row.Selected = false;
            _selectedWorlds.Clear();
            UndoButton.Visibility = _lastTargets.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
            ApplyFilter();
            // ApplyFilter rewrites the status line, so show the batch result last
            if (summary is not null) StatusText.Text = summary;
        }
    }

    private void OnWorldCardClick(object sender, MouseButtonEventArgs e)
    {
        if ((sender as FrameworkElement)?.DataContext is not WorldRow world)
        {
            return;
        }

        ShowCharacters(world);
    }

    // ── level 2: characters carrying a map for this world ─────────────────────

    private void ShowCharacters(WorldRow world)
    {
        _world = world;
        CharacterWorldName.Text = world.Name;
        var rows = new List<CharacterRow>();

        if (_folder is not null && Directory.Exists(_folder.PlayersDir))
        {
            foreach (string plr in Directory.EnumerateFiles(_folder.PlayersDir, "*.plr").OrderBy(f => f))
            {
                string character = Path.GetFileNameWithoutExtension(plr);
                string map = Path.Combine(_folder.PlayersDir, character, world.MapGuid + ".map");
                bool exists = File.Exists(map);
                string state = exists
                    ? string.Format(Str("Chars.WithMap"), new FileInfo(map).Length / 1024.0)
                    : Str("Chars.NoMap");
                rows.Add(new CharacterRow(character, state, world.MapGuid + ".map"));
            }
        }

        _picked.Clear();
        CharacterList.ItemsSource = rows;
        PickButton.IsEnabled = false;
        UpdateCharacterSelection();
        ShowPage(1);
    }


    /// <summary>Selection counter in the character footer; also drives the pick button.</summary>
    private void UpdateCharacterSelection()
    {
        CharacterSelection.Text = _picked.Count == 0
            ? string.Empty
            : string.Format(Str("Chars.Selected"), _picked.Count);
    }

    // ── backups ───────────────────────────────────────────────────────────────

    private void ReloadBackups()
    {
        var rows = new List<BackupRow>();
        _folder ??= SaveDiscovery.Locate();

        if (_folder is null)
        {
            BackupStatus.Text = Str("Worlds.Missing");
            BackupList.ItemsSource = null;
            return;
        }

        try
        {
            foreach (string characterDir in Directory.EnumerateDirectories(_folder.PlayersDir))
            {
                string character = Path.GetFileName(characterDir);
                foreach (string file in Directory.EnumerateFiles(characterDir, "*.map.bak*")
                             .OrderByDescending(File.GetLastWriteTime))
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
                    string kind = mapName.EndsWith(".bak", StringComparison.OrdinalIgnoreCase)
                        ? Str("Backups.Latest")
                        : mapName[(mapName.LastIndexOf(".bak", StringComparison.OrdinalIgnoreCase) + 4)..];
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
        BackupStatus.Text = rows.Count == 0 ? Str("Backups.Empty") : string.Format(Str("Backups.Count"), rows.Count);
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

    /// <summary>Takes back the last reveal: the pre-reveal map comes back out of .bak.</summary>
    private void OnUndoClick(object sender, RoutedEventArgs e)
    {
        if (_lastTargets.Count == 0)
        {
            return;
        }

        int restored = 0;
        try
        {
            foreach (string target in _lastTargets)
            {
                UnrotateBackups(target);
                restored++;
            }
        }
        catch (Exception ex)
        {
            StatusText.Text = $"撤销失败：{ex.Message}";
            return;
        }
        finally
        {
            _lastTargets.Clear();
            UndoButton.Visibility = Visibility.Collapsed;
        }

        StatusText.Text = string.Format(Str("Chars.Undone"), restored);
        if (_world is not null)
        {
            ShowCharacters(_world);
        }
    }

    /// <summary>Inverse of <see cref="RotateBackups"/>; deletes the map when it was the first write.</summary>
    private static void UnrotateBackups(string mapPath)
    {
        string bak = mapPath + ".bak";
        string bak1 = bak + ".1";
        string bak2 = bak + ".2";

        if (File.Exists(mapPath))
        {
            File.Delete(mapPath);
        }

        if (File.Exists(bak))
        {
            File.Move(bak, mapPath);
        }

        if (File.Exists(bak1))
        {
            File.Move(bak1, bak);
        }

        if (File.Exists(bak2))
        {
            File.Move(bak2, bak1);
        }
    }

    /// <summary>Drops the map file itself so the game regenerates a fully unexplored one.</summary>
    private void OnDeleteMap(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is not BackupRow row)
        {
            return;
        }

        try
        {
            if (File.Exists(row.Target))
            {
                File.Delete(row.Target);
            }

            BackupStatus.Text = string.Format(Str("Backups.MapDeleted"), Path.GetFileName(row.Target));
            ReloadBackups();
        }
        catch (Exception ex)
        {
            BackupStatus.Text = $"删除地图失败：{ex.Message}";
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

        if (File.Exists(mapPath))
        {
            File.Move(mapPath, bak);
        }
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

    /// <summary>Applied by <see cref="PreviewRenderer"/> when the preview tiles are rendered.</summary>
    public void SetPreviewTheme(string theme)
    {
        ApplyTheme(theme, save: false);
    }

    private void ApplyTheme(string theme, bool save)
    {
        _theme = theme;
        bool dark = theme switch
        {
            "dark" => true,
            "light" => false,
            _ => IsSystemDark(),
        };

        var palette = dark
            ? (_darkVariant == "oled" ? OledPalette : DarkPalette)
            : LightPalette;

        foreach ((string key, string colour) in palette)
        {
            var colourValue = (Color)ColorConverter.ConvertFromString(colour);
            Application.Current.Resources[key] = new SolidColorBrush(colourValue);
        }

        if (save)
        {
            SaveConfig();
        }

        var active = (Style)FindResource("ThemeChoiceActive");
        var idle = (Style)FindResource("ThemeChoice");
        ThemeSystem.Style = theme == "system" ? active : idle;
        ThemeLight.Style = theme == "light" ? active : idle;
        ThemeDark.Style = theme == "dark" ? active : idle;

        var filled = (Style)FindResource("PillButton");
        var outlined = (Style)FindResource("PillButtonOutline");
        DarkStandard.Style = _darkVariant == "standard" ? filled : outlined;
        DarkOled.Style = _darkVariant == "oled" ? filled : outlined;
        DarkStandard.IsEnabled = dark;
        DarkOled.IsEnabled = dark;
    }

    private void OnDarkVariantClick(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is not string variant || variant == _darkVariant)
        {
            return;
        }

        _darkVariant = variant;
        ApplyTheme(_theme, save: true);
    }

    private void OnLanguageChanged(object sender, SelectionChangedEventArgs e)
    {
        if ((LanguageBox.SelectedItem as ComboBoxItem)?.Tag is not string code || code == _language)
        {
            return;
        }

        ApplyLanguage(code, save: true);
    }

    private void ApplyLanguage(string code, bool save)
    {
        _language = code;

        var dictionary = new ResourceDictionary
        {
            Source = new Uri($"Strings/{code}.xaml", UriKind.Relative),
        };

        // Replace the previous language dictionary instead of stacking them: WPF keeps the
        // *last* merged dictionary first, so stale entries would win otherwise.
        for (int i = Application.Current.Resources.MergedDictionaries.Count - 1; i >= 0; i--)
        {
            Uri? source = Application.Current.Resources.MergedDictionaries[i].Source;
            if (source is not null && source.OriginalString.Contains("Strings/", StringComparison.OrdinalIgnoreCase))
            {
                Application.Current.Resources.MergedDictionaries.RemoveAt(i);
            }
        }

        Application.Current.Resources.MergedDictionaries.Add(dictionary);

        if (save)
        {
            SaveConfig();
        }

        UpdateMaximizeGlyph();
    }

    private void SelectLanguageItem(string code)
    {
        foreach (object item in LanguageBox.Items)
        {
            if (item is ComboBoxItem combo && (combo.Tag as string) == code)
            {
                LanguageBox.SelectedItem = combo;
                return;
            }
        }

        LanguageBox.SelectedIndex = 0;
    }

    private void SaveConfig() => DesktopConfig.Save(_theme, _darkVariant, _language);

    private static string Str(string key)
        => Application.Current.TryFindResource(key) as string ?? key;

    private void OnOpenGithub(object sender, RoutedEventArgs e) => Open(GithubUrl);

    private void OnOpenChangelog(object sender, RoutedEventArgs e)
    {
        Open("https://github.com/Dragrowl/TerraXaleid/blob/main/CHANGELOG.md");
    }

    private void OnOpenBilibili(object sender, RoutedEventArgs e) => Open(BilibiliUrl);

    private void Open(string url)
    {
        try
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            MessageBox.Show(ex.Message, "TerraXaleid");
        }
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
        ("App.DangerSoft", "#FDE8E6"),
        ("App.Danger", "#B3261E"),
        ("App.ScrollThumb", "#CFCFC7"),
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
        ("App.DangerSoft", "#3A1D1D"),
        ("App.Danger", "#F2B8B5"),
        ("App.ScrollThumb", "#3A3A38"),
    };

    /// <summary>True black so OLED panels switch pixels off; surfaces lift just enough to read.</summary>
    private static readonly (string Key, string Colour)[] OledPalette =
    {
        ("App.Background", "#000000"),
        ("App.Surface", "#0B0B0B"),
        ("App.Rail", "#050505"),
        ("App.Border", "#1A1A1A"),
        ("App.Chip", "#141414"),
        ("App.Text", "#E8E8E6"),
        ("App.TextSoft", "#9A9A95"),
        ("App.Accent", "#A9C3A9"),
        ("App.AccentSoft", "#1C2A1E"),
        ("App.DangerSoft", "#2A1414"),
        ("App.Danger", "#F2B8B5"),
        ("App.ScrollThumb", "#2A2A28"),
    };

    private void OnCharacterCardClick(object sender, MouseButtonEventArgs e)
    {
        if (sender is not FrameworkElement { DataContext: CharacterRow row }) return;
        row.Selected = !row.Selected;
        if (row.Selected) _picked.Add(row.Name);
        else _picked.Remove(row.Name);
        PickButton.IsEnabled = _picked.Count > 0;
        UpdateCharacterSelection();
    }

    private void OnPickClick(object sender, RoutedEventArgs e)
    {
        if (_world is null || _folder is null || _picked.Count == 0) return;
        int written = 0, failed = 0;
        string? firstError = null;

        try
        {
            byte[]? image = BuildRevealedMap(_world, out string? buildError);
            if (image is null)
            {
                MessageBox.Show(this, buildError ?? "verify failed", "TerraXaleid", MessageBoxButton.OK, MessageBoxImage.Error);
                return;
            }

            string mapName = _world.MapGuid + ".map";
            _lastTargets.Clear();

            foreach (string character in _picked)
            {
                try
                {
                    string dir = Path.Combine(_folder.PlayersDir, character);
                    Directory.CreateDirectory(dir);
                    string target = Path.Combine(dir, mapName);
                    RotateBackups(target);
                    string tmp = target + ".tmp";
                    File.WriteAllBytes(tmp, image);
                    File.Move(tmp, target, overwrite: true);
                    _lastTargets.Add(target);
                    written++;
                }
                catch (Exception ex)
                {
                    failed++;
                    firstError ??= ex.Message;
                }
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, ex.Message, "TerraXaleid", MessageBoxButton.OK, MessageBoxImage.Error);
            return;
        }

        var current = _world;
        _picked.Clear();
        ShowCharacters(current);
        UndoButton.Visibility = _lastTargets.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        StatusText.Text = failed == 0
            ? string.Format(Str("Chars.Written"), written)
            : string.Format(Str("Chars.WrittenFailed"), written, failed) + (firstError is null ? string.Empty : " · " + firstError);
    }
    /// <summary>Encodes one world into a fresh PC map image, or null with a reason.</summary>
    private byte[]? BuildRevealedMap(WorldRow row, out string? error)
    {
        error = null;
        try
        {
            string worldPath = Path.Combine(_folder!.WorldsDir, row.Guid + ".wld");
            byte[] bytes = File.ReadAllBytes(worldPath);
            WorldMeta meta = WorldFile.ParseMeta(bytes);
            World world = WorldFile.Load(bytes);
            TileOptions options = TileOptions.FromFirstReadable(
                    Directory.GetFiles(_folder.PlayersDir, "*.map", SearchOption.AllDirectories), out _)
                ?? new TileOptions(new int[754], new int[367]);

            using var buffer = new MemoryStream();
            PcMapFormat.Write(buffer, options, world.Name, world.WorldId, world.Width, world.Height, 754, 367,
                world.Tiles, world.Walls, world.LiquidAmounts, world.LiquidKinds,
                (float)world.Surface, (float)world.RockLayer, world.UnderworldLayer);
            byte[] image = buffer.ToArray();

            if (!PcMapFormat.TryVerify(image, world.Width, world.Height, out _, out string? verifyError))
            {
                error = verifyError ?? "verify failed";
                return null;
            }

            return image;
        }
        catch (Exception ex)
        {
            error = ex.Message;
            return null;
        }
    }
}
/// <summary>Tiny json config in %APPDATA%\TerraXaleid\config.json (theme, dark variant, language).</summary>
internal sealed record DesktopConfig(string Theme, string DarkVariant, string Language)
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
                string theme = Read(doc.RootElement, "theme") ?? "system";
                string darkVariant = Read(doc.RootElement, "darkVariant") ?? "standard";
                string language = Read(doc.RootElement, "language") ?? "sc";
                return new DesktopConfig(theme, darkVariant, language);
            }
        }
        catch
        {
            // A broken config must never stop the app from starting.
        }

        return new DesktopConfig("system", "standard", "sc");
    }

    private static string? Read(JsonElement root, string name)
        => root.TryGetProperty(name, out JsonElement value) && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

    public static void Save(string theme, string darkVariant, string language)
    {
        try
        {
            string? dir = Path.GetDirectoryName(FilePath);
            if (dir is not null)
            {
                Directory.CreateDirectory(dir);
            }

            var payload = new Dictionary<string, string>
            {
                ["theme"] = theme,
                ["darkVariant"] = darkVariant,
                ["language"] = language,
            };
            File.WriteAllText(FilePath,
                JsonSerializer.Serialize(payload, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch
        {
            // Ignore: the settings simply will not persist.
        }
    }
}

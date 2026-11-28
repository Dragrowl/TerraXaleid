// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.ComponentModel;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Reflection;
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

        public CharacterRow(string name, string detail, string guid, string? mapPath = null)
        {
            Name = name;
            Detail = detail;
            Guid = guid;
            MapPath = mapPath;
        }

        public string? MapPath { get; }

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

    /// <summary>Column count for the world grid; the cards then stretch to fill the row.</summary>
    public static readonly DependencyProperty WorldColumnsProperty = DependencyProperty.Register(
        nameof(WorldColumns), typeof(int), typeof(MainWindow), new PropertyMetadata(2));

    public int WorldColumns
    {
        get => (int)GetValue(WorldColumnsProperty);
        set => SetValue(WorldColumnsProperty, value);
    }

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
        SourceInitialized += (_, _) => RoundCorners();

        DesktopConfig config = DesktopConfig.Load();
        _theme = config.Theme;
        _darkVariant = config.DarkVariant;
        _language = config.Language;

        ApplyLanguage(_language, save: false);
        LoadFontChoices();
        ApplyFontFamily(config.FontFamily);
        UpdateFontLabel();
        RefreshAboutTexts();
        ApplyTheme(_theme, save: false);
        UpdateLanguageLabel();
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

        // cards stretch to fill the row, so the column count follows the available width
        WorldList.SizeChanged += (_, _) => UpdateWorldColumns();
        UpdateWorldColumns();
    }

    /// <summary>Roughly 300 px per card: as many whole cards as fit, then they share the width.</summary>
    private void UpdateWorldColumns()
    {
        double available = WorldList.ActualWidth;
        if (available <= 0) available = ActualWidth - 96;
        int columns = (int)Math.Floor((available - 24) / 300);
        WorldColumns = Math.Clamp(columns, 1, 6);
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
    // A WindowStyle=None window is drawn with square corners; DWM is the only thing that can
    // round the frame, so ask it to. Windows 10 ignores the attribute and stays square, which
    // is what it looks like natively anyway.
    private void RoundCorners()
    {
        IntPtr handle = new System.Windows.Interop.WindowInteropHelper(this).Handle;
        if (handle == IntPtr.Zero)
        {
            return;
        }

        int preference = 2; // DWMWCP_ROUND
        _ = DwmSetWindowAttribute(handle, 33 /* DWMWA_WINDOW_CORNER_PREFERENCE */, ref preference, sizeof(int));
    }

    [System.Runtime.InteropServices.DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);
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

        ShowPage(_page);
        StatusText.Text = string.Format(Str("Worlds.Refreshed"), DateTime.Now.ToString("HH:mm:ss"));
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

    private void OnSearchKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape && SearchBox.Text.Length > 0)
        {
            SearchBox.Clear();
            e.Handled = true;
        }
    }

    private void ApplyFilter()
    {
        string query = SearchBox.Text?.Trim() ?? string.Empty;
        List<WorldRow> rows = query.Length == 0
            ? _all
            : _all.Where(w => w.Name.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();
        WorldList.ItemsSource = rows;
        WorldEmpty.Visibility = rows.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

        if (_folder is not null)
        {
            StatusText.Text = query.Length == 0
                ? string.Format(Str("Worlds.Count"), _folder.Root, _all.Count)
                : string.Format(Str("Worlds.Match"), rows.Count, _all.Count);
        }

        if (IsLoaded)
        {
            UpdateBatchHint();
        }
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
        UpdateBatchHint();
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
                row.Selected = !row.Selected;
                UpdateBatchHint();
                e.Handled = true;
                break;
            case Key.Enter:
                ShowCharacters(row);
                e.Handled = true;
                break;
        }
    }

    /// <summary>Rows currently ticked for the batch action; the row owns the state.</summary>
    private List<WorldRow> SelectedRows() => _all.Where(r => r.Selected).ToList();

    private void UpdateBatchHint()
    {
        int count = SelectedRows().Count;
        // the batch action runs on Ctrl+B; the status line keeps it discoverable
        StatusText.Text = count == 0
            ? (_folder is null ? string.Empty : string.Format(Str("Worlds.Count"), _folder.Root, _all.Count))
            : string.Format(Str("Worlds.BatchHint"), count);
    }

    private void OnBatchPickClick(object sender, RoutedEventArgs e)
    {
        if (!_all.Any(r => r.Selected)) return;

        _lastTargets.Clear();
        int worlds = 0, written = 0, failed = 0;
        string? firstError = null;
        string? summary = null;

        try
        {
            try
            {
            foreach (WorldRow row in SelectedRows())
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
                summary = ex.Message;
            }
        }
        finally
        {
            foreach (WorldRow row in SelectedRows()) row.Selected = false;
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
                rows.Add(new CharacterRow(character, state, world.MapGuid + ".map", exists ? map : null));
            }
        }

        _picked.Clear();
        CharacterList.ItemsSource = rows;

        string? template = rows.FirstOrDefault(r => r.MapPath is not null)?.Name;
        CharacterTemplate.Text = template is null
            ? Str("Chars.NoTemplate")
            : string.Format(Str("Chars.Template"), template);

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
        BackupEmpty.Visibility = rows.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
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
    private void OnCharacterMenuUndo(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is not CharacterRow row || _world is null) return;
        if (row.MapPath is null || !File.Exists(row.MapPath))
        {
            StatusText.Text = Str("Chars.NoMap");
            return;
        }

        try
        {
            UnrotateBackups(row.MapPath);
            StatusText.Text = string.Format(Str("Chars.Undone"), 1);
            ShowCharacters(_world);
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, ex.Message, Str("Chars.Undo"), MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

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

    /// <summary>True once the first palette has been applied; later switches cross-fade.</summary>
    private bool _paletteReady;

    /// <summary>Set while the preview renderer drives the window; the cross-fade would be captured.</summary>
    internal bool Previewing;

    /// <summary>Replaces every palette brush. Colours are applied at once; the cross-fade is drawn
    /// by <see cref="FadeThemeChange"/> because brushes inserted into a resource dictionary get frozen.</summary>
    private static void ApplyPalette((string Key, string Colour)[] palette)
    {
        foreach ((string key, string colour) in palette)
        {
            var solid = new SolidColorBrush((Color)ColorConverter.ConvertFromString(colour));
            solid.Freeze();
            Application.Current.Resources[key] = solid;
        }
    }

    /// <summary>Paints the outgoing background over everything and fades it out, so a theme swap reads
    /// as a cross-fade instead of a hard cut.</summary>
    private void FadeThemeChange(Color outgoing)
    {
        if (!_paletteReady || Previewing)
        {
            return;
        }

        ThemeFade.Background = new SolidColorBrush(outgoing);
        ThemeFade.Opacity = 1;

        var fade = new DoubleAnimation(1, 0, new Duration(TimeSpan.FromMilliseconds(220)))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
        };
        fade.Completed += (_, _) =>
        {
            ThemeFade.Opacity = 0;
            ThemeFade.Background = Brushes.Transparent;
        };
        ThemeFade.BeginAnimation(OpacityProperty, fade);
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

        Color outgoing = Application.Current.Resources["App.Background"] is SolidColorBrush previousBackground
            ? previousBackground.Color
            : Colors.Transparent;

        ApplyPalette(palette);
        FadeThemeChange(outgoing);
        _paletteReady = true;

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
        // in light theme the variant switch is inert: keep both buttons as outlines so
        // the disabled pair reads as a single muted control instead of a dead filled pill.
        DarkStandard.Style = dark && _darkVariant == "standard" ? filled : outlined;
        DarkOled.Style = dark && _darkVariant == "oled" ? filled : outlined;
        // the OLED switch only exists while a dark theme is selected
        VariantRow.Visibility = dark ? Visibility.Visible : Visibility.Collapsed;
    }

    private void OnVariantClick(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is not string variant || variant == _darkVariant)
        {
            return;
        }

        _darkVariant = variant;
        ApplyTheme(_theme, save: true);
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
        RefreshAboutTexts();
        UpdateLanguageLabel();
        LoadFontChoices();
        UpdateFontLabel();
    }
    private static readonly (string Code, string Label)[] LanguageOptions =
    {
        ("sc", "简体中文"),
        ("tc", "繁體中文"),
        ("ja", "日本語"),
        ("en", "English"),
    };

    private void OnLanguageClick(object sender, RoutedEventArgs e)
    {
        BuildLanguageMenu();
        LanguagePopup.IsOpen = !LanguagePopup.IsOpen;
    }

    private void BuildLanguageMenu()
    {
        LanguageItems.Children.Clear();
        foreach ((string code, string label) in LanguageOptions)
        {
            LanguageItems.Children.Add(CreateMenuItem(label, code, OnLanguagePick, code == _language));
        }
    }

    private void OnLanguagePick(object sender, RoutedEventArgs e)
    {
        LanguagePopup.IsOpen = false;
        if ((sender as Button)?.Tag is not string code || code == _language)
        {
            return;
        }

        ApplyLanguage(code, save: true);
        UpdateLanguageLabel();
    }

    private void UpdateLanguageLabel()
    {
        LanguageLabel.Text = LanguageOptions.FirstOrDefault(o => o.Code == _language).Label ?? "简体中文";
    }
    private void SaveConfig() => DesktopConfig.Save(_theme, _darkVariant, _language, _fontFamily);

    private static string Str(string key)
        => Application.Current.TryFindResource(key) as string ?? key;

    private void OnOpenGithub(object sender, RoutedEventArgs e) => Open(GithubUrl);

    private void OnOpenRelease(object sender, RoutedEventArgs e)
    {
        Open("https://github.com/Dragrowl/TerraXaleid/releases/latest");
    }

    private void OnOpenChangelog(object sender, RoutedEventArgs e)
    {
        Open("https://github.com/Dragrowl/TerraXaleid/blob/main/CHANGELOG.md");
    }

    private void OnOpenBilibili(object sender, RoutedEventArgs e) => Open(BilibiliUrl);

    // ── font ──────────────────────────────────────────────────────────────────

    private string _fontFamily = string.Empty;

    private readonly List<(string Tag, string Label)> _fontChoices = new();

    private void LoadFontChoices()
    {
        _fontChoices.Clear();
        _fontChoices.Add((string.Empty, Str("Settings.Font.Default")));
        foreach (FontFamily family in Fonts.SystemFontFamilies.OrderBy(f => f.Source, StringComparer.CurrentCulture))
        {
            _fontChoices.Add((family.Source, family.Source));
        }
    }

    private void OnFontClick(object sender, RoutedEventArgs e)
    {
        BuildFontMenu();
        FontPopup.IsOpen = !FontPopup.IsOpen;
    }

    private void BuildFontMenu()
    {
        FontItems.Children.Clear();
        foreach ((string tag, string label) in _fontChoices)
        {
            FontItems.Children.Add(CreateMenuItem(label, tag, OnFontPick, string.Equals(tag, _fontFamily, StringComparison.OrdinalIgnoreCase)));
        }
    }

    private void OnFontPick(object sender, RoutedEventArgs e)
    {
        FontPopup.IsOpen = false;
        if ((sender as Button)?.Tag is not string family)
        {
            return;
        }

        ApplyFontFamily(family);
        UpdateFontLabel();
        SaveConfig();
    }

    private void UpdateFontLabel()
    {
        string label = _fontChoices.FirstOrDefault(c => string.Equals(c.Tag, _fontFamily, StringComparison.OrdinalIgnoreCase)).Label;
        FontLabel.Text = string.IsNullOrEmpty(label) ? Str("Settings.Font.Default") : label;
    }

    private void ApplyFontFamily(string family)
    {
        _fontFamily = family;
        if (string.IsNullOrWhiteSpace(family))
        {
            Application.Current.Resources.Remove("App.FontFamily");
            return;
        }

        Application.Current.Resources["App.FontFamily"] = new FontFamily(family);
    }

    private Button CreateMenuItem(string label, string tag, RoutedEventHandler handler, bool selected)
    {
        var button = new Button
        {
            Content = label,
            Tag = tag,
            Style = (Style)FindResource(selected ? "MenuItemSelected" : "MenuItem"),
        };
        button.Click += handler;
        return button;
    }
    // ── about + update ────────────────────────────────────────────────────────

    private static string CurrentVersion
    {
        get
        {
            var informational = (AssemblyInformationalVersionAttribute?)Attribute.GetCustomAttribute(
                Assembly.GetExecutingAssembly(), typeof(AssemblyInformationalVersionAttribute));
            string raw = informational?.InformationalVersion
                ?? Assembly.GetExecutingAssembly().GetName().Version?.ToString(3)
                ?? "0.0.0";
            int plus = raw.IndexOf('+');
            return plus > 0 ? raw[..plus] : raw;
        }
    }

    private void RefreshAboutTexts()
    {
        AboutVersion.Text = string.Format(Str("Settings.About.Version"), CurrentVersion);
        UpdateCurrent.Text = string.Format(Str("Settings.Update.Current"), CurrentVersion);
    }

    private async void OnCheckUpdate(object sender, RoutedEventArgs e)
    {
        CheckUpdateButton.IsEnabled = false;
        string original = CheckUpdateButton.Content as string ?? string.Empty;
        CheckUpdateButton.Content = Str("Settings.Update.Checking");

        try
        {
            using var http = new HttpClient();
            http.Timeout = TimeSpan.FromSeconds(8);
            http.DefaultRequestHeaders.UserAgent.ParseAdd("TerraXaleid");
            http.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");

            string json;
            try
            {
                json = await http.GetStringAsync(
                    "https://api.github.com/repos/Dragrowl/TerraXaleid/releases/latest");
            }
            catch (HttpRequestException notFound) when (notFound.StatusCode == System.Net.HttpStatusCode.NotFound)
            {
                MessageBox.Show(this, Str("Settings.Update.NoRelease"),
                    Str("Settings.Update"), MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }
            using JsonDocument document = JsonDocument.Parse(json);
            string tag = document.RootElement.TryGetProperty("tag_name", out JsonElement node)
                ? node.GetString() ?? string.Empty
                : string.Empty;
            string latest = tag.StartsWith("v", StringComparison.OrdinalIgnoreCase) ? tag[1..] : tag;

            if (string.IsNullOrEmpty(latest))
            {
                MessageBox.Show(this, string.Format(Str("Settings.Update.Failed"), "no tag_name"),
                    Str("Settings.Update"), MessageBoxButton.OK, MessageBoxImage.Warning);
            }
            else if (string.Equals(latest, CurrentVersion, StringComparison.OrdinalIgnoreCase))
            {
                MessageBox.Show(this, string.Format(Str("Settings.Update.Latest"), CurrentVersion),
                    Str("Settings.Update"), MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else
            {
                MessageBoxResult answer = MessageBox.Show(this,
                    string.Format(Str("Settings.Update.Available"), latest, CurrentVersion),
                    Str("Settings.Update"), MessageBoxButton.YesNo, MessageBoxImage.Information);
                if (answer == MessageBoxResult.Yes)
                {
                    OnOpenRelease(this, new RoutedEventArgs());
                }
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, string.Format(Str("Settings.Update.Failed"), ex.Message),
                Str("Settings.Update"), MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        finally
        {
            CheckUpdateButton.Content = original;
            CheckUpdateButton.IsEnabled = true;
        }
    }

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
        ("App.Border", "#DCDCD6"),
        ("App.Chip", "#EFEFEA"),
            ("App.Hover", "#E7E7E2"),
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
            ("App.Hover", "#1F1F1D"),
        ("App.Text", "#E8E8E6"),
        ("App.TextSoft", "#B4B4AE"),
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
        ("App.Border", "#242422"),
        ("App.Chip", "#141414"),
            ("App.Hover", "#171715"),
        ("App.Text", "#E8E8E6"),
        ("App.TextSoft", "#B4B4AE"),
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
internal sealed record DesktopConfig(string Theme, string DarkVariant, string Language, string FontFamily = "")
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
                string fontFamily = Read(doc.RootElement, "fontFamily") ?? string.Empty;
                return new DesktopConfig(theme, darkVariant, language, fontFamily);
            }
        }
        catch
        {
            // A broken config must never stop the app from starting.
        }

        return new DesktopConfig("system", "standard", "sc", string.Empty);
    }

    private static string? Read(JsonElement root, string name)
        => root.TryGetProperty(name, out JsonElement value) && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

    public static void Save(string theme, string darkVariant, string language, string fontFamily)
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
                ["fontFamily"] = fontFamily,
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

// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using TerraXaleid.Core;

namespace TerraXaleid.App;

/// <summary>
/// First Windows shell. It only reads the save folder and shows what is there:
/// the map writer is the same <see cref="TerraXaleid.Core"/> code the CLI uses, so wiring the
/// reveal button is a later step, not a rewrite.
/// </summary>
public partial class MainWindow : Window
{
    private sealed record WorldRow(string Name, string Difficulty, string Size, string Evil, string Progress, string Path);

    private List<WorldRow> _all = new();

    public MainWindow()
    {
        InitializeComponent();
        Reload();
    }

    private void OnRefresh(object sender, RoutedEventArgs e) => Reload();

    private void OnSearchChanged(object sender, TextChangedEventArgs e) => ApplyFilter();

    private void Reload()
    {
        _all = new List<WorldRow>();

        SaveFolder? folder = SaveDiscovery.Locate();
        if (folder is null)
        {
            StatusText.Text = "找不到存档目录（%USERPROFILE%\\Documents\\My Games\\Terraria）。";
            WorldList.ItemsSource = null;
            return;
        }

        foreach (string file in Directory.EnumerateFiles(folder.WorldsDir, "*.wld").OrderBy(f => f))
        {
            try
            {
                WorldMeta meta = WorldFile.ParseMeta(File.ReadAllBytes(file));
                string progress = meta.HardMode
                    ? (meta.DefeatedMoonLord ? "已击败月亮领主" : "困难模式")
                    : "困难模式之前";

                _all.Add(new WorldRow(meta.Name, meta.Difficulty, meta.Size, meta.Evil, progress, file));
            }
            catch (Exception ex)
            {
                StatusText.Text = $"跳过 {Path.GetFileName(file)}：{ex.Message}";
            }
        }

        StatusText.Text = $"存档目录：{folder.Root} · 世界 {_all.Count} 个";
        ApplyFilter();
    }

    private void ApplyFilter()
    {
        string query = SearchBox.Text?.Trim() ?? string.Empty;
        WorldList.ItemsSource = query.Length == 0
            ? _all
            : _all.Where(w => w.Name.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();
    }
}

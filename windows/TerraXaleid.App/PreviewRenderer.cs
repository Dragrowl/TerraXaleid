// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Collections.Generic;
using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

namespace TerraXaleid.App;

/// <summary>
/// Renders the appearance previews with this very window: the phone thumbnails cannot be
/// reused, the desktop shell has a different layout. Text is drawn with the Sarkaz display
/// font so the three tiles read the same as the phone ones.
/// </summary>
internal static class PreviewRenderer
{
    private const int TileWidth = 720;
    private const int TileHeight = 480;

    public static void RenderAll(string fontPath, string outDir)
    {
        Directory.CreateDirectory(outDir);
        FontFamily? font = LoadFont(fontPath);

        InstallSarkazStrings();

        var tiles = new Dictionary<string, BitmapSource>(StringComparer.Ordinal);
        foreach (string theme in new[] { "light", "dark" })
        {
            tiles[theme] = RenderTheme(theme, font);
            Save(tiles[theme], Path.Combine(outDir, $"theme_preview_{theme}.png"));
        }

        Save(DiagonalSplit(tiles["light"], tiles["dark"]), Path.Combine(outDir, "theme_preview_auto.png"));
    }


    /// <summary>
    /// The phone thumbnails show every label in Sarkaz script. The display font only covers
    /// Latin, so each label is re-spelled into stable letter groups of a similar length before
    /// it is drawn, which reads the same at thumbnail size.
    /// </summary>
    private static void InstallSarkazStrings()
    {
        var dictionary = new ResourceDictionary
        {
            Source = new Uri("pack://application:,,,/Strings/sc.xaml", UriKind.Absolute),
        };

        foreach (object key in dictionary.Keys)
        {
            if (dictionary[key] is string text)
            {
                Application.Current.Resources[key] = Sarkazify(text);
            }
        }
    }

    private static string Sarkazify(string text)
    {
        const string Alphabet = "abcdefghijklmnoprstuvwyz";
        var builder = new System.Text.StringBuilder(text.Length * 2);
        for (int index = 0; index < text.Length; index++)
        {
            char c = text[index];

            // format placeholders such as {0} must survive untouched: Reload() passes them
            // through string.Format later on.
            if (c == '{')
            {
                int close = text.IndexOf('}', index);
                if (close < 0)
                {
                    builder.Append(c);
                    continue;
                }

                builder.Append(text, index, close - index + 1);
                index = close;
                continue;
            }

            if (c == ' ' || char.IsPunctuation(c) || char.IsWhiteSpace(c))
            {
                builder.Append(c);
                continue;
            }

            int code = c;
            builder.Append(Alphabet[(code * 7 + 3) % Alphabet.Length]);
            builder.Append(Alphabet[(code * 13 + 5) % Alphabet.Length]);
        }

        return builder.ToString();
    }

    private static FontFamily? LoadFont(string fontPath)
    {
        if (!File.Exists(fontPath))
        {
            return null;
        }

        string folder = Path.GetDirectoryName(fontPath) ?? ".";
        var families = Fonts.GetFontFamilies(new Uri(folder + Path.DirectorySeparatorChar));
        foreach (FontFamily family in families)
        {
            return family;
        }

        return null;
    }

    private static BitmapSource RenderTheme(string theme, FontFamily? font)
    {
        var window = new MainWindow
        {
            Width = TileWidth,
            Height = TileHeight,
            WindowStartupLocation = WindowStartupLocation.Manual,
            Left = -6000,
            Top = -6000,
            ShowInTaskbar = false,
        };

        if (font is not null)
        {
            window.FontFamily = font;
        }

        window.Show();
        window.Previewing = true;
        window.SetPreviewTheme(theme);
        window.UpdateLayout();
        Pump();

        // the phone tiles read as pure Sarkaz; spell every leftover literal (world names, chips)
        // into glyphs as well so the thumbnails stay consistent.
        SarkazifyVisualTree(window);
        window.UpdateLayout();
        Pump();

        var bitmap = new RenderTargetBitmap(TileWidth, TileHeight, 96, 96, PixelFormats.Pbgra32);
        bitmap.Render(window);
        bitmap.Freeze();
        window.Close();
        Pump();

        return bitmap;
    }

    /// <summary>The "system" tile is the usual diagonal blend of the light and dark halves.</summary>
    private static BitmapSource DiagonalSplit(BitmapSource light, BitmapSource dark)
    {
        int width = light.PixelWidth;
        int height = light.PixelHeight;
        int stride = width * 4;

        var lightPixels = new byte[stride * height];
        var darkPixels = new byte[stride * height];
        light.CopyPixels(lightPixels, stride, 0);
        dark.CopyPixels(darkPixels, stride, 0);

        var mixed = new byte[stride * height];

        // Corner to corner through the centre: light fills the upper-left half, dark the
        // lower-right half, so the seam runs from the bottom-left corner to the top-right one.
        for (int x = 0; x < width; x++)
        {
            int boundary = height - (int)Math.Round((double)height * x / width);
            for (int y = 0; y < height; y++)
            {
                int i = (y * stride) + (x * 4);
                byte[] source = y < boundary ? lightPixels : darkPixels;
                mixed[i] = source[i];
                mixed[i + 1] = source[i + 1];
                mixed[i + 2] = source[i + 2];
                mixed[i + 3] = source[i + 3];
            }
        }

        var result = BitmapSource.Create(width, height, 96, 96, PixelFormats.Pbgra32, null, mixed, stride);
        result.Freeze();
        return result;
    }

    private static void Save(BitmapSource bitmap, string path)
    {
        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using var stream = File.Create(path);
        encoder.Save(stream);
    }


    private static void SarkazifyVisualTree(DependencyObject node)
    {
        int count = VisualTreeHelper.GetChildrenCount(node);
        for (int index = 0; index < count; index++)
        {
            DependencyObject child = VisualTreeHelper.GetChild(node, index);
            if (child is System.Windows.Controls.TextBlock block && !string.IsNullOrEmpty(block.Text))
            {
                block.Text = Sarkazify(block.Text);
            }

            SarkazifyVisualTree(child);
        }
    }

    private static void Pump()
    {
        var frame = new DispatcherFrame();
        Dispatcher.CurrentDispatcher.BeginInvoke(
            DispatcherPriority.Background,
            new Action(() => frame.Continue = false));
        Dispatcher.PushFrame(frame);
    }
}
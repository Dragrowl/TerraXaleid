// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace TerraXaleid.App;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // A WPF shell that dies on startup would otherwise vanish without a trace,
        // which is exactly how the first MaterialDesign theme attempt went. Keep the
        // last failure on disk so it can be read after the fact.
        DispatcherUnhandledException += (_, args) =>
        {
            Log(args.Exception);
            MessageBox.Show(args.Exception.Message, "TerraXaleid", MessageBoxButton.OK, MessageBoxImage.Error);
            args.Handled = true;
        };

        // tools: regenerate Assets\theme_preview_*.png from this window, optionally with the
        // Sarkaz display font (kept outside the repository, see .gitignore).
        if (e.Args.Length >= 3 && e.Args[0] == "--render-previews")
        {
            try
            {
                // closing the first preview window would otherwise end the whole app
                ShutdownMode = ShutdownMode.OnExplicitShutdown;
                PreviewRenderer.RenderAll(e.Args[1], e.Args[2]);
                Environment.Exit(0);
            }
            catch (Exception ex)
            {
                Log(ex);
                Console.Error.WriteLine(ex);
                Environment.Exit(1);
            }
        }
    }

    private static void Log(Exception ex)
    {
        try
        {
            string path = Path.Combine(Path.GetTempPath(), "terraxaleid-app-crash.log");
            File.AppendAllText(path, $"[{DateTime.Now:O}] {ex}{Environment.NewLine}{Environment.NewLine}");
        }
        catch (IOException)
        {
            // Logging must never be the thing that crashes the app.
        }
    }
}

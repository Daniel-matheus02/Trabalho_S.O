// Duelo de Fichas - inicializador para Windows.
//
// Este fonte é compilado por tools/packaging/build_packages.py em um único .exe
// autônomo, com o payload do jogo (runtime Java embutido, jars do cliente e do
// servidor) como o recurso "payload.zip".
//
// Primeira execução: descompacta o payload em %LOCALAPPDATA%\DueloDeFichas\app e
// cria um atalho no Menu Iniciar. As execuções seguintes iniciam o jogo direto.
//
// Mantido compatível com C# 5 de propósito: é compilado com o csc.exe que vem com
// o .NET Framework, então não é necessário ter o SDK do .NET para gerar o executável.

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Windows.Forms;

internal static class DueloLauncher
{
    private const string AppName = "Duelo de Fichas";
    private const string GameVersion = "@GAME_VERSION@";
    private const string PayloadVersion = "@PAYLOAD_VERSION@";
    private const string PayloadResource = "payload.zip";
    private const string ClientJar = "game/duelo-de-fichas-" + GameVersion + ".jar";
    private const string ServerJar = "game/duelo-server-" + GameVersion + ".jar";

    [STAThread]
    private static int Main(string[] args)
    {
        Application.EnableVisualStyles();

        try
        {
            bool serverMode = false;
            bool installOnly = false;
            List<string> passthrough = new List<string>();
            foreach (string arg in args)
            {
                if (arg == "--server")
                {
                    serverMode = true;
                }
                else if (arg == "--install-only")
                {
                    installOnly = true;
                }
                else
                {
                    passthrough.Add(arg);
                }
            }

            string installRoot = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "DueloDeFichas");
            string appDir = Path.Combine(installRoot, "app");

            bool freshInstall = EnsureInstalled(installRoot, appDir);
            if (freshInstall)
            {
                TryCreateStartMenuShortcut();
            }

            if (installOnly)
            {
                Console.WriteLine("Instalado em " + appDir);
                return 0;
            }

            return serverMode ? StartServer(appDir, passthrough) : StartClient(appDir, passthrough);
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                    "Não foi possível iniciar o " + AppName + ".\r\n\r\n" + ex.Message,
                    AppName,
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            return 1;
        }
    }

    private static bool EnsureInstalled(string installRoot, string appDir)
    {
        string markerPath = Path.Combine(appDir, "payload.version");
        string installedVersion = "";
        try
        {
            if (File.Exists(markerPath))
            {
                installedVersion = File.ReadAllText(markerPath).Trim();
            }
        }
        catch (IOException)
        {
            installedVersion = "";
        }

        if (installedVersion == PayloadVersion)
        {
            return false;
        }

        Directory.CreateDirectory(installRoot);

        string staging = Path.Combine(installRoot, "app.staging");
        if (Directory.Exists(staging))
        {
            Directory.Delete(staging, true);
        }
        Directory.CreateDirectory(staging);

        string zipPath = Path.Combine(installRoot, "payload.zip");
        using (Stream source = Assembly.GetExecutingAssembly().GetManifestResourceStream(PayloadResource))
        {
            if (source == null)
            {
                throw new InvalidOperationException(
                        "O payload embutido do jogo está ausente. O executável está corrompido; baixe-o novamente.");
            }
            using (FileStream target = File.Create(zipPath))
            {
                source.CopyTo(target);
            }
        }

        string stagingRoot = Path.GetFullPath(staging);
        if (!stagingRoot.EndsWith(Path.DirectorySeparatorChar.ToString()))
        {
            stagingRoot += Path.DirectorySeparatorChar;
        }

        try
        {
            using (InstallProgress progress = new InstallProgress())
            using (ZipArchive archive = ZipFile.OpenRead(zipPath))
            {
                int total = archive.Entries.Count;
                int done = 0;
                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    string destination = Path.GetFullPath(Path.Combine(staging, entry.FullName));
                    if (!destination.StartsWith(stagingRoot, StringComparison.OrdinalIgnoreCase))
                    {
                        throw new IOException("Entrada de arquivo insegura bloqueada: " + entry.FullName);
                    }

                    if (entry.Name.Length == 0)
                    {
                        Directory.CreateDirectory(destination);
                    }
                    else
                    {
                        string parent = Path.GetDirectoryName(destination);
                        if (!string.IsNullOrEmpty(parent))
                        {
                            Directory.CreateDirectory(parent);
                        }
                        entry.ExtractToFile(destination, true);
                    }

                    done++;
                    progress.Report(done, total);
                }
            }
        }
        finally
        {
            TryDelete(zipPath);
        }

        File.WriteAllText(Path.Combine(staging, "payload.version"), PayloadVersion + Environment.NewLine);

        string previous = Path.Combine(installRoot, "app.previous");
        TryDeleteDirectory(previous);
        if (Directory.Exists(appDir))
        {
            Directory.Move(appDir, previous);
        }
        Directory.Move(staging, appDir);
        TryDeleteDirectory(previous);

        return true;
    }

    private static int StartClient(string appDir, List<string> args)
    {
        string java = FindJava(appDir, "javaw.exe");
        ProcessStartInfo startInfo = new ProcessStartInfo();
        startInfo.FileName = java;
        startInfo.Arguments = "-jar " + Quote(Path.Combine(appDir, ClientJar)) + JoinArguments(args);
        startInfo.WorkingDirectory = appDir;
        startInfo.UseShellExecute = false;

        Process process = Process.Start(startInfo);
        if (process == null)
        {
            throw new InvalidOperationException("O processo do jogo não foi iniciado.");
        }
        return 0;
    }

    private static int StartServer(string appDir, List<string> args)
    {
        string java = FindJava(appDir, "java.exe");
        string command = Quote(java) + " -jar " + Quote(Path.Combine(appDir, ServerJar)) + JoinArguments(args);

        // O servidor é uma aplicação de console; execute-o em uma janela de console
        // que permanece aberta para que o banner de endereços e os logs fiquem visíveis.
        ProcessStartInfo startInfo = new ProcessStartInfo();
        startInfo.FileName = "cmd.exe";
        startInfo.Arguments = "/k " + Quote(command);
        startInfo.WorkingDirectory = appDir;
        startInfo.UseShellExecute = true;

        Process process = Process.Start(startInfo);
        if (process == null)
        {
            throw new InvalidOperationException("O processo do servidor não foi iniciado.");
        }
        return 0;
    }

    private static string FindJava(string appDir, string executable)
    {
        string java = Path.Combine(appDir, Path.Combine("runtime", Path.Combine("bin", executable)));
        if (!File.Exists(java))
        {
            string fallback = Path.Combine(appDir, Path.Combine("runtime", Path.Combine("bin", "java.exe")));
            if (!File.Exists(fallback))
            {
                throw new FileNotFoundException("O runtime Java embutido está ausente: " + java);
            }
            java = fallback;
        }
        return java;
    }

    private static void TryCreateStartMenuShortcut()
    {
        try
        {
            string programs = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
            if (string.IsNullOrEmpty(programs))
            {
                return;
            }

            string linkPath = Path.Combine(programs, AppName + ".lnk");
            Type shellType = Type.GetTypeFromProgID("WScript.Shell");
            if (shellType == null)
            {
                return;
            }

            object shell = Activator.CreateInstance(shellType);
            object shortcut = shellType.InvokeMember(
                    "CreateShortcut",
                    BindingFlags.InvokeMethod,
                    null,
                    shell,
                    new object[] { linkPath });
            Type shortcutType = shortcut.GetType();
            string exePath = Application.ExecutablePath;

            shortcutType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, shortcut, new object[] { exePath });
            shortcutType.InvokeMember(
                    "WorkingDirectory",
                    BindingFlags.SetProperty,
                    null,
                    shortcut,
                    new object[] { Path.GetDirectoryName(exePath) });
            shortcutType.InvokeMember(
                    "IconLocation",
                    BindingFlags.SetProperty,
                    null,
                    shortcut,
                    new object[] { exePath + ",0" });
            shortcutType.InvokeMember(
                    "Description",
                    BindingFlags.SetProperty,
                    null,
                    shortcut,
                    new object[] { "Duelo de Fichas - duelo de cartas 1v1" });
            shortcutType.InvokeMember("Save", BindingFlags.InvokeMethod, null, shortcut, null);
        }
        catch (Exception)
        {
            // Um atalho ausente nunca deve impedir o jogo de iniciar.
        }
    }

    private static string Quote(string value)
    {
        return "\"" + value + "\"";
    }

    private static string JoinArguments(List<string> args)
    {
        if (args.Count == 0)
        {
            return "";
        }

        System.Text.StringBuilder builder = new System.Text.StringBuilder();
        foreach (string arg in args)
        {
            builder.Append(' ');
            if (arg.IndexOf(' ') >= 0 || arg.IndexOf('"') >= 0)
            {
                builder.Append(Quote(arg.Replace("\"", "\\\"")));
            }
            else
            {
                builder.Append(arg);
            }
        }
        return builder.ToString();
    }

    private static void TryDelete(string path)
    {
        try
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
        catch (IOException)
        {
        }
    }

    private static void TryDeleteDirectory(string path)
    {
        try
        {
            if (Directory.Exists(path))
            {
                Directory.Delete(path, true);
            }
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }

    /// <summary>Pequena janela sem bordas exibida enquanto o payload é descompactado.</summary>
    private sealed class InstallProgress : IDisposable
    {
        private readonly Form form;
        private readonly Label label;
        private readonly ProgressBar bar;
        private int lastPercent = -1;

        public InstallProgress()
        {
            form = new Form();
            form.Text = AppName;
            form.FormBorderStyle = FormBorderStyle.FixedDialog;
            form.StartPosition = FormStartPosition.CenterScreen;
            form.MinimizeBox = false;
            form.MaximizeBox = false;
            form.ControlBox = false;
            form.ShowInTaskbar = true;
            form.TopMost = true;
            form.ClientSize = new Size(420, 96);

            label = new Label();
            label.Text = "Instalando " + AppName + " (primeira execução)...";
            label.AutoSize = false;
            label.SetBounds(16, 16, 388, 20);
            label.TextAlign = ContentAlignment.MiddleLeft;

            bar = new ProgressBar();
            bar.SetBounds(16, 46, 388, 20);
            bar.Minimum = 0;
            bar.Maximum = 100;

            form.Controls.Add(label);
            form.Controls.Add(bar);
            form.Show();
            form.Refresh();
        }

        public void Report(int done, int total)
        {
            if (total <= 0)
            {
                return;
            }

            int percent = (int)((long)done * 100 / total);
            if (percent == lastPercent)
            {
                return;
            }

            lastPercent = percent;
            bar.Value = Math.Min(100, percent);
            label.Text = "Instalando " + AppName + " (" + percent + "%)";
            form.Refresh();
            Application.DoEvents();
        }

        public void Dispose()
        {
            form.Close();
            form.Dispose();
        }
    }
}

using System.Diagnostics;
using System.Text;

namespace UsbScreenClient;

internal sealed class AdbController
{
    private const int DevicePort = 27183;
    private readonly string _adbPath;

    public AdbController(string adbPath)
    {
        _adbPath = adbPath;
    }

    public static string? FindAdb()
    {
        IEnumerable<string> candidates = EnumerateCandidates();
        return candidates.FirstOrDefault(File.Exists);
    }

    public async Task EnsureForwardAsync(CancellationToken cancellationToken)
    {
        await RunAsync("start-server", cancellationToken);
        string devices = await RunAsync("devices", cancellationToken);
        bool hasDevice = devices
            .Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries)
            .Any(line => line.EndsWith("\tdevice", StringComparison.Ordinal));

        if (!hasDevice)
        {
            throw new InvalidOperationException("No authorized Android device found. Enable USB debugging and accept the authorization prompt.");
        }

        await RunAsync($"forward tcp:{DevicePort} tcp:{DevicePort}", cancellationToken);
        await RunAsync("shell am start -n com.example.usbscreenstream/.MainActivity", cancellationToken);
    }

    public async Task RemoveForwardAsync(CancellationToken cancellationToken)
    {
        try
        {
            await RunAsync($"forward --remove tcp:{DevicePort}", cancellationToken);
        }
        catch
        {
            // Removing an absent forward should not block disconnect.
        }
    }

    private async Task<string> RunAsync(string arguments, CancellationToken cancellationToken)
    {
        using Process process = new()
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = _adbPath,
                Arguments = arguments,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            }
        };

        process.Start();
        string stdout = await process.StandardOutput.ReadToEndAsync(cancellationToken);
        string stderr = await process.StandardError.ReadToEndAsync(cancellationToken);
        await process.WaitForExitAsync(cancellationToken);

        if (process.ExitCode != 0)
        {
            throw new InvalidOperationException(stderr.Length > 0 ? stderr.Trim() : stdout.Trim());
        }

        return stdout;
    }

    private static IEnumerable<string> EnumerateCandidates()
    {
        string[] pathEntries = (Environment.GetEnvironmentVariable("PATH") ?? "")
            .Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries);

        yield return Path.Combine(AppContext.BaseDirectory, "adb.exe");
        yield return Path.Combine(AppContext.BaseDirectory, "platform-tools", "adb.exe");

        foreach (string entry in pathEntries)
        {
            yield return Path.Combine(entry.Trim(), "adb.exe");
        }

        string? androidHome = Environment.GetEnvironmentVariable("ANDROID_HOME");
        string? androidSdkRoot = Environment.GetEnvironmentVariable("ANDROID_SDK_ROOT");
        string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);

        foreach (string? root in new[]
        {
            androidHome,
            androidSdkRoot,
            Path.Combine(localAppData, "Android", "Sdk")
        })
        {
            if (!string.IsNullOrWhiteSpace(root))
            {
                yield return Path.Combine(root, "platform-tools", "adb.exe");
            }
        }
    }
}

# Contributing

Thanks for helping improve USB Screen Stream.

## Development Setup

You need:

- Windows 10/11 x64.
- .NET SDK with Windows Desktop support.
- Android SDK build-tools/platforms.
- JDK 8+.
- Android platform-tools.
- An Android 10+ test device with USB debugging enabled.

Build Android:

```powershell
.\android\build-android.ps1
```

Build Windows:

```powershell
dotnet restore .\windows\UsbScreenClient\UsbScreenClient.csproj
dotnet build .\windows\UsbScreenClient\UsbScreenClient.csproj -c Release
```

## Pull Request Guidelines

- Keep changes focused.
- Do not commit build outputs, APKs, zip files, local SDK paths, private signing keys, logs, or screenshots with personal data.
- Preserve the low-latency behavior. Avoid adding frame queues or playback buffers unless they are optional and disabled by default.
- Update README and CHANGELOG when behavior or packaging changes.
- Include device and OS details when fixing Android vendor-specific behavior.

## Useful Test Checklist

- Android APK builds and installs.
- Windows client builds and starts.
- `adb devices` shows an authorized device.
- Android app requests screen capture permission.
- Windows `Connect` displays the phone screen.
- `Disconnect` removes the ADB forward and does not leave the UI stuck.
- Default settings stay low-latency: `360x800`, `600 kbps`, `30 FPS`.

## Reporting Bugs

Please include:

- Windows version.
- Android device model and Android version.
- Whether a privacy/multi-user mode is enabled.
- The selected resolution, bitrate/quality, and FPS.
- What the Android app shows.
- What the Windows client status says.
- Output from `scripts/diagnose-usb.ps1`.

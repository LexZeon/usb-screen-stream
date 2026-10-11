# USB Screen Stream

USB Screen Stream is a minimal, low-latency Android-to-Windows screen mirroring project over a USB cable. It is built for developers and users who need a simple wired screen preview without audio, remote control, accounts, cloud services, or wireless networking.

The current stable path uses Android `MediaProjection`, a foreground capture service, MJPEG frames over a local TCP socket, `adb forward` over USB, and a lightweight Windows WinForms viewer. It intentionally favors low latency and simple debugging over image quality.

## Features

- Wired Android screen mirroring to Windows over USB.
- Android screen capture through the official MediaProjection permission flow.
- Low-buffer MJPEG transport with length-prefixed JPEG frames.
- Windows client with Connect/Disconnect, resolution, bitrate/quality, FPS, status text, and preview window.
- Bundled ADB support in the Windows release package.
- No server account, API key, cloud relay, or wireless connection required.

## Requirements

For users:

- Windows 10/11 x64.
- Android 10 or newer, because the MVP targets `minSdk 29`.
- A USB data cable. Charge-only cables will not work.
- Android Developer options and USB debugging enabled.
- User approval for both USB debugging and Android screen capture.

For developers:

- .NET SDK with Windows desktop support. The project currently targets `net10.0-windows`.
- Android SDK build-tools/platforms.
- JDK 8+.
- Android platform-tools if you want to run ADB manually.

## Download And Run

From a GitHub Release, download:

- `UsbScreen-Android-v1.0.0.apk`
- `UsbScreen-Windows-win-x64-v1.0.0.zip`

Install the APK on the Android phone. Then extract the Windows zip and run `Start.bat` or `UsbScreenClient.exe`.

Basic flow:

1. Enable USB debugging on the phone.
2. Connect the phone to the PC with a USB data cable.
3. Accept the `Allow USB debugging?` prompt on the phone.
4. Open `USB Screen Stream` on Android.
5. Tap `START PROJECTION`.
6. Accept the Android screen capture prompt.
7. Open the Windows client.
8. Keep the default `360x800 / 600 kbps / 30 FPS` settings and click `Connect`.

## Build From Source

Build Android:

```powershell
.\android\build-android.ps1
```

If the script cannot find your SDK/JDK automatically:

```powershell
.\android\build-android.ps1 -AndroidSdk "C:\Path\To\Android\Sdk" -JdkHome "C:\Path\To\JDK"
```

Build Windows:

```powershell
dotnet restore .\windows\UsbScreenClient\UsbScreenClient.csproj
dotnet build .\windows\UsbScreenClient\UsbScreenClient.csproj -c Release
```

Run Windows from source:

```powershell
dotnet run --project .\windows\UsbScreenClient\UsbScreenClient.csproj -c Release
```

Create release packages:

```powershell
.\scripts\package-github-release.ps1 -AndroidSdk "C:\Path\To\Android\Sdk" -JdkHome "C:\Path\To\JDK" -AdbRoot "C:\Path\To\Android\Sdk\platform-tools"
```

## Transport Protocol

The Android service listens on device-local `127.0.0.1:27183`. The Windows client runs:

```text
adb forward tcp:27183 tcp:27183
```

After connecting, Windows sends one ASCII config line:

```text
mode=mjpeg;width=360;height=800;bitrate=600000;fps=30;quality=45
```

Android then streams repeated MJPEG frames:

```text
4-byte big-endian frameLength
JPEG bytes
4-byte big-endian frameLength
JPEG bytes
...
```

## Latency Notes

- Use `360x800`, `600 kbps`, `30 FPS` first.
- Lower JPEG quality before increasing resolution.
- Do not queue old frames in a viewer. Display the newest frame and drop stale frames if needed.
- Use direct USB when testing. Avoid USB hubs when diagnosing latency or disconnects.
- H.264 code is retained for experimentation, but the public v1.0.0 path defaults to MJPEG because it has fewer decoder and buffering failure modes.

## Related Projects

USB Screen Stream can be used alongside [osr-screen-tcode](https://github.com/LexZeon/osr-screen-tcode) as a wired, low-latency Android screen source. A typical integration path is to keep this project responsible for USB capture/preview and let downstream tools consume the mirrored screen or adapt the Windows frame reader.

## Privacy And Safety

This app captures the visible Android screen after the user grants Android's screen capture permission. Anything visible on the phone may appear in the Windows preview, including private messages, photos, passwords shown on screen, or protected work content.

Security reminders:

- Only use this with devices you own or have permission to test.
- Do not distribute captures without consent.
- USB debugging gives the connected computer powerful access to the phone. Only authorize trusted PCs.
- Some apps and systems may block screen capture or show black frames for protected content.
- Huawei PrivateSpace and similar multi-user/privacy modes may require installing and running the APK inside the same user space as the content being mirrored.

## Known Limitations

- USB only; no wireless mode.
- Video only; no audio.
- Preview only; no keyboard/mouse control of the phone.
- Rotation changes may require disconnecting and starting projection again.
- APKs are debug-signed unless you configure a production signing key.
- Windows release packages include ADB platform-tools files; review Android SDK Platform-Tools license terms before redistributing.

## Troubleshooting

Run:

```powershell
.\scripts\diagnose-usb.ps1
```

Common states:

- `unauthorized`: unlock the phone and accept the USB debugging prompt.
- `offline`: unplug and reconnect the cable.
- no device: enable USB debugging, use a data cable, switch USB mode to file transfer, or install the vendor USB driver.

## Contributing

Issues and pull requests are welcome. Please include:

- Windows version.
- Android device model and Android version.
- Whether the phone uses a privacy/multi-user mode.
- Exact client settings: resolution, bitrate/quality, FPS.
- Logs from `scripts/diagnose-usb.ps1` when possible.

See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## Contact

Contact LexZeon: [lexzeonstudio@gmail.com](mailto:lexzeonstudio@gmail.com).

---

# USB Screen Stream 中文说明

USB Screen Stream 是一个通过 USB 数据线把安卓屏幕低延迟显示到 Windows 窗口的开源 MVP。它适合需要有线投屏预览的用户和开发者，不需要账号、API Key、云服务或无线网络。

当前稳定方案是：Android 使用 `MediaProjection` 授权抓屏，前台服务输出 MJPEG 帧；Windows 通过 `adb forward` 建立 USB 转发，读取 JPEG 帧并直接显示。它优先低延迟和易调试，画质不是第一优先级。

## 主要功能

- 安卓屏幕通过 USB 投到 Windows。
- Android 官方投屏/录屏授权流程。
- 低缓存 MJPEG 传输。
- Windows 客户端支持连接/断开、分辨率、码率/画质、FPS、状态提示和显示窗口。
- Windows 发布包内置 ADB。
- 不需要账号、云端服务器或无线连接。

## 运行要求

用户需要：

- Windows 10/11 x64。
- Android 10 或更高版本。
- 能传数据的 USB 线。
- 手机开启开发者选项和 USB 调试。
- 手机上同意 USB 调试授权和投屏/录屏授权。

开发者需要：

- 支持 Windows Desktop 的 .NET SDK，当前目标框架为 `net10.0-windows`。
- Android SDK build-tools/platforms。
- JDK 8+。
- Android platform-tools。

## 下载运行

从 GitHub Release 下载：

- `UsbScreen-Android-v1.0.0.apk`
- `UsbScreen-Windows-win-x64-v1.0.0.zip`

手机安装 APK。Windows 解压 zip 后运行 `Start.bat` 或 `UsbScreenClient.exe`。

基本步骤：

1. 手机开启 USB 调试。
2. 用 USB 数据线连接手机和电脑。
3. 手机上允许 USB 调试。
4. 打开 Android 端 `USB Screen Stream`。
5. 点击 `START PROJECTION`。
6. 允许系统投屏/录屏权限。
7. 打开 Windows 客户端。
8. 保持默认 `360x800 / 600 kbps / 30 FPS`，点击 `Connect`。

## 隐私和安全提醒

这个软件会在用户授权后采集手机屏幕。手机上可见的隐私内容也可能显示在 Windows 窗口里。

请注意：

- 只在自己的设备或得到授权的设备上使用。
- 不要在未经同意的情况下传播投屏内容。
- USB 调试会给电脑较高的手机访问权限，只授权可信电脑。
- 某些 App 或系统页面可能禁止录屏，表现为黑屏。
- 华为隐私空间等多用户/隐私模式下，APK 必须安装在当前空间里，并且投屏 App 和要显示的内容要处在同一个空间。

## 相关项目

本项目可以和 [osr-screen-tcode](https://github.com/LexZeon/osr-screen-tcode) 联动使用，作为一个 USB 有线、低延迟的 Android 屏幕输入源。常见做法是让本项目负责手机抓屏和 Windows 端预览，下游项目复用或改造 Windows 收帧逻辑。

## 已知限制

- 当前只支持 USB，不支持无线。
- 不支持声音。
- 不支持电脑控制手机。
- 手机旋转后如显示异常，需要断开并重新开始投屏。
- APK 默认是调试签名。

## 反馈和贡献

欢迎提交 issue 和 pull request。反馈问题时请尽量提供手机型号、Android 版本、Windows 版本、是否使用隐私空间、客户端参数，以及 `scripts/diagnose-usb.ps1` 的输出。

## 联系方式

联系 LexZeon：[lexzeonstudio@gmail.com](mailto:lexzeonstudio@gmail.com)。

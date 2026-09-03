# Third-Party Notices

USB Screen Stream is released under the MIT License. It also relies on platform SDKs and tools when built or packaged.

## Microsoft .NET

The Windows client is built with Microsoft .NET and Windows Forms.

- Project: https://github.com/dotnet/runtime
- License: MIT License

Self-contained Windows release packages may include .NET runtime components. Review Microsoft .NET license terms before redistribution.

## Android SDK And Platform APIs

The Android app uses Android platform APIs including MediaProjection, MediaCodec, ImageReader, foreground services, and ADB port forwarding during development/testing.

- Android Open Source Project: https://source.android.com/
- Android SDK Platform-Tools license terms apply to redistributed `adb.exe` and related files.

## ADB Platform-Tools

Windows release packages may include:

- `adb.exe`
- `AdbWinApi.dll`
- `AdbWinUsbApi.dll`

These files come from Android SDK Platform-Tools. They are not authored by this project. Check and comply with the Android SDK Platform-Tools license before publishing binary release packages.

## No External Runtime Libraries In Source

The current source implementation does not vendor LibVLC, FFmpeg, or other third-party media libraries. Earlier experimental H.264 notes remain in documentation as future work context, but the v1.0.0 release path defaults to MJPEG.

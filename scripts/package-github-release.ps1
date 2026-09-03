param(
    [string]$Version = "v1.0.0",
    [string]$AndroidSdk = "",
    [string]$JdkHome = "",
    [string]$AdbRoot = ""
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Outputs = Join-Path $Root "outputs"
$ReleaseDir = Join-Path $Outputs "github-release"
$WorkDir = Join-Path $Root "work"
$SourceStage = Join-Path $WorkDir "github-source"
$WindowsStage = Join-Path $WorkDir "github-windows"

$AndroidApk = Join-Path $ReleaseDir "UsbScreen-Android-$Version.apk"
$WindowsZip = Join-Path $ReleaseDir "UsbScreen-Windows-win-x64-$Version.zip"
$SourceZip = Join-Path $ReleaseDir "UsbScreen-source-$Version.zip"

function Assert-Under([string]$Path, [string]$ExpectedRoot) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $fullRoot = [System.IO.Path]::GetFullPath($ExpectedRoot)
    if (-not $fullPath.StartsWith($fullRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside expected root: $fullPath"
    }
}

function Resolve-ExistingPath([string[]]$Candidates) {
    foreach ($candidate in $Candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

function Clean-Directory([string]$Path, [string]$ExpectedRoot) {
    Assert-Under $Path $ExpectedRoot
    if (Test-Path $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Force $Path | Out-Null
}

New-Item -ItemType Directory -Force $Outputs, $WorkDir | Out-Null
Clean-Directory $ReleaseDir $Outputs
Clean-Directory $SourceStage $WorkDir
Clean-Directory $WindowsStage $WorkDir

if (-not $AdbRoot) {
    $AdbRoot = Resolve-ExistingPath @(
        "$env:ANDROID_HOME\platform-tools",
        "$env:ANDROID_SDK_ROOT\platform-tools",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools"
    )
}

if (-not $AdbRoot) {
    throw "ADB platform-tools was not found. Pass -AdbRoot or set ANDROID_HOME."
}

foreach ($adbFile in @("adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll")) {
    $candidate = Join-Path $AdbRoot $adbFile
    if (-not (Test-Path $candidate)) {
        throw "Required ADB package file missing: $candidate"
    }
}

$AndroidBuildScript = Join-Path $Root "android\build-android.ps1"
if ($AndroidSdk -and $JdkHome) {
    & $AndroidBuildScript -AndroidSdk $AndroidSdk -JdkHome $JdkHome
} elseif ($AndroidSdk) {
    & $AndroidBuildScript -AndroidSdk $AndroidSdk
} elseif ($JdkHome) {
    & $AndroidBuildScript -JdkHome $JdkHome
} else {
    & $AndroidBuildScript
}

$BuiltApk = Join-Path $Root "android\build\UsbScreenAndroid-debug.apk"
if (-not (Test-Path $BuiltApk)) {
    throw "Android build did not produce $BuiltApk"
}
Copy-Item -LiteralPath $BuiltApk -Destination $AndroidApk -Force

dotnet publish (Join-Path $Root "windows\UsbScreenClient\UsbScreenClient.csproj") `
    -c Release `
    -r win-x64 `
    --self-contained true `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:DebugType=None `
    -p:DebugSymbols=false `
    -o $WindowsStage

$PlatformToolsOut = Join-Path $WindowsStage "platform-tools"
New-Item -ItemType Directory -Force $PlatformToolsOut | Out-Null
Copy-Item -Force (Join-Path $AdbRoot "adb.exe") $PlatformToolsOut
Copy-Item -Force (Join-Path $AdbRoot "AdbWinApi.dll") $PlatformToolsOut
Copy-Item -Force (Join-Path $AdbRoot "AdbWinUsbApi.dll") $PlatformToolsOut

Copy-Item -Force (Join-Path $Root "README.md") $WindowsStage
Copy-Item -Force (Join-Path $Root "LICENSE") $WindowsStage
Copy-Item -Force (Join-Path $Root "THIRD_PARTY_NOTICES.md") $WindowsStage
if (Test-Path (Join-Path $Outputs "使用说明.txt")) {
    Copy-Item -Force (Join-Path $Outputs "使用说明.txt") $WindowsStage
}

$StartBat = @"
@echo off
setlocal
cd /d "%~dp0"
UsbScreenClient.exe
if errorlevel 1 (
  echo.
  echo USB Screen Client exited with an error.
  echo Make sure the Android phone is connected, USB debugging is authorized, and platform-tools\adb.exe exists.
  echo.
  pause
)
"@
Set-Content -LiteralPath (Join-Path $WindowsStage "Start.bat") -Value $StartBat -Encoding ASCII

Compress-Archive -Path (Join-Path $WindowsStage "*") -DestinationPath $WindowsZip -Force

foreach ($item in @("android", "windows", "scripts", "README.md", "LICENSE", "CONTRIBUTING.md", "CHANGELOG.md", "THIRD_PARTY_NOTICES.md", "SECURITY.md", ".gitignore")) {
    $source = Join-Path $Root $item
    if (Test-Path $source) {
        Copy-Item -Recurse -Force $source $SourceStage
    }
}

$SourceAndroidBuild = Join-Path $SourceStage "android\build"
$SourceDebugKey = Join-Path $SourceStage "android\debug.keystore"
$SourceWindowsBin = Join-Path $SourceStage "windows\UsbScreenClient\bin"
$SourceWindowsObj = Join-Path $SourceStage "windows\UsbScreenClient\obj"
foreach ($path in @($SourceAndroidBuild, $SourceDebugKey, $SourceWindowsBin, $SourceWindowsObj)) {
    Assert-Under $path $SourceStage
    if (Test-Path $path) {
        Remove-Item -LiteralPath $path -Recurse -Force
    }
}

Compress-Archive -Path (Join-Path $SourceStage "*") -DestinationPath $SourceZip -Force

Get-Item $AndroidApk, $WindowsZip, $SourceZip | Select-Object FullName,Length,LastWriteTime

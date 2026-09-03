param(
    [string]$AdbRoot = ""
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Outputs = Join-Path $Root "outputs"
$ShareDir = Join-Path $Outputs "share-windows"
$ZipPath = Join-Path $Outputs "UsbScreen-Windows.zip"
$ApkOut = Join-Path $Outputs "UsbScreen-Android.apk"
$ApkSource = Join-Path $Root "android\build\UsbScreenAndroid-debug.apk"
$GuideSource = Join-Path $Outputs "使用说明.txt"

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

Assert-Under $ShareDir $Outputs
Assert-Under $ZipPath $Outputs
Assert-Under $ApkOut $Outputs

dotnet publish (Join-Path $Root "windows\UsbScreenClient\UsbScreenClient.csproj") `
    -c Release `
    -r win-x64 `
    --self-contained true `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:DebugType=None `
    -p:DebugSymbols=false `
    -o $ShareDir

$PlatformToolsOut = Join-Path $ShareDir "platform-tools"
New-Item -ItemType Directory -Force $PlatformToolsOut | Out-Null
Copy-Item -Force (Join-Path $AdbRoot "adb.exe") $PlatformToolsOut
Copy-Item -Force (Join-Path $AdbRoot "AdbWinApi.dll") $PlatformToolsOut
Copy-Item -Force (Join-Path $AdbRoot "AdbWinUsbApi.dll") $PlatformToolsOut
Copy-Item -Force $ApkSource $ApkOut
if (Test-Path $GuideSource) {
    Copy-Item -Force $GuideSource $ShareDir
}

if (Test-Path $ZipPath) {
    Remove-Item -LiteralPath $ZipPath -Force
}

Compress-Archive -Path (Join-Path $ShareDir "*") -DestinationPath $ZipPath -Force
Get-Item $ApkOut, $ZipPath | Select-Object FullName,Length,LastWriteTime

param(
    [string]$AndroidSdk = "",
    [string]$JdkHome = ""
)

$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ProjectDir
$AppDir = Join-Path $ProjectDir "app"
$BuildDir = Join-Path $ProjectDir "build"

function Resolve-ExistingPath([string[]]$Candidates) {
    foreach ($candidate in $Candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

if (-not $AndroidSdk) {
    $AndroidSdk = Resolve-ExistingPath @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        "$env:LOCALAPPDATA\Android\Sdk"
    )
}

if (-not $JdkHome) {
    $JdkHome = Resolve-ExistingPath @(
        $env:JAVA_HOME
    )
}

if (-not $AndroidSdk) {
    throw "Android SDK was not found. Pass -AndroidSdk or set ANDROID_HOME."
}
if (-not $JdkHome) {
    throw "JDK was not found. Pass -JdkHome or set JAVA_HOME."
}

$BuildTools = Get-ChildItem (Join-Path $AndroidSdk "build-tools") -Directory |
    Sort-Object { [version]($_.Name -replace '[^\d\.].*$', '') } -Descending |
    Select-Object -First 1
$Platform = Get-ChildItem (Join-Path $AndroidSdk "platforms") -Directory |
    Where-Object { $_.Name -match '^android-\d+$' } |
    Sort-Object { [int]($_.Name -replace 'android-', '') } -Descending |
    Select-Object -First 1

if (-not $BuildTools) {
    throw "No Android build-tools found under $AndroidSdk."
}
if (-not $Platform) {
    throw "No Android platform found under $AndroidSdk."
}

$Aapt2 = Join-Path $BuildTools.FullName "aapt2.exe"
$D8 = Join-Path $BuildTools.FullName "d8.bat"
$Zipalign = Join-Path $BuildTools.FullName "zipalign.exe"
$ApkSigner = Join-Path $BuildTools.FullName "apksigner.bat"
$AndroidJar = Join-Path $Platform.FullName "android.jar"
$Javac = Join-Path $JdkHome "bin\javac.exe"
$Jar = Join-Path $JdkHome "bin\jar.exe"
$Keytool = Join-Path $JdkHome "bin\keytool.exe"

foreach ($tool in @($Aapt2, $D8, $Zipalign, $ApkSigner, $AndroidJar, $Javac, $Jar, $Keytool)) {
    if (-not (Test-Path $tool)) {
        throw "Required tool missing: $tool"
    }
}

$env:JAVA_HOME = $JdkHome
$env:Path = (Join-Path $JdkHome "bin") + [System.IO.Path]::PathSeparator + $env:Path

$ResolvedBuildDir = [System.IO.Path]::GetFullPath($BuildDir)
$ResolvedProjectDir = [System.IO.Path]::GetFullPath($ProjectDir)
if (-not $ResolvedBuildDir.StartsWith($ResolvedProjectDir, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean build directory outside the Android project: $ResolvedBuildDir"
}
if (Test-Path $BuildDir) {
    Remove-Item -LiteralPath $BuildDir -Recurse -Force
}

$CompiledDir = Join-Path $BuildDir "compiled"
$GeneratedDir = Join-Path $BuildDir "generated"
$ClassesDir = Join-Path $BuildDir "classes"
$DexDir = Join-Path $BuildDir "dex"
$ClassesJar = Join-Path $BuildDir "classes.jar"
$UnsignedApk = Join-Path $BuildDir "unsigned.apk"
$AlignedApk = Join-Path $BuildDir "aligned.apk"
$OutputApk = Join-Path $BuildDir "UsbScreenAndroid-debug.apk"
$KeyStore = Join-Path $ProjectDir "debug.keystore"
New-Item -ItemType Directory -Force $CompiledDir, $GeneratedDir, $ClassesDir, $DexDir | Out-Null

& $Aapt2 compile --dir (Join-Path $AppDir "src\main\res") -o $CompiledDir
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed." }

$FlatFiles = @(Get-ChildItem $CompiledDir -Recurse -Filter *.flat | ForEach-Object { $_.FullName })
$LinkArgs = @(
    "link",
    "-o", $UnsignedApk,
    "-I", $AndroidJar,
    "--manifest", (Join-Path $AppDir "src\main\AndroidManifest.xml"),
    "--java", $GeneratedDir,
    "--auto-add-overlay",
    "--min-sdk-version", "29",
    "--target-sdk-version", "33",
    "--version-code", "100",
    "--version-name", "1.0.0"
) + $FlatFiles
& $Aapt2 @LinkArgs
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed." }

$JavaSources = @(
    Get-ChildItem (Join-Path $AppDir "src\main\java") -Recurse -Filter *.java
    Get-ChildItem $GeneratedDir -Recurse -Filter *.java
) | ForEach-Object { $_.FullName }

& $Javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $AndroidJar -classpath $AndroidJar -d $ClassesDir @JavaSources
if ($LASTEXITCODE -ne 0) { throw "javac failed." }

& $Jar cf $ClassesJar -C $ClassesDir .
if ($LASTEXITCODE -ne 0) { throw "classes jar failed." }

& $D8 --min-api 29 --lib $AndroidJar --output $DexDir $ClassesJar
if ($LASTEXITCODE -ne 0) { throw "d8 failed." }

& $Jar uf $UnsignedApk -C $DexDir classes.dex
if ($LASTEXITCODE -ne 0) { throw "jar update failed." }

& $Zipalign -p -f 4 $UnsignedApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed." }

if (-not (Test-Path $KeyStore)) {
    & $Keytool -genkeypair -v `
        -keystore $KeyStore `
        -storepass android `
        -alias androiddebugkey `
        -keypass android `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "debug key generation failed." }
}

& $ApkSigner sign `
    --ks $KeyStore `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $OutputApk `
    $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "APK signing failed." }

& $ApkSigner verify --verbose $OutputApk
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed." }

Write-Host "Built APK: $OutputApk"

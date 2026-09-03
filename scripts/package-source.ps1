$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Work = Join-Path $Root "work"
$Outputs = Join-Path $Root "outputs"
$PackageDir = Join-Path $Work "UsbScreenMvp-source"
$ZipPath = Join-Path $Outputs "UsbScreenMvp-source.zip"

function Assert-UnderRoot([string]$Path, [string]$ExpectedRoot) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $fullRoot = [System.IO.Path]::GetFullPath($ExpectedRoot)
    if (-not $fullPath.StartsWith($fullRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside expected root: $fullPath"
    }
}

Assert-UnderRoot $PackageDir $Work
Assert-UnderRoot $ZipPath $Outputs

if (Test-Path $PackageDir) {
    Remove-Item -LiteralPath $PackageDir -Recurse -Force
}
New-Item -ItemType Directory -Force $PackageDir | Out-Null

Copy-Item -Recurse -Force (Join-Path $Root "android") $PackageDir
Copy-Item -Recurse -Force (Join-Path $Root "windows") $PackageDir
Copy-Item -Recurse -Force (Join-Path $Root "scripts") $PackageDir
Copy-Item -Force (Join-Path $Root ".gitignore") $PackageDir
Copy-Item -Force (Join-Path $Root "README.md") $PackageDir
Copy-Item -Force (Join-Path $Root "LICENSE") $PackageDir
Copy-Item -Force (Join-Path $Root "CONTRIBUTING.md") $PackageDir
Copy-Item -Force (Join-Path $Root "CHANGELOG.md") $PackageDir
Copy-Item -Force (Join-Path $Root "THIRD_PARTY_NOTICES.md") $PackageDir
Copy-Item -Force (Join-Path $Root "SECURITY.md") $PackageDir

$ExcludedDirs = @(
    (Join-Path $PackageDir "android\build"),
    (Join-Path $PackageDir "windows\UsbScreenClient\bin"),
    (Join-Path $PackageDir "windows\UsbScreenClient\obj")
)

foreach ($dir in $ExcludedDirs) {
    Assert-UnderRoot $dir $PackageDir
    if (Test-Path $dir) {
        Remove-Item -LiteralPath $dir -Recurse -Force
    }
}

$DebugKey = Join-Path $PackageDir "android\debug.keystore"
Assert-UnderRoot $DebugKey $PackageDir
if (Test-Path $DebugKey) {
    Remove-Item -LiteralPath $DebugKey -Force
}

if (Test-Path $ZipPath) {
    Remove-Item -LiteralPath $ZipPath -Force
}

Compress-Archive -Path (Join-Path $PackageDir "*") -DestinationPath $ZipPath -Force
Get-Item $ZipPath | Select-Object FullName,Length

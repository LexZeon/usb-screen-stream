$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Outputs = Join-Path $Root "outputs"
$ShareDir = Join-Path $Outputs "share-windows"
$Guide = Join-Path $Outputs "使用说明.txt"
$ZipPath = Join-Path $Outputs "UsbScreen-Windows-with-guide.zip"

function Assert-Under([string]$Path, [string]$ExpectedRoot) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $fullRoot = [System.IO.Path]::GetFullPath($ExpectedRoot)
    if (-not $fullPath.StartsWith($fullRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside expected root: $fullPath"
    }
}

Assert-Under $ShareDir $Outputs
Assert-Under $Guide $Outputs
Assert-Under $ZipPath $Outputs

Copy-Item -Force $Guide $ShareDir

if (Test-Path $ZipPath) {
    Remove-Item -LiteralPath $ZipPath -Force
}

Compress-Archive -Path (Join-Path $ShareDir "*") -DestinationPath $ZipPath -Force
Get-Item $Guide, $ZipPath | Select-Object FullName,Length,LastWriteTime


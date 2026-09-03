param(
    [string]$AdbPath = ""
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

if (-not $AdbPath) {
    $AndroidHomeAdb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" } else { $null }
    $AndroidSdkRootAdb = if ($env:ANDROID_SDK_ROOT) { Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe" } else { $null }
    $Candidates = @(
        (Get-Command adb.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source),
        $AndroidHomeAdb,
        $AndroidSdkRootAdb,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
    )
    $AdbPath = $Candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
}

if (-not $AdbPath -or -not (Test-Path $AdbPath)) {
    throw "ADB was not found. Pass -AdbPath or install Android platform-tools."
}

Write-Host "ADB: $AdbPath"
& $AdbPath version

Write-Host ""
Write-Host "Devices:"
$DeviceOutput = & $AdbPath devices -l
$DeviceOutput

$RawDeviceLines = (& $AdbPath devices) | Where-Object { $_ -match "^\S+\s+\S+" -and $_ -notmatch "^List of devices" }
$DeviceLines = $RawDeviceLines | Where-Object { $_ -match "`tdevice$" }
if (-not $DeviceLines) {
    Write-Host ""
    if ($RawDeviceLines | Where-Object { $_ -match "`tunauthorized$" }) {
        Write-Host "A USB device is visible, but ADB says it is unauthorized."
        Write-Host "On the phone, unlock the screen and accept the 'Allow USB debugging?' dialog. If it does not appear, revoke USB debugging authorizations in Developer options, then reconnect the cable."
    } elseif ($RawDeviceLines | Where-Object { $_ -match "`toffline$" }) {
        Write-Host "A USB device is visible, but ADB says it is offline."
        Write-Host "Unplug and reconnect the cable, then run this script again."
    } else {
        Write-Host "No USB debugging device is visible."
        Write-Host "Check: Developer options > USB debugging, use a data cable, and avoid charge-only USB mode."
        $UsbPhones = Get-PnpDevice -PresentOnly -ErrorAction SilentlyContinue |
            Where-Object {
                $_.FriendlyName -match "Android|ADB|MTP|Portable|SAMSUNG|Xiaomi|OnePlus|Pixel|HUAWEI|vivo|OPPO|realme|Redmi" -or
                $_.InstanceId -match "VID_18D1|VID_04E8|VID_2717|VID_2A70|VID_12D1|VID_22D9|VID_2D95|VID_0BB4"
            } |
            Select-Object Status,Class,FriendlyName,InstanceId
        if ($UsbPhones) {
            Write-Host ""
            Write-Host "Windows does see these related USB devices, but ADB does not:"
            $UsbPhones | Format-Table -AutoSize
            Write-Host "This usually means USB debugging is disabled, the authorization dialog was not accepted, or the phone vendor ADB driver is missing."
        }
    }
    exit 2
}

Write-Host ""
Write-Host "Package:"
& $AdbPath shell pm path com.example.usbscreenstream

Write-Host ""
Write-Host "Process:"
& $AdbPath shell pidof com.example.usbscreenstream

Write-Host ""
Write-Host "Forward table:"
& $AdbPath forward --list

Write-Host ""
Write-Host "Recent app errors/logs:"
& $AdbPath logcat -d -t 250 UsbScreenStream:D AndroidRuntime:E ActivityManager:W ActivityTaskManager:I *:S

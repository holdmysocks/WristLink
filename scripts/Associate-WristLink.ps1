<#
.SYNOPSIS
Create only WristLink's Companion Device association through owner-authorized ADB.
.EXAMPLE
.\Associate-WristLink.ps1 -WatchSerial 'WATCH_SERIAL' -PhoneMac 'AA:BB:CC:DD:EE:FF'
.NOTES
Does not install the app, pair Bluetooth, grant notification sharing, or activate app observation.
After success, use WristLink Settings > Pause connection, then Connect to iPhone; verify service binding.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string]$WatchSerial,
    [Parameter(Mandatory=$true)][ValidatePattern('(?i)^(?:[0-9a-f]{2}:){5}[0-9a-f]{2}$')][string]$PhoneMac,
    [ValidateRange(0,999)][int]$UserId = 0,
    [string]$AdbPath = 'adb',
    [switch]$VerifyOnly
)
$ErrorActionPreference = 'Stop'
$PhoneMac = $PhoneMac.ToUpperInvariant()
function Invoke-WristLinkAdb {
    param([string[]]$AdbArguments)
    $result = @(& $AdbPath -s $WatchSerial @AdbArguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return ($result -join "`n")
}
$state = Invoke-WristLinkAdb -AdbArguments @('get-state')
if ($state.Trim() -ne 'device') { throw 'Watch is not online/authorized in ADB. Check adb devices -l.' }
$installed = Invoke-WristLinkAdb -AdbArguments @('shell','pm','path','dev.wristlink')
if ($installed -notmatch 'package:') { throw 'Install WristLink before creating its association.' }
$before = Invoke-WristLinkAdb -AdbArguments @('shell','dumpsys','companiondevice')
$ours = @($before -split "`r?`n" | Where-Object { $_ -match "mPackageName='dev\.wristlink'" -and $_ -match "mUserId=$UserId(?:,|\s)" })
$matching = @($ours | Where-Object { $_ -match ('(?i)mDeviceMacAddress=' + [regex]::Escape($PhoneMac) + '(?:,|\s)') })
if ($matching.Count -eq 0) {
    if ($ours.Count -gt 0) { throw 'A WristLink association already exists for a different address. Inspect it before replacing or adding associations; this helper will not create a duplicate.' }
    if ($VerifyOnly) { throw 'No matching WristLink association exists. VerifyOnly changed nothing.' }
    $null = Invoke-WristLinkAdb -AdbArguments @('shell','cmd','companiondevice','associate',"$UserId",'dev.wristlink',$PhoneMac)
} else { Write-Output 'Matching association already exists; reusing it.' }
$after = Invoke-WristLinkAdb -AdbArguments @('shell','dumpsys','companiondevice')
$verified = @($after -split "`r?`n" | Where-Object { $_ -match "mPackageName='dev\.wristlink'" -and $_ -match "mUserId=$UserId(?:,|\s)" -and $_ -match ('(?i)mDeviceMacAddress=' + [regex]::Escape($PhoneMac) + '(?:,|\s)') })
if ($verified.Count -ne 1) { throw 'Could not verify exactly one matching association. Inspect dumpsys companiondevice.' }
Write-Output 'Verified WristLink association. No bonds or other apps were changed.'
Write-Output ($verified -join "`n")
Write-Output 'Next: WristLink Settings > Pause connection, then Connect to iPhone.'
Write-Output 'Verify mNotifyOnDeviceNearby=true, Bound Companion Applications includes dev.wristlink while the phone is present, and app logs show ANCS ready.'

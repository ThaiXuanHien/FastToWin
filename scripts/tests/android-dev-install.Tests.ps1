$ErrorActionPreference = 'Stop'

$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$installerScript = Join-Path $projectDir 'scripts\android-dev-install.ps1'

function Assert-Equal {
    param(
        [Parameter(Mandatory = $true)]$Expected,
        [Parameter(Mandatory = $true)]$Actual,
        [Parameter(Mandatory = $true)][string]$Message
    )

    if ($Expected -ne $Actual) {
        throw "$Message Expected: '$Expected'. Actual: '$Actual'."
    }
}

function Assert-Contains {
    param(
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Actual,
        [Parameter(Mandatory = $true)][string]$Message
    )

    if (-not $Actual.Contains($Expected)) {
        throw "$Message Missing: '$Expected'. Actual: '$Actual'."
    }
}

if (-not (Test-Path -LiteralPath $installerScript)) {
    throw 'Missing Android dev installer helper.'
}

. $installerScript

$testDir = Join-Path ([IO.Path]::GetTempPath()) ("fasttowin-android-install-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDir | Out-Null

try {
    $fakeAdb = Join-Path $testDir 'adb.cmd'
    $callLog = Join-Path $testDir 'adb-calls.log'
    $attemptFile = Join-Path $testDir 'install-attempt.txt'
    $apk = Join-Path $testDir 'app-dev-debug.apk'
    New-Item -ItemType File -Path $apk | Out-Null

@"
@echo off
echo %*>>"$callLog"
if "%3"=="uninstall" goto uninstall
if not "%3"=="install" exit /b 2
if exist "$attemptFile" goto install_success
echo first>"$attemptFile"
echo adb.exe: failed to install: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package signatures do not match newer version; ignoring!] 1>&2
exit /b 1
:uninstall
echo Success
exit /b 0
:install_success
echo Success
exit /b 0
exit /b 2
"@ | Set-Content -LiteralPath $fakeAdb -Encoding Ascii

    $thrownMessage = $null
    try {
        Install-AndroidDevApk `
            -AdbPath $fakeAdb `
            -Serial 'device-1' `
            -ApkPath $apk `
            -PackageName 'com.hienthai.fastowin.dev'
    } catch {
        $thrownMessage = $_.Exception.Message
    }

    Assert-Contains '-ReinstallAndroid' $thrownMessage 'Signature mismatch must explain the explicit recovery option.'
    $callsWithoutConsent = @(Get-Content -LiteralPath $callLog)
    Assert-Equal 1 $callsWithoutConsent.Count 'The app must not be uninstalled without explicit consent.'

    Remove-Item -LiteralPath $callLog, $attemptFile -Force

    Install-AndroidDevApk `
        -AdbPath $fakeAdb `
        -Serial 'device-1' `
        -ApkPath $apk `
        -PackageName 'com.hienthai.fastowin.dev' `
        -ReinstallOnSignatureMismatch

    $callsWithConsent = @(Get-Content -LiteralPath $callLog)
    Assert-Equal 3 $callsWithConsent.Count 'Consented recovery must install, uninstall, then retry installation.'
    Assert-Contains '-s device-1 uninstall com.hienthai.fastowin.dev' $callsWithConsent[1] 'Recovery must uninstall only the dev package.'
    Assert-Contains '-s device-1 install -r -t' $callsWithConsent[2] 'Recovery must retry the original APK installation.'

    Write-Host 'Android dev installer tests: PASS' -ForegroundColor Green
} finally {
    if (Test-Path -LiteralPath $testDir) {
        Remove-Item -LiteralPath $testDir -Recurse -Force
    }
}

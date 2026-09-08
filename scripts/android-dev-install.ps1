function Invoke-FastToWinAdb {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $AdbPath @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    foreach ($line in $output) {
        Write-Host $line
    }

    return [PSCustomObject]@{
        ExitCode = $exitCode
        Output = (($output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine)
    }
}

function Install-AndroidDevApk {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$ApkPath,
        [Parameter(Mandatory = $true)][string]$PackageName,
        [switch]$ReinstallOnSignatureMismatch
    )

    $installArguments = @('-s', $Serial, 'install', '-r', '-t', $ApkPath)
    $installResult = Invoke-FastToWinAdb -AdbPath $AdbPath -Arguments $installArguments
    if ($installResult.ExitCode -eq 0) {
        return
    }

    $signatureMismatch = $installResult.Output -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' -or
        $installResult.Output -match 'signatures do not match'
    if (-not $signatureMismatch) {
        throw "Khong the cai APK development tren $Serial (adb exit $($installResult.ExitCode))."
    }

    if (-not $ReinstallOnSignatureMismatch) {
        throw "Ban dev tren $Serial duoc ky bang khoa khac. Chay lai '.\start-dev-all.cmd -ReinstallAndroid' de go rieng $PackageName va cai lai. Luu y: du lieu cuc bo cua ban dev tren thiet bi se bi xoa."
    }

    Write-Host "[FastToWin] Chu ky ban dev khong khop. Dang go $PackageName tren $Serial..." -ForegroundColor Yellow
    $uninstallResult = Invoke-FastToWinAdb `
        -AdbPath $AdbPath `
        -Arguments @('-s', $Serial, 'uninstall', $PackageName)
    if ($uninstallResult.ExitCode -ne 0) {
        throw "Khong the go ban dev $PackageName tren $Serial (adb exit $($uninstallResult.ExitCode))."
    }

    Write-Host "[FastToWin] Dang cai lai ban Android development tren $Serial..."
    $retryResult = Invoke-FastToWinAdb -AdbPath $AdbPath -Arguments $installArguments
    if ($retryResult.ExitCode -ne 0) {
        throw "Khong the cai lai APK development tren $Serial (adb exit $($retryResult.ExitCode))."
    }
}

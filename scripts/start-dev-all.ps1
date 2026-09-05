param(
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtimeDir = Join-Path $projectDir '.artifacts\dev-all'
$serverProcess = $null
$webProcess = $null
$startedServer = $false
$startedWeb = $false

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Lenh that bai ($LASTEXITCODE): $FilePath $($Arguments -join ' ')"
    }
}

function Test-HttpEndpoint {
    param([string]$Url)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Test-TcpPort {
    param([int]$Port)
    try {
        $client = [Net.Sockets.TcpClient]::new()
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        $connected = $task.Wait(500) -and $client.Connected
        $client.Dispose()
        return $connected
    } catch {
        return $false
    }
}

function Stop-ProcessTree {
    param([Diagnostics.Process]$Process)
    if ($null -eq $Process -or $Process.HasExited) { return }
    & taskkill.exe /PID $Process.Id /T /F *> $null
}

trap {
    if ($startedWeb) { Stop-ProcessTree $webProcess }
    if ($startedServer) { Stop-ProcessTree $serverProcess }
    Write-Host "[FastToWin] LOI: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Set-Location $projectDir
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
}
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) {
    throw "Khong tim thay Java tai $java"
}

$docker = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
if (-not (Test-Path -LiteralPath $docker)) {
    $dockerCommand = Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($null -eq $dockerCommand) {
        throw 'Khong tim thay Docker. Hay mo Docker Desktop roi chay lai.'
    }
    $docker = $dockerCommand.Source
}

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) {
    throw "Khong tim thay adb tai $adb"
}

Write-Host '[FastToWin] Khoi dong PostgreSQL...'
Invoke-Checked $docker compose up -d --wait database

Write-Host '[FastToWin] Ket noi cac thiet bi Android...'
Invoke-Checked (Join-Path $projectDir 'connect-dev-device.cmd')

$env:FASTTOWIN_ENV = 'dev'
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/fasttowin'
$env:DATABASE_USER = 'fasttowin'
$env:DATABASE_PASSWORD = 'fasttowin'
$env:FASTTOWIN_WEB_BASE_URL = 'http://localhost:8081'

if (-not (Test-HttpEndpoint 'http://127.0.0.1:8080/health')) {
    if (Test-TcpPort 8080) {
        throw 'Cong 8080 dang bi ung dung khac su dung. Hay dong ung dung do roi chay lai.'
    }

    $serverInstallDir = [IO.Path]::GetFullPath((Join-Path $projectDir '.artifacts\dev-server\server'))
    Write-Host '[FastToWin] Dong goi backend...'
    $prepareServerArguments = @(
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        (Join-Path $projectDir 'scripts\prepare-dev-server.ps1')
    )
    Invoke-Checked -FilePath 'powershell.exe' -Arguments $prepareServerArguments

    $serverLib = Join-Path $serverInstallDir 'lib\*'
    $serverProcess = Start-Process `
        -FilePath $java `
        -ArgumentList @('-cp', $serverLib, 'com.hienthai.fastowin.server.MainKt') `
        -WorkingDirectory $projectDir `
        -RedirectStandardOutput (Join-Path $runtimeDir 'server.stdout.log') `
        -RedirectStandardError (Join-Path $runtimeDir 'server.stderr.log') `
        -WindowStyle Hidden `
        -PassThru
    $startedServer = $true

    $serverReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (Test-HttpEndpoint 'http://127.0.0.1:8080/health') {
            $serverReady = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $serverReady) {
        throw "Backend khong san sang. Xem log tai $runtimeDir"
    }
} else {
    Write-Host '[FastToWin] Backend cong 8080 dang chay, se su dung lai.'
}

$deviceLines = & $adb devices
$serials = foreach ($line in ($deviceLines | Select-Object -Skip 1)) {
    $parts = $line.Trim() -split '\s+'
    if ($parts.Length -ge 2 -and $parts[1] -eq 'device') {
        $parts[0]
    }
}

if ($serials.Count -eq 0) {
    Write-Host '[FastToWin] Khong co thiet bi Android online, bo qua buoc build va cai app.' -ForegroundColor Yellow
} else {
    Write-Host '[FastToWin] Dong goi ban Android development...'
    Invoke-Checked (Join-Path $projectDir 'gradlew.bat') :app:assembleDevDebug

    $androidApk = Join-Path $projectDir 'app\build\outputs\apk\dev\debug\app-dev-debug.apk'
    if (-not (Test-Path -LiteralPath $androidApk)) {
        throw "Khong tim thay APK development tai $androidApk"
    }

    foreach ($serial in $serials) {
        Write-Host "[FastToWin] Cai ban Android development tren $serial..."
        Invoke-Checked $adb -s $serial install -r -t $androidApk
        & $adb -s $serial shell am start -W `
            -n 'com.hienthai.fastowin.dev/com.hienthai.fastowin.MainActivity' *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Khong the mo app Android tren $serial"
        }
        Write-Host "[FastToWin] Da mo Android tren $serial"
    }
}

if (Test-TcpPort 8081) {
    Write-Host '[FastToWin] Web cong 8081 dang chay, se su dung lai.'
} else {
    Write-Host '[FastToWin] Khoi dong web Kotlin/Wasm...'
    $webProcess = Start-Process `
        -FilePath (Join-Path $projectDir 'gradlew.bat') `
        -ArgumentList @(':webApp:wasmJsBrowserDevelopmentRun', '--no-daemon') `
        -WorkingDirectory $projectDir `
        -RedirectStandardOutput (Join-Path $runtimeDir 'web.stdout.log') `
        -RedirectStandardError (Join-Path $runtimeDir 'web.stderr.log') `
        -WindowStyle Hidden `
        -PassThru
    $startedWeb = $true

    $webReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if (Test-TcpPort 8081) {
            $webReady = $true
            break
        }
        if ($webProcess.HasExited) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $webReady) {
        throw "Web khong san sang. Xem log tai $runtimeDir"
    }
}

Write-Host '[FastToWin] Android va web da san sang.' -ForegroundColor Green
Write-Host '[FastToWin] Web: http://localhost:8081'
Write-Host "[FastToWin] Log: $runtimeDir"

if (-not $NoBrowser) {
    Start-Process 'http://localhost:8081'
}

if ($startedServer -or $startedWeb) {
    Write-Host '[FastToWin] Nhan Ctrl+C de dung cac tien trinh do script nay khoi dong.'
    try {
        while ($true) {
            if ($startedServer -and $serverProcess.HasExited) {
                throw 'Backend da dung bat ngo. Hay kiem tra server.stderr.log.'
            }
            if ($startedWeb -and $webProcess.HasExited) {
                throw 'Web da dung bat ngo. Hay kiem tra web.stderr.log.'
            }
            Start-Sleep -Seconds 1
        }
    } finally {
        if ($startedWeb) { Stop-ProcessTree $webProcess }
        if ($startedServer) { Stop-ProcessTree $serverProcess }
    }
}

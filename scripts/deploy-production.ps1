param(
    [switch]$BuildOnly
)

$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$environmentFile = Join-Path $projectDir 'deploy\.env.production'
$requiredSecretFiles = @(
    (Join-Path $projectDir 'deploy\secrets\database_password.txt'),
    (Join-Path $projectDir 'deploy\secrets\smtp_password.txt'),
    (Join-Path $projectDir 'deploy\secrets\firebase-service-account.json')
)

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Lệnh thất bại ($LASTEXITCODE): $FilePath $($Arguments -join ' ')"
    }
}

Set-Location $projectDir

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
}
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) {
    throw "Không tìm thấy Java tại $java. Hãy đặt JAVA_HOME tới JDK 17."
}

$dockerPath = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
if (-not (Test-Path -LiteralPath $dockerPath)) {
    $dockerCommand = Get-Command docker.exe -ErrorAction SilentlyContinue
    $dockerPath = if ($null -eq $dockerCommand) { $null } else { $dockerCommand.Source }
}
if ([string]::IsNullOrWhiteSpace($dockerPath)) {
    throw 'Không tìm thấy Docker CLI. Hãy cài và khởi động Docker Desktop/Engine.'
}

Write-Host '[FastToWin] Kiểm thử và đóng gói backend + Web production...'
Invoke-Checked (Join-Path $projectDir 'gradlew.bat') `
    :server:clean `
    :server:test `
    :server:installDist `
    :webApp:composeCompatibilityBrowserDistribution `
    --no-daemon

$serverDistribution = Join-Path $projectDir 'server\build\install\server\bin\server'
$webDistribution = Join-Path $projectDir 'webApp\build\dist\composeWebCompatibility\productionExecutable\index.html'
if (-not (Test-Path -LiteralPath $serverDistribution)) {
    throw "Không tìm thấy backend distribution tại $serverDistribution"
}
if (-not (Test-Path -LiteralPath $webDistribution)) {
    throw "Không tìm thấy Web distribution tại $webDistribution"
}

if ($BuildOnly) {
    Write-Host '[FastToWin] Build production đã hoàn tất.' -ForegroundColor Green
    exit 0
}

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw 'Thiếu deploy\.env.production. Hãy sao chép từ deploy\.env.production.example và thay domain/email.'
}
foreach ($secretFile in $requiredSecretFiles) {
    if (-not (Test-Path -LiteralPath $secretFile)) {
        throw "Thiếu production secret: $secretFile"
    }
}

Write-Host '[FastToWin] Kiểm tra Docker Compose production...'
Invoke-Checked $dockerPath compose --env-file $environmentFile -f compose.production.yaml config --quiet

Write-Host '[FastToWin] Build image và khởi động production...'
Invoke-Checked $dockerPath compose --env-file $environmentFile -f compose.production.yaml up -d --build --wait

Write-Host '[FastToWin] Production đã khởi động. Kiểm tra HTTPS /health trước khi phát hành.' -ForegroundColor Green

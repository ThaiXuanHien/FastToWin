param(
    [switch]$BuildOnly,
    [string]$ReleaseTag
)

$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$environmentFile = Join-Path $projectDir 'deploy\.env.production'
$requiredSecretFiles = @(
    (Join-Path $projectDir 'deploy\secrets\database_password.txt'),
    (Join-Path $projectDir 'deploy\secrets\smtp_password.txt'),
    (Join-Path $projectDir 'deploy\secrets\firebase-service-account.json'),
    (Join-Path $projectDir 'deploy\secrets\grafana_admin_password.txt')
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

if ([string]::IsNullOrWhiteSpace($ReleaseTag)) {
    $ReleaseTag = (& git -c "safe.directory=$($projectDir -replace '\\', '/')" rev-parse --short=12 HEAD 2>$null)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($ReleaseTag)) {
        $ReleaseTag = 'local'
    }
}
$ReleaseTag = $ReleaseTag.Trim()
if ($ReleaseTag -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$') {
    throw 'ReleaseTag chỉ được gồm chữ, số, dấu chấm, gạch dưới và gạch ngang (tối đa 64 ký tự).'
}
$env:FASTTOWIN_RELEASE_TAG = $ReleaseTag
Write-Host "[FastToWin] Release: $ReleaseTag"

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

$stateDir = Join-Path $projectDir 'deploy\state'
$activeReleaseFile = Join-Path $stateDir 'active-release.txt'
$previousReleaseFile = Join-Path $stateDir 'previous-release.txt'
$previousRelease = if (Test-Path -LiteralPath $activeReleaseFile) {
    (Get-Content -LiteralPath $activeReleaseFile -Raw).Trim()
} else { $null }
New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
if (-not [string]::IsNullOrWhiteSpace($previousRelease) -and $previousRelease -ne $ReleaseTag) {
    Set-Content -LiteralPath $previousReleaseFile -Value $previousRelease -NoNewline
}
Set-Content -LiteralPath $activeReleaseFile -Value $ReleaseTag -NoNewline

Write-Host "[FastToWin] Production release $ReleaseTag đã khởi động. Chạy production-ops health." -ForegroundColor Green

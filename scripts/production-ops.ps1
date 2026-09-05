param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet(
        'status',
        'health',
        'maintenance-on',
        'maintenance-off',
        'backup',
        'verify-backup',
        'backup-cycle',
        'restore-drill',
        'prune-backups',
        'rollback'
    )]
    [string]$Action,
    [Parameter(Position = 1)]
    [string]$Value,
    [switch]$ConfirmSchemaCompatible
)

$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$environmentFile = Join-Path $projectDir 'deploy\.env.production'
$composeFile = Join-Path $projectDir 'compose.production.yaml'
$stateDir = Join-Path $projectDir 'deploy\state'
$backupDir = Join-Path $projectDir 'deploy\backups'
$defaultBackupRetentionDays = 14

function Find-Docker {
    $desktopDocker = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
    if (Test-Path -LiteralPath $desktopDocker) { return $desktopDocker }
    $command = Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($null -eq $command) { throw 'Không tìm thấy Docker CLI.' }
    return $command.Source
}

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

function Get-EnvValue([string]$Name, [string]$Default = '') {
    $line = Get-Content -LiteralPath $environmentFile |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -Last 1
    if ($null -eq $line) { return $Default }
    return ($line -split '=', 2)[1].Trim()
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $composeArguments = @('compose', '--env-file', $environmentFile, '-f', $composeFile) + $Arguments
    Invoke-Checked -FilePath $script:dockerPath -Arguments $composeArguments
}

function Get-DatabaseContainerId {
    $containerId = (& $script:dockerPath compose --env-file $environmentFile -f $composeFile ps -q database).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        throw 'Database container chưa chạy.'
    }
    return $containerId
}

function Resolve-BackupFile([string]$Path = '') {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        $latest = Get-ChildItem -LiteralPath $backupDir -Filter 'fasttowin-*.dump' -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTimeUtc -Descending |
            Select-Object -First 1
        if ($null -eq $latest) { throw 'Chưa có backup PostgreSQL để kiểm tra.' }
        return $latest.FullName
    }

    $candidate = if ([IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $projectDir $Path }
    $resolved = [IO.Path]::GetFullPath($candidate)
    $allowedRoot = [IO.Path]::GetFullPath("$backupDir$([IO.Path]::DirectorySeparatorChar)")
    if (-not $resolved.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Chỉ cho phép dùng backup trong deploy\backups.'
    }
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf) -or [IO.Path]::GetExtension($resolved) -ne '.dump') {
        throw "Không tìm thấy backup hợp lệ: $resolved"
    }
    return $resolved
}

function Get-BackupRetentionDays {
    $raw = if ($env:FASTTOWIN_BACKUP_RETENTION_DAYS) {
        $env:FASTTOWIN_BACKUP_RETENTION_DAYS
    } else {
        Get-EnvValue 'FASTTOWIN_BACKUP_RETENTION_DAYS' "$defaultBackupRetentionDays"
    }
    $days = 0
    if (-not [int]::TryParse($raw, [ref]$days) -or $days -lt 1 -or $days -gt 3650) {
        throw 'FASTTOWIN_BACKUP_RETENTION_DAYS phải từ 1 đến 3650.'
    }
    return $days
}

function Test-ProductionHealth {
    $domain = Get-EnvValue 'FASTTOWIN_DOMAIN'
    if ([string]::IsNullOrWhiteSpace($domain) -or $domain -like '*your-domain*') {
        throw 'FASTTOWIN_DOMAIN chưa được cấu hình trong deploy\.env.production.'
    }
    $health = Invoke-WebRequest -UseBasicParsing -Uri "https://$domain/health" -TimeoutSec 15
    if ($health.StatusCode -ne 200 -or $health.Content.Trim() -ne 'OK') {
        throw "Health check thất bại: HTTP $($health.StatusCode)."
    }
    $status = Invoke-RestMethod -Uri "https://$domain/status" -TimeoutSec 15
    Write-Host "[FastToWin] /health OK; maintenance=$($status.maintenance); pollAfterSeconds=$($status.pollAfterSeconds)" -ForegroundColor Green
}

function Backup-Database {
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backupFile = Join-Path $backupDir "fasttowin-$timestamp.dump"
    $containerId = Get-DatabaseContainerId
    $remoteFile = "/tmp/fasttowin-$timestamp.dump"
    $dumpCommand = "pg_dump -U `"`$POSTGRES_USER`" -d `"`$POSTGRES_DB`" -Fc -f '$remoteFile'"
    $dumpArguments = @('exec', $containerId, 'sh', '-c', $dumpCommand)
    Invoke-Checked -FilePath $script:dockerPath -Arguments $dumpArguments
    try {
        Invoke-Checked -FilePath $script:dockerPath -Arguments @('cp', "${containerId}:$remoteFile", $backupFile)
    } finally {
        & $script:dockerPath exec $containerId rm -f $remoteFile | Out-Null
    }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $backupFile).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$backupFile.sha256" -Value "$hash  $([IO.Path]::GetFileName($backupFile))"
    Write-Host "[FastToWin] Backup: $backupFile" -ForegroundColor Green
    return $backupFile
}

function Test-Backup([string]$Path = '') {
    $backupFile = Resolve-BackupFile $Path
    $checksumFile = "$backupFile.sha256"
    if (-not (Test-Path -LiteralPath $checksumFile -PathType Leaf)) {
        throw "Thiếu checksum: $checksumFile"
    }

    $expectedHash = ((Get-Content -LiteralPath $checksumFile -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $backupFile).Hash.ToLowerInvariant()
    if ($expectedHash -ne $actualHash) {
        throw "Checksum không khớp cho $backupFile"
    }

    $containerId = Get-DatabaseContainerId
    $remoteFile = "/tmp/verify-$([IO.Path]::GetFileName($backupFile))"
    Invoke-Checked -FilePath $script:dockerPath -Arguments @('cp', $backupFile, "${containerId}:$remoteFile")
    try {
        Invoke-Checked -FilePath $script:dockerPath -Arguments @('exec', $containerId, 'pg_restore', '--list', $remoteFile) | Out-Null
    } finally {
        & $script:dockerPath exec $containerId rm -f $remoteFile | Out-Null
    }
    Write-Host "[FastToWin] Backup hợp lệ: $backupFile" -ForegroundColor Green
    return $backupFile
}

function Remove-ExpiredBackups {
    $retentionDays = Get-BackupRetentionDays
    $cutoff = (Get-Date).ToUniversalTime().AddDays(-$retentionDays)
    $expired = Get-ChildItem -LiteralPath $backupDir -Filter 'fasttowin-*.dump' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTimeUtc -lt $cutoff }

    foreach ($dump in $expired) {
        $resolved = [IO.Path]::GetFullPath($dump.FullName)
        $allowedRoot = [IO.Path]::GetFullPath("$backupDir$([IO.Path]::DirectorySeparatorChar)")
        if (-not $resolved.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Từ chối xóa file ngoài thư mục backup: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Force
        $checksum = "$resolved.sha256"
        if (Test-Path -LiteralPath $checksum -PathType Leaf) {
            Remove-Item -LiteralPath $checksum -Force
        }
    }
    Write-Host "[FastToWin] Đã xóa $($expired.Count) backup cũ hơn $retentionDays ngày." -ForegroundColor Green
}

function Invoke-RestoreDrill([string]$Path = '') {
    $backupFile = @(Test-Backup $Path)[-1]
    $timestamp = Get-Date -Format 'yyyyMMddHHmmss'
    $containerName = "fasttowin-restore-drill-$timestamp-$PID"
    $databaseName = 'fasttowin_restore_drill'
    $databaseUser = 'fasttowin_drill'
    $databasePassword = [Guid]::NewGuid().ToString('N')
    $remoteFile = '/tmp/restore.dump'
    $started = $false

    try {
        $runArguments = @(
            'run', '--detach', '--rm',
            '--name', $containerName,
            '--tmpfs', '/var/lib/postgresql/data:rw,noexec,nosuid,size=1g',
            '--env', "POSTGRES_DB=$databaseName",
            '--env', "POSTGRES_USER=$databaseUser",
            '--env', "POSTGRES_PASSWORD=$databasePassword",
            'postgres:17-alpine'
        )
        Invoke-Checked -FilePath $script:dockerPath -Arguments $runArguments | Out-Null
        $started = $true

        $ready = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            & $script:dockerPath exec $containerName pg_isready -U $databaseUser -d $databaseName *> $null
            if ($LASTEXITCODE -eq 0) {
                $ready = $true
                break
            }
            Start-Sleep -Seconds 1
        }
        if (-not $ready) { throw 'PostgreSQL phục vụ restore drill không sẵn sàng.' }

        Invoke-Checked -FilePath $script:dockerPath -Arguments @('cp', $backupFile, "${containerName}:$remoteFile")
        $restoreArguments = @(
            'exec', '--env', "PGPASSWORD=$databasePassword", $containerName,
            'pg_restore', '--exit-on-error', '--no-owner', '--no-privileges',
            '-U', $databaseUser, '-d', $databaseName, $remoteFile
        )
        Invoke-Checked -FilePath $script:dockerPath -Arguments $restoreArguments | Out-Null

        $tableQuery = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
        $tableQueryArguments = @(
            'exec', '--env', "PGPASSWORD=$databasePassword", $containerName,
            'psql', '-U', $databaseUser, '-d', $databaseName, '-Atc', $tableQuery
        )
        $tableCount = (& $script:dockerPath @tableQueryArguments).Trim()
        if ($LASTEXITCODE -ne 0 -or [int]$tableCount -lt 1) {
            throw 'Restore drill không tìm thấy bảng dữ liệu.'
        }

        $coreQuery = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('users','sessions','matches');"
        $coreQueryArguments = @(
            'exec', '--env', "PGPASSWORD=$databasePassword", $containerName,
            'psql', '-U', $databaseUser, '-d', $databaseName, '-Atc', $coreQuery
        )
        $coreTableCount = (& $script:dockerPath @coreQueryArguments).Trim()
        if ($LASTEXITCODE -ne 0 -or [int]$coreTableCount -ne 3) {
            throw 'Restore drill thiếu một hoặc nhiều bảng lõi: users, sessions, matches.'
        }

        Write-Host "[FastToWin] Restore drill đạt: $tableCount bảng, đủ 3 bảng lõi." -ForegroundColor Green
    } finally {
        if ($started) {
            & $script:dockerPath rm -f $containerName *> $null
        }
    }
}

function Set-Maintenance([bool]$Enabled, [string]$Message = '') {
    $oldEnabled = $env:FASTTOWIN_MAINTENANCE
    $oldMessage = $env:FASTTOWIN_MAINTENANCE_MESSAGE
    try {
        $env:FASTTOWIN_MAINTENANCE = if ($Enabled) { 'true' } else { 'false' }
        if ($Enabled -and -not [string]::IsNullOrWhiteSpace($Message)) {
            $env:FASTTOWIN_MAINTENANCE_MESSAGE = $Message
        }
        Invoke-Compose -Arguments @('up', '-d', '--no-deps', '--force-recreate', '--wait', 'server')
    } finally {
        $env:FASTTOWIN_MAINTENANCE = $oldEnabled
        $env:FASTTOWIN_MAINTENANCE_MESSAGE = $oldMessage
    }
    Write-Host "[FastToWin] Maintenance: $Enabled" -ForegroundColor Green
}

Set-Location $projectDir
if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw 'Thiếu deploy\.env.production.'
}
$dockerPath = Find-Docker
$activeReleaseFile = Join-Path $stateDir 'active-release.txt'
if (-not $env:FASTTOWIN_RELEASE_TAG -and (Test-Path -LiteralPath $activeReleaseFile)) {
    $env:FASTTOWIN_RELEASE_TAG = (Get-Content -LiteralPath $activeReleaseFile -Raw).Trim()
}

switch ($Action) {
    'status' {
        Invoke-Compose -Arguments @('ps')
        if (Test-Path -LiteralPath $activeReleaseFile) {
            Write-Host "[FastToWin] Active release: $((Get-Content $activeReleaseFile -Raw).Trim())"
        }
    }
    'health' { Test-ProductionHealth }
    'maintenance-on' {
        $message = if ([string]::IsNullOrWhiteSpace($Value)) {
            'Máy chủ đang nâng cấp. Vui lòng quay lại sau.'
        } else { $Value }
        Set-Maintenance $true $message
    }
    'maintenance-off' { Set-Maintenance $false }
    'backup' { Backup-Database | Out-Null }
    'verify-backup' { Test-Backup $Value | Out-Null }
    'backup-cycle' {
        $backupFile = @(Backup-Database)[-1]
        Test-Backup $backupFile | Out-Null
        Remove-ExpiredBackups
    }
    'restore-drill' { Invoke-RestoreDrill $Value }
    'prune-backups' { Remove-ExpiredBackups }
    'rollback' {
        if (-not $ConfirmSchemaCompatible) {
            throw 'Rollback có thể không tương thích migration. Chỉ chạy lại với -ConfirmSchemaCompatible sau khi đã kiểm tra schema và backup.'
        }
        $target = $Value
        if ([string]::IsNullOrWhiteSpace($target)) {
            $previousFile = Join-Path $stateDir 'previous-release.txt'
            if (Test-Path -LiteralPath $previousFile) {
                $target = (Get-Content -LiteralPath $previousFile -Raw).Trim()
            }
        }
        if ([string]::IsNullOrWhiteSpace($target) -or $target -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$') {
            throw 'Không tìm thấy release rollback hợp lệ.'
        }
        $serverImage = if ($env:FASTTOWIN_SERVER_IMAGE) {
            $env:FASTTOWIN_SERVER_IMAGE
        } else { Get-EnvValue 'FASTTOWIN_SERVER_IMAGE' 'fasttowin-server' }
        $webImage = if ($env:FASTTOWIN_WEB_IMAGE) {
            $env:FASTTOWIN_WEB_IMAGE
        } else { Get-EnvValue 'FASTTOWIN_WEB_IMAGE' 'fasttowin-web' }
        Invoke-Checked -FilePath $dockerPath -Arguments @('image', 'inspect', "${serverImage}:$target")
        Invoke-Checked -FilePath $dockerPath -Arguments @('image', 'inspect', "${webImage}:$target")
        Backup-Database
        Set-Maintenance $true 'Máy chủ đang khôi phục phiên bản ổn định.'
        $oldTag = $env:FASTTOWIN_RELEASE_TAG
        $oldMaintenance = $env:FASTTOWIN_MAINTENANCE
        try {
            $env:FASTTOWIN_RELEASE_TAG = $target
            $env:FASTTOWIN_MAINTENANCE = 'false'
            Invoke-Compose -Arguments @('up', '-d', '--no-build', '--wait', 'server', 'web')
            Test-ProductionHealth
        } finally {
            $env:FASTTOWIN_RELEASE_TAG = $oldTag
            $env:FASTTOWIN_MAINTENANCE = $oldMaintenance
        }
        New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
        if (-not [string]::IsNullOrWhiteSpace($oldTag) -and $oldTag -ne $target) {
            Set-Content -LiteralPath (Join-Path $stateDir 'previous-release.txt') -Value $oldTag -NoNewline
        }
        Set-Content -LiteralPath (Join-Path $stateDir 'active-release.txt') -Value $target -NoNewline
        Write-Host "[FastToWin] Đã rollback ứng dụng về $target." -ForegroundColor Green
    }
}

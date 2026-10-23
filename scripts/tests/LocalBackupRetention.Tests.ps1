$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$fixture = Join-Path $projectDir ('.artifacts/retention-tests/' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $fixture -Force
foreach ($name in @('expired.dump','fresh.dump','not-a-backup.dump')) {
    [IO.File]::WriteAllText((Join-Path $fixture $name), $(if ($name -eq 'not-a-backup.dump') { 'fixture' } else { 'PGDMP-test-fixture' }))
}
(Get-Item (Join-Path $fixture 'expired.dump')).LastWriteTimeUtc = [datetime]::UtcNow.AddDays(-15)
(Get-Item (Join-Path $fixture 'not-a-backup.dump')).LastWriteTimeUtc = [datetime]::UtcNow.AddDays(-15)
$script = Join-Path $projectDir 'scripts/local-backup-retention.ps1'
$report = @(& $script -BackupDirectory $fixture)
if ($report.Count -ne 2 -or @($report | Where-Object Expired).Count -ne 1) { throw 'Must classify only real PostgreSQL custom dumps' }
if (-not (Test-Path (Join-Path $fixture 'expired.dump'))) { throw 'Default report must not delete' }
& $script -BackupDirectory $fixture -Apply -Confirm:$false | Out-Null
if (Test-Path (Join-Path $fixture 'expired.dump')) { throw 'Expired dump was not removed' }
foreach ($name in @('fresh.dump','not-a-backup.dump')) {
    if (-not (Test-Path (Join-Path $fixture $name))) { throw 'Fresh/unrecognized file must remain' }
}
$rejected = $false
try { & $script -BackupDirectory ([IO.Path]::GetTempPath()) -Apply -Confirm:$false | Out-Null } catch { $rejected = $true }
if (-not $rejected) { throw 'Must reject directories outside project artifact/backup roots' }
Write-Host 'PASS: default report is read-only; expiry deletes only valid expired dumps; rejects outside roots.'

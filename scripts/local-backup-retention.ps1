[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory = $true)][string]$BackupDirectory,
    [string]$ProjectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')),
    [ValidateRange(1, 14)][int]$RetentionDays = 14,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectDir = [IO.Path]::GetFullPath($ProjectDirectory)
if (-not (Test-Path -LiteralPath (Join-Path $projectDir '.git'))) { throw 'ProjectDirectory must be the FastToWin checkout.' }
$directory = [IO.Path]::GetFullPath($BackupDirectory)
$separator = [IO.Path]::DirectorySeparatorChar
$allowed = @('.artifacts', 'deploy/backups') | ForEach-Object {
    [IO.Path]::GetFullPath((Join-Path $projectDir $_)).TrimEnd($separator)
}
$inside = @($allowed | Where-Object {
    $directory.Equals($_, [StringComparison]::OrdinalIgnoreCase) -or
    $directory.StartsWith("$_$separator", [StringComparison]::OrdinalIgnoreCase)
}).Count -gt 0
if (-not $inside) { throw 'Only project .artifacts and deploy/backups directories are allowed.' }
# Reject a linked root or ancestor before enumerating any file.
$ancestor = Get-Item -LiteralPath $directory -Force
if (-not $ancestor.PSIsContainer) { throw 'BackupDirectory must be a directory.' }
while ($ancestor.FullName -ne $projectDir) {
    if ($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Linked directories are not allowed.' }
    $ancestor = $ancestor.Parent
    if ($null -eq $ancestor) { throw 'Directory escaped project.' }
}
$cutoff = [datetime]::UtcNow.AddDays(-$RetentionDays)
$pending = [Collections.Generic.Stack[string]]::new()
$pending.Push($directory)
while ($pending.Count -gt 0) {
    $current = $pending.Pop()
    foreach ($entry in Get-ChildItem -LiteralPath $current -Force) {
        if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { continue }
        if ($entry.PSIsContainer) { $pending.Push($entry.FullName); continue }
        if ($entry.Extension -ne '.dump') { continue }
        $stream = [IO.File]::OpenRead($entry.FullName)
        try {
            $signature = New-Object byte[] 5
            $read = $stream.Read($signature, 0, 5)
        } finally { $stream.Dispose() }
        if ($read -ne 5 -or [Text.Encoding]::ASCII.GetString($signature) -ne 'PGDMP') { continue }
        $target = [IO.Path]::GetFullPath($entry.FullName)
        if (-not $target.StartsWith("$directory$separator", [StringComparison]::OrdinalIgnoreCase)) { throw 'File escaped backup directory.' }
        $expired = $entry.LastWriteTimeUtc -le $cutoff
        $deleted = $false
        if ($expired -and $Apply -and $PSCmdlet.ShouldProcess($target, "Permanently delete PostgreSQL dump older than $RetentionDays days")) {
            # Recheck after approval; a newly replaced file must not be pruned.
            $fresh = Get-Item -LiteralPath $target -Force
            if (($fresh.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $fresh.LastWriteTimeUtc -gt $cutoff) { throw 'Backup changed during retention check.' }
            Remove-Item -LiteralPath $target -ErrorAction Stop
            $deleted = -not (Test-Path -LiteralPath $target)
        }
        [pscustomobject]@{ Path = $target; LastWriteTimeUtc = $entry.LastWriteTimeUtc; Expired = $expired; Deleted = $deleted }
    }
}

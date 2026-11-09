#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$LogPath,
    [Parameter(Mandatory)][string]$OutputPath,
    [switch]$Failure
)
$ErrorActionPreference = 'Stop'
$parts = @{}
$digest = $null
$total = 0
$prefix = if ($Failure) { 'FTW_BASELINE_FAILURE' } else { 'FTW_BASELINE_RECEIPT' }
foreach ($line in Get-Content -LiteralPath $LogPath) {
    # Handles Docker text and Railway JSON log exports without evaluating input.
    $message = $line
    if ($line.TrimStart().StartsWith('{')) {
        try { $row = $line | ConvertFrom-Json; if ($row.message) { $message = $row.message } } catch { continue }
    }
    if ($message -notmatch ("\A$prefix " + '([0-9]+)/([0-9]+) ([a-f0-9]{64}) ([A-Za-z0-9+/=]{1,2048})\z')) { continue }
    $index = [int]$Matches[1]
    $count = [int]$Matches[2]
    $hash = $Matches[3]
    $data = $Matches[4]
    if ($count -lt 1 -or $count -gt 171 -or $index -lt 1 -or $index -gt $count) { throw 'Invalid receipt chunk index' }
    if ($digest -and ($digest -ne $hash -or $total -ne $count)) { throw 'Mixed baseline receipts' }
    if ($parts.ContainsKey($index) -and $parts[$index] -cne $data) { throw 'Conflicting receipt chunk' }
    $digest = $hash
    $total = $count
    $parts[$index] = $data
}
if ($total -eq 0 -or $parts.Count -ne $total) { throw 'Incomplete baseline receipt' }
$bytes = [Convert]::FromBase64String(((1..$total | ForEach-Object { $parts[$_] }) -join ''))
if ($bytes.Length -gt 262144 -or [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant() -ne $digest) {
    throw 'Baseline receipt checksum mismatch'
}
$receipt = [Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json
if ($Failure) {
    if ($receipt.phase -notin @('private-dns','fixture-marker-and-readiness','load','receipt','unknown') -or
        $receipt.category -notin @('timeout','database-connection','database-error','network','protocol-rejected','configuration','assertion','other')) {
        throw 'Invalid baseline failure classification'
    }
} elseif ($receipt.backendCount -ne 1 -or $receipt.concurrentRooms -ne 20 -or $receipt.configuredDurationSeconds -ne 600 -or
    $receipt.selectionCount -ne 60000 -or $receipt.fullAcceptanceProfile -ne $true -or
    @($receipt.selectionsByRoomSlot).Count -ne 20 -or @($receipt.selectionsByRoomSlot | Where-Object { $_ -ne 3000 }).Count -ne 0 -or
    $receipt.actualSelectionsPerSecond -lt 95 -or $receipt.elapsedSeconds -gt 631) {
    throw 'Receipt does not satisfy the full one-backend baseline'
}
[IO.File]::WriteAllBytes([IO.Path]::GetFullPath($OutputPath), $bytes)
Write-Output "Verified baseline export SHA-256 $digest"

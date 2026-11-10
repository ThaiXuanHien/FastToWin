#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$LogPath,
    [Parameter(Mandatory)][string]$OutputPath,
    [switch]$Failure,
    [switch]$Latency,
    [switch]$Diagnostic
)
$ErrorActionPreference = 'Stop'
if (([int]$Failure.IsPresent + [int]$Latency.IsPresent + [int]$Diagnostic.IsPresent) -gt 1) { throw 'Select one export type' }
$parts = @{}
$digest = $null
$total = 0
$prefix = if ($Latency) { 'FTW_BASELINE_LATENCY' } elseif ($Failure) { 'FTW_BASELINE_FAILURE' } else { 'FTW_BASELINE_RECEIPT' }
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
if ($Latency) {
    function Test-Numeric($Value) {
        return ($Value -is [int] -or $Value -is [long] -or $Value -is [double] -or $Value -is [decimal]) -and
            [double]::IsFinite([double]$Value) -and $Value -ge 0
    }
    $probes = @('poolAcquire','selectRoundTrip','markerUpdateRoundTrip','commitRoundTrip','readinessRoundTrip')
    $rootKeys = @('scope','expectedIterations','complete','backendBeforeLoadMetrics','fixtureDatabaseMetrics') + $probes
    if ($receipt.scope -cne 'preflight-fixture-probes-and-workload-timings' -or $receipt.expectedIterations -ne 12 -or
        $receipt.complete -isnot [bool] -or @($receipt.PSObject.Properties.Name | Where-Object { $_ -notin $rootKeys }).Count) {
        throw 'Invalid latency export scope'
    }
    foreach ($name in $probes) {
        $stat = $receipt.$name
        if ($stat -isnot [pscustomobject] -or @($stat.PSObject.Properties.Name | Where-Object { $_ -notin @('count','meanMillis','maxMillis','p95Millis','failures') }).Count -or
            -not (Test-Numeric $stat.count) -or $stat.count -ne [Math]::Truncate($stat.count) -or $stat.count -gt 32 -or
            -not (Test-Numeric $stat.failures) -or $stat.failures -ne [Math]::Truncate($stat.failures) -or $stat.failures -gt $stat.count -or
            -not (Test-Numeric $stat.meanMillis) -or -not (Test-Numeric $stat.maxMillis) -or $stat.meanMillis -gt $stat.maxMillis) {
            throw 'Invalid latency summary'
        }
        if (($stat.count -eq 0 -and ($null -ne $stat.p95Millis -or $stat.meanMillis -ne 0 -or $stat.maxMillis -ne 0)) -or
            ($stat.count -gt 0 -and (-not (Test-Numeric $stat.p95Millis) -or $stat.p95Millis -gt $stat.maxMillis)) -or
            ($receipt.complete -and ($stat.count -ne 12 -or $stat.failures -ne 0))) { throw 'Invalid latency probe completeness' }
    }
    $allowedMetrics = @(
        'fasttowin_uptime_seconds','fasttowin_websocket_connections','fasttowin_websocket_connections_total',
        'fasttowin_websocket_messages_total','fasttowin_websocket_rate_limited_total',
        'fasttowin_jvm_memory_used_bytes','fasttowin_jvm_memory_max_bytes','fasttowin_room_ownership_available_processors',
        'fasttowin_room_ownership_takeovers_total','fasttowin_room_ownership_fence_rejections_total',
        'fasttowin_room_ownership_resyncs_total','fasttowin_room_ownership_settlement_failures_total',
        'fasttowin_room_ownership_oldest_queued_event_seconds','fasttowin_room_ownership_pool_active_connections','fasttowin_room_ownership_pool_max_connections'
    )
    foreach ($stage in @('pool_acquire','transaction_setup','transaction_body','statement_execute','row_decode','commit','rollback','setup_and_first_statement','read_transaction')) {
        foreach ($suffix in @('count','sum','max','p95_upper_bound','failures_total')) { $allowedMetrics += "fasttowin_room_ownership_db_${stage}_seconds_$suffix" }
    }
    foreach ($map in @($receipt.backendBeforeLoadMetrics,$receipt.fixtureDatabaseMetrics)) {
        if ($map -isnot [pscustomobject]) { throw 'Invalid latency metrics' }
        foreach ($property in $map.PSObject.Properties) {
            if ($property.Name -notin $allowedMetrics -or -not (Test-Numeric $property.Value)) { throw 'Invalid latency metric label or value' }
        }
    }
} elseif ($Failure) {
    if ($receipt.phase -notin @('private-dns','fixture-marker-and-readiness','load','receipt','unknown') -or
        $receipt.category -notin @('timeout','database-connection','database-error','network','protocol-rejected','configuration','assertion','other')) {
        throw 'Invalid baseline failure classification'
    }
} elseif ($Diagnostic) {
    if ($receipt.backendCount -ne 1 -or $receipt.concurrentRooms -ne 20 -or $receipt.configuredDurationSeconds -ne 60 -or
        $receipt.fullAcceptanceProfile -isnot [bool] -or $receipt.fullAcceptanceProfile -or $receipt.selectionCount -ne 6000 -or
        @($receipt.selectionsByRoomSlot).Count -ne 20 -or @($receipt.selectionsByRoomSlot | Where-Object { $_ -ne 300 }).Count -or
        -not [double]::IsFinite([double]$receipt.elapsedSeconds) -or $receipt.elapsedSeconds -le 0 -or
        -not [double]::IsFinite([double]$receipt.actualSelectionsPerSecond) -or $receipt.actualSelectionsPerSecond -le 0) {
        throw 'Invalid short local diagnostic; never a full baseline pass'
    }
} elseif ($receipt.backendCount -ne 1 -or $receipt.concurrentRooms -ne 20 -or $receipt.configuredDurationSeconds -ne 600 -or
    $receipt.selectionCount -ne 60000 -or $receipt.fullAcceptanceProfile -ne $true -or
    @($receipt.selectionsByRoomSlot).Count -ne 20 -or @($receipt.selectionsByRoomSlot | Where-Object { $_ -ne 3000 }).Count -ne 0 -or
    $receipt.actualSelectionsPerSecond -lt 95 -or $receipt.elapsedSeconds -gt 631) {
    throw 'Receipt does not satisfy the full one-backend baseline'
}
[IO.File]::WriteAllBytes([IO.Path]::GetFullPath($OutputPath), $bytes)
Write-Output "Verified baseline export SHA-256 $digest"

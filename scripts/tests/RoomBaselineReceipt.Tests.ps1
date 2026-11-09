#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$reader = Join-Path $root 'scripts/read-room-baseline-receipt.ps1'
$fixture = Join-Path $root ('.artifacts/baseline-reader-tests/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture -Force | Out-Null
$json = @{phase='load';category='timeout';progress=@{elapsedSeconds=630.0;selectionsByRoomSlot=@(1000L)*20;completedMatches=400;commandAttempts=20000};counts=@{matches=400;settlements=400};metrics=@{fasttowin_room_ownership_pool_active_connections=10}} | ConvertTo-Json -Depth 5 -Compress
$bytes = [Text.Encoding]::UTF8.GetBytes($json)
$hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
$data = [Convert]::ToBase64String($bytes)
# Deliberately split into two real chunks to exercise ordering/loss handling.
$first = 'FTW_BASELINE_FAILURE 1/2 ' + $hash + ' ' + $data.Substring(0, 64)
$second = 'FTW_BASELINE_FAILURE 2/2 ' + $hash + ' ' + $data.Substring(64)
$cases = @(
    @{name='plain';accepted=$true;lines=@($first,$second)},
    @{name='reordered-duplicate';accepted=$true;lines=@($second,$first,$first)},
    @{name='json-log';accepted=$true;lines=@(@{message=$first}|ConvertTo-Json -Compress;@{message=$second}|ConvertTo-Json -Compress)},
    @{name='missing';accepted=$false;lines=@($first)},
    @{name='corrupt';accepted=$false;lines=@($first.Replace($hash,('0'*64)),$second.Replace($hash,('0'*64)))},
    @{name='mixed';accepted=$false;lines=@($first,$second.Replace($hash,('0'*64)))},
    @{name='conflicting-duplicate';accepted=$false;lines=@($first,$first.Substring(0,$first.Length-1)+'A',$second)}
)
foreach ($case in $cases) {
    $inputPath = Join-Path $fixture ($case.name + '.log')
    $outputPath = Join-Path $fixture ($case.name + '.json')
    $case.lines | Set-Content -LiteralPath $inputPath -Encoding utf8NoBOM
    $accepted = $false
    try { & $reader -Failure -LogPath $inputPath -OutputPath $outputPath | Out-Null; $accepted=$true }
    catch { if ($case.accepted) { throw } }
    if ($accepted -ne $case.accepted) { throw "Wrong reader result for $($case.name)" }
    if ($accepted -and (Get-FileHash -LiteralPath $outputPath).Hash.ToLowerInvariant() -ne $hash) { throw 'Export bytes changed' }
}
Write-Output 'PASS: seven failure-export cases preserve bytes and reject missing, corrupt, mixed or conflicting chunks.'

$stats = @{count=12;meanMillis=1.5;maxMillis=2.0;p95Millis=2.0;failures=0}
$latency = @{
    scope='preflight-fixture-probes-and-workload-timings';expectedIterations=12;complete=$true
    poolAcquire=$stats;selectRoundTrip=$stats;markerUpdateRoundTrip=$stats;commitRoundTrip=$stats;readinessRoundTrip=$stats
    backendBeforeLoadMetrics=@{fasttowin_room_ownership_available_processors=3}
    fixtureDatabaseMetrics=@{fasttowin_room_ownership_db_pool_acquire_seconds_count=5}
}
$valid = $latency | ConvertTo-Json -Depth 6 -Compress
$latencyCases = @(
    @{name='latency-valid';accepted=$true;json=$valid},
    @{name='latency-root-secret';accepted=$false;json=($valid.Substring(0,$valid.Length-1)+',"rawSql":"secret"}')},
    @{name='latency-metric-secret';accepted=$false;json=$valid.Replace('fasttowin_room_ownership_available_processors','player_secret')},
    @{name='latency-non-numeric';accepted=$false;json=$valid.Replace('"meanMillis":1.5','"meanMillis":"secret"')},
    @{name='latency-invalid-scope';accepted=$false;json=$valid.Replace('preflight-fixture-probes-and-workload-timings','secret-scope')},
    @{name='latency-incomplete-as-complete';accepted=$false;json=$valid.Replace('"count":12','"count":11')}
)
foreach ($case in $latencyCases) {
    $payload = [Text.Encoding]::UTF8.GetBytes($case.json)
    $digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($payload)).ToLowerInvariant()
    $encoded = [Convert]::ToBase64String($payload)
    $total = [int][Math]::Ceiling($encoded.Length/2048)
    $lines = @(for ($index=0; $index -lt $total; $index++) {
        'FTW_BASELINE_LATENCY '+($index+1)+'/'+$total+' '+$digest+' '+$encoded.Substring($index*2048,[Math]::Min(2048,$encoded.Length-$index*2048))
    })
    $inputPath = Join-Path $fixture ($case.name+'.log')
    $outputPath = Join-Path $fixture ($case.name+'.json')
    $lines | Set-Content -LiteralPath $inputPath -Encoding utf8NoBOM
    $accepted=$false
    try { & $reader -Latency -LogPath $inputPath -OutputPath $outputPath | Out-Null; $accepted=$true }
    catch { if ($case.accepted) { throw } }
    if ($accepted -ne $case.accepted) { throw "Wrong latency reader result: $($case.name)" }
    if ($accepted -and (Get-FileHash -LiteralPath $outputPath).Hash.ToLowerInvariant() -ne $digest) { throw 'Latency export bytes changed' }
}
Write-Output 'PASS: six latency-export cases validate numeric fields, fixed labels and probe completeness.'

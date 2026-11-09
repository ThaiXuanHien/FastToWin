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

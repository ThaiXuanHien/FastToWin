#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BackendContext,
    [Parameter(Mandatory)][string]$BackendManifest,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$BackendManifestSha256,
    [switch]$RailwayLimits,
    [switch]$Profile,
    [switch]$CloudDatabaseLatency,
    [switch]$DiagnosticOnly,
    [ValidateSet('0.5', '2')][string]$PostgresCpu = '0.5',
    [ValidateSet('1', '2', '3')][string]$BackendCpu = '1'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
$context = (Resolve-Path -LiteralPath $BackendContext).Path
$manifestPath = (Resolve-Path -LiteralPath $BackendManifest).Path
if ((Get-FileHash -LiteralPath $manifestPath).Hash.ToLowerInvariant() -ne $BackendManifestSha256) { throw 'Backend manifest changed' }
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
$contextFiles = @(Get-ChildItem -LiteralPath $context -File -Recurse)
if ($contextFiles.Count -ne $manifest.files.Count) { throw 'Backend context file count changed' }
foreach ($file in $manifest.files) {
    $path = [IO.Path]::GetFullPath((Join-Path $context $file.path))
    if (-not $path.StartsWith($context + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        (Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint -or
        (Get-Item -LiteralPath $path).Length -ne $file.bytes -or
        (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -ne $file.sha256) { throw 'Frozen backend file changed' }
}
$endpoint = if ($env:DOCKER_CONTEXT) {
    $resolved = & docker context inspect $env:DOCKER_CONTEXT --format '{{.Endpoints.docker.Host}}'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve Docker context' }
    $resolved -join ''
} elseif ($env:DOCKER_HOST) { $env:DOCKER_HOST } else {
    $resolved = & docker context inspect --format '{{.Endpoints.docker.Host}}'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve Docker context' }
    $resolved -join ''
}
if ($endpoint -notmatch '\A(npipe:////\./pipe/[a-zA-Z0-9_.-]+|unix:///[a-zA-Z0-9_./-]+)\z') { throw 'Local Docker endpoint required' }
$fixtureId = [guid]::NewGuid().ToString()
$project = 'room-pilot-' + $fixtureId.Replace('-', '').Substring(0, 12)
$out = Join-Path $root ".artifacts/room-ownership-staging-baseline/$project"
$runnerContext = Join-Path $out 'runner'
New-Item -ItemType Directory -Path "$runnerContext/classes", "$runnerContext/lib" | Out-Null
$saved = @{}
$fixtureConfigured = $false
$compose = Join-Path $root 'deploy/room-ownership/compose.staging-baseline.yaml'
$composeArguments = @('--file', $compose)
if ($Profile -and -not $RailwayLimits) { throw 'Profiling requires the explicit Railway resource envelope' }
if (($PostgresCpu -ne '0.5' -or $BackendCpu -ne '1') -and -not $Profile) { throw 'The CPU control requires local profiling' }
if ($RailwayLimits) { $composeArguments += @('--file', (Join-Path $root 'deploy/room-ownership/compose.staging-limits.yaml')) }
if ($CloudDatabaseLatency) {
    if (-not $Profile) { throw 'DB latency reproduction requires local profiling and resource limits' }
    $composeArguments += @('--file', (Join-Path $root 'deploy/room-ownership/compose.database-latency.yaml'))
}
$backendImage = "${project}-backend:local"
$runnerImage = "${project}-runner:local"
$summary = [ordered]@{ sourceRevision = $manifest.sourceRevision; backendManifestSha256 = $BackendManifestSha256; baselineAccepted = $false; cleanupVerified = $false }
$summary.railwayLimits = $RailwayLimits.IsPresent
$summary.profile = $Profile.IsPresent
$summary.databaseLatencyProfile = if ($CloudDatabaseLatency) { 'backend-1ms-fixture-3ms-downstream' } else { 'none' }
$summary.diagnosticOnly = $DiagnosticOnly.IsPresent
$summary.configuredDurationSeconds = if ($DiagnosticOnly) { 60 } else { 600 }
$summary.postgresCpu = if ($RailwayLimits) { [double]::Parse($PostgresCpu, [Globalization.CultureInfo]::InvariantCulture) } else { $null }
$summary.backendCpu = if ($RailwayLimits) { [double]::Parse($BackendCpu, [Globalization.CultureInfo]::InvariantCulture) } else { $null }
$summary.resourceEnvelope = if (-not $RailwayLimits) { 'uncapped-local' } elseif ($PostgresCpu -eq '0.5' -and $BackendCpu -eq '1') { 'railway' } else { 'local-cpu-control' }
$runnerProcess = $null
$runnerCollected = $false
$profileCollected = $false
function Set-E([string]$Name, [string]$Value) {
    $saved[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}
function D([string[]]$Arguments) {
    $result = & docker --host $endpoint @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Local fixture Docker operation failed: $($Arguments[0])" }
    return $result
}
function C([string[]]$Arguments) { D (@('compose', '--project-name', $project) + $composeArguments + $Arguments) }
function Hex { [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant() }
function Record-ProfileError([string]$Stage) {
    # Store only a fixed stage tag. Native error text may contain fixture data.
    $Stage | Add-Content -LiteralPath (Join-Path $out 'profile-errors.log') -Encoding utf8NoBOM
}
function Save-Profile {
    if (-not $Profile -or $script:profileCollected) { return }
    $script:profileCollected = $true
    try {
        $queryStats = @'
SELECT json_agg(r) FROM (SELECT queryid,calls,total_exec_time,mean_exec_time,rows,shared_blks_hit,shared_blks_read,shared_blk_read_time,shared_blk_write_time,wal_bytes,
CASE WHEN query ILIKE '%room_ownership_pilot_rooms%' THEN 'ownership-rooms' WHEN query ILIKE '%room_ownership_pilot_events%' THEN 'ownership-events' WHEN query ILIKE '%room_ownership_pilot%' THEN 'ownership-other' ELSE 'other' END AS category
FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 25) r;
'@
        C @('exec','-T','postgres','psql','-At','-U','fasttowin_room_pilot','-d','fasttowin_room_pilot','-c',$queryStats) | Set-Content -LiteralPath (Join-Path $out 'postgres-statements.json') -Encoding utf8NoBOM
        C @('exec','-T','postgres','psql','-At','-U','fasttowin_room_pilot','-d','fasttowin_room_pilot','-c','SELECT row_to_json(w) FROM (SELECT wal_records,wal_bytes,wal_buffers_full FROM pg_stat_wal) w;') | Set-Content -LiteralPath (Join-Path $out 'postgres-wal.json') -Encoding utf8NoBOM
    } catch { Record-ProfileError 'final-postgres-statistics' }
    try {
        C @('stop','--timeout','30','backend-a') | Out-Null
        $backendId = (C @('ps','-aq','backend-a')) -join ''
        D @('cp',"${backendId}:/tmp/baseline.jfr",(Join-Path $out 'baseline.jfr')) | Out-Null
    } catch { Record-ProfileError 'final-jfr-copy' }
}
try {
    Write-Output "Preparing baseline package $project"
    & ./gradlew.bat :server:roomOwnershipAcceptanceClasspath --offline --console=plain *> (Join-Path $out 'build.log')
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance classpath build failed' }
    # The runner reuses the reviewed backend libraries, never a newly rebuilt server.
    $serverFile = @($manifest.files | Where-Object { $_.path -match '/server[^/]*\.jar$' })
    if ($serverFile.Count -ne 1 -or (Get-FileHash 'server/build/libs/server.jar').Hash.ToLowerInvariant() -ne $serverFile[0].sha256) {
        throw 'Runner server differs from frozen backend'
    }
    Copy-Item -Path (Join-Path $context 'distribution/lib/*') -Destination "$runnerContext/lib"
    Copy-Item -Path (Join-Path $root 'server/build/classes/kotlin/test/*') -Destination "$runnerContext/classes" -Recurse
    $classpath = (Get-Content -Raw 'server/build/room-ownership-acceptance-classpath.txt').Trim().Split([IO.Path]::PathSeparator)
    foreach ($entry in $classpath) {
        if ([IO.Path]::GetExtension($entry) -ne '.jar') { continue }
        $target = Join-Path "$runnerContext/lib" ([IO.Path]::GetFileName($entry))
        if (Test-Path -LiteralPath $target) {
            if ((Get-FileHash -LiteralPath $entry).Hash -ne (Get-FileHash -LiteralPath $target).Hash) { throw 'Runtime library collision' }
        } else { Copy-Item -LiteralPath $entry -Destination $target }
    }
    Copy-Item -LiteralPath 'deploy/room-ownership/staging-runner.Dockerfile' -Destination "$runnerContext/Dockerfile"
    $files = @(Get-ChildItem -LiteralPath $runnerContext -Recurse -File | Sort-Object FullName | ForEach-Object {
        [ordered]@{ path = [IO.Path]::GetRelativePath($runnerContext, $_.FullName).Replace('\', '/'); bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant() }
    })
    [ordered]@{ sourceRevision = ((& git rev-parse HEAD) -join ''); backendManifestSha256 = $BackendManifestSha256; files = $files } |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $out 'runner-manifest.json') -Encoding utf8NoBOM
    & git diff --binary HEAD -- server/src/main/kotlin/com/hienthai/fastowin/server/ownership server/src/test/kotlin/com/hienthai/fastowin/server/ownership deploy/room-ownership scripts > (Join-Path $out 'tested.patch')
    $summary.runnerManifestSha256 = (Get-FileHash (Join-Path $out 'runner-manifest.json')).Hash.ToLowerInvariant()
    $sourceFiles = @(
        'scripts/room-ownership-staging-baseline.ps1', 'scripts/read-room-baseline-receipt.ps1',
        'scripts/tests/RoomBaselineReceipt.Tests.ps1',
        'deploy/room-ownership/compose.staging-baseline.yaml', 'deploy/room-ownership/staging-runner.Dockerfile',
        'deploy/room-ownership/compose.staging-limits.yaml',
        'deploy/room-ownership/compose.database-latency.yaml', 'deploy/room-ownership/toxiproxy-database-latency.json',
        'scripts/tests/RoomDatabaseLatency.Tests.ps1',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RailwayRoomSmoke.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RailwayRoomBaseline.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RailwayBaselineFailure.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RailwayBaselineFailureTest.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RailwayBaselineLatency.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RailwayBaselineLatencyTest.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipDatabaseTimingsTest.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/PostgresRoomOwnershipRepositoryTest.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/JdbcCallRecorder.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipLocalDiagnostic.kt',
        'server/src/main/kotlin/com/hienthai/fastowin/server/ownership/PostgresRoomOwnershipRepository.kt',
        'server/src/main/kotlin/com/hienthai/fastowin/server/ownership/OwnedRoomEngine.kt',
        'server/src/main/kotlin/com/hienthai/fastowin/server/ownership/RoomEventDelivery.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RoomEventDeliveryTest.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/OwnedRoomEngineTest.kt',
        'server/src/main/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipMetrics.kt',
        'server/src/main/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipDatabaseTimings.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipAcceptanceMain.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RoomOwnershipLoadAcceptance.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RailwayRoomSmokeSettingsTest.kt'
    )
    $sourceEntries = @(foreach ($source in $sourceFiles) {
        $target = Join-Path "$out/tested-source" $source
        New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $target
        [ordered]@{ path = $source; sha256 = (Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant() }
    })
    [ordered]@{ baseRevision = ((& git rev-parse HEAD) -join ''); files = $sourceEntries } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $out 'tested-source/manifest.json') -Encoding utf8NoBOM
    $summary.sourceManifestSha256 = (Get-FileHash (Join-Path $out 'tested-source/manifest.json')).Hash.ToLowerInvariant()
    Set-E 'FTW_PILOT_NAME' $project
    Set-E 'FTW_PILOT_BACKEND_IMAGE' $backendImage
    Set-E 'FTW_PILOT_RUNNER_IMAGE' $runnerImage
    Set-E 'FTW_PILOT_IPV6_SUBNET' ('fd' + (Hex).Substring(0, 2) + ':' + (Hex).Substring(0, 4) + ':' + (Hex).Substring(0, 4) + '::/64')
    Set-E 'FTW_PILOT_DEADLINE_UTC' ([DateTime]::UtcNow.AddHours(2).ToString('o'))
    Set-E 'FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID' $fixtureId
    Set-E 'FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS' ((1..40 | ForEach-Object { [guid]::NewGuid().ToString() }) -join ',')
    Set-E 'FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD' (Hex)
    Set-E 'FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD' ('Pilot1!' + (Hex))
    Set-E 'FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET' ([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)))
    if ($Profile) {
        # The profile changes PostgreSQL instrumentation and JVM recording only.
        $profileOverride = Join-Path $out 'profile.compose.yaml'
        @"
services:
  postgres:
    cpus: $PostgresCpu
    command: [postgres, -c, shared_preload_libraries=pg_stat_statements, -c, track_io_timing=on]
  backend-a:
    cpus: $BackendCpu
    environment:
      JAVA_OPTS: -Xms64m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError -XX:StartFlightRecording=settings=/opt/profile/baseline.jfc,filename=/tmp/baseline.jfr,dumponexit=true,maxsize=32m
    volumes:
      - type: bind
        source: '$(Join-Path $out 'baseline.jfc')'
        target: /opt/profile/baseline.jfc
        read_only: true
"@ | Set-Content -LiteralPath $profileOverride -Encoding utf8NoBOM
        $composeArguments += @('--file', $profileOverride)
    }
    $fixtureConfigured = $true
    foreach ($pair in @(@($backendImage, $context), @($runnerImage, $runnerContext))) {
        D @('build', '--pull=false', '--network', 'none', '--label', "fasttowin.baseline=$project", '-t', $pair[0], $pair[1]) | Out-Null
    }
    if ($Profile) {
        [xml]$configuration = (D @('run','--rm','--network','none','--entrypoint','cat',$backendImage,'/opt/java/openjdk/lib/jfr/profile.jfc')) -join "`n"
        foreach ($event in $configuration.configuration.event) {
            if ($event.name -in @('jdk.InitialEnvironmentVariable','jdk.InitialSystemProperty','jdk.JVMInformation','jdk.SystemProcess')) {
                foreach ($setting in $event.setting) { if ($setting.name -eq 'enabled') { $setting.InnerText = 'false' } }
            }
        }
        $configuration.Save((Join-Path $out 'baseline.jfc'))
    }
    C @('up', '-d', '--wait', '--wait-timeout', '120', 'postgres', 'redis') | Out-Null
    if ($CloudDatabaseLatency) {
        C @('up', '-d', 'database-latency') | Out-Null
        $proxyReady = $false
        for ($attempt = 0; $attempt -lt 20; $attempt++) {
            try { C @('exec', '-T', 'database-latency', '/toxiproxy-cli', 'inspect', 'backend') | Out-Null; $proxyReady = $true; break }
            catch { Start-Sleep -Milliseconds 250 }
        }
        if (-not $proxyReady) { throw 'DB delay proxy did not become ready' }
        foreach ($path in @(@('backend', '1'), @('fixture', '3'))) {
            C @('exec', '-T', 'database-latency', '/toxiproxy-cli', 'toxic', 'add', '-t', 'latency', '-a', "latency=$($path[1])", '-a', 'jitter=0', $path[0]) | Out-Null
            C @('exec', '-T', 'database-latency', '/toxiproxy-cli', 'inspect', $path[0]) |
                Set-Content -LiteralPath (Join-Path $out "database-latency-$($path[0]).json") -Encoding utf8NoBOM
        }
    }
    C @('run', '--rm', '--no-deps', 'runner', '--seed') | Set-Content -LiteralPath (Join-Path $out 'seed.log')
    if ($Profile) { C @('exec', '-T', 'postgres', 'psql', '-U', 'fasttowin_room_pilot', '-d', 'fasttowin_room_pilot', '-c', 'CREATE EXTENSION pg_stat_statements;') | Out-Null }
    C @('up', '-d', '--wait', '--wait-timeout', '120', 'backend-a', 'backend-b') | Out-Null
    # Both peers resolve at startup. B is stopped before baseline traffic begins.
    C @('stop', '--timeout', '30', 'backend-b') | Out-Null
    $active = @(C @('ps', '--services', '--status', 'running'))
    if ('backend-b' -in $active -or 'backend-a' -notin $active) { throw 'Baseline requires exactly one active backend' }
    if ($RailwayLimits) {
        $expected = @{postgres=@(([long]($summary.postgresCpu * 1000000000)),512000000);redis=@(250000000,250000000);'backend-a'=@(([long]($summary.backendCpu * 1000000000)),1000000000)}
        if ($CloudDatabaseLatency) { $expected['database-latency'] = @(1000000000,128000000) }
        foreach ($service in $expected.Keys) {
            $id = (C @('ps', '-q', $service)) -join ''
            $limits = (D @('inspect','--format','{{json .HostConfig}}',$id)) | ConvertFrom-Json
            if ($limits.NanoCpus -ne $expected[$service][0] -or $limits.Memory -ne $expected[$service][1] -or $limits.MemorySwap -ne $limits.Memory) { throw 'Fixture resource ceiling differs from Railway' }
        }
        $summary.resourceCeilingsVerified = $true
    }
    Write-Output "Running private-DNS load: one backend, twenty rooms, $($summary.configuredDurationSeconds) seconds; diagnosticOnly=$DiagnosticOnly"
    $log = Join-Path $out 'runner.log'
    $start = [Diagnostics.ProcessStartInfo]::new('docker')
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $runArguments = @('run','--name',"$project-runner",'--no-deps','-e',"FTW_PILOT_BACKEND_A_HOST=$project-a.railway.internal")
    if ($DiagnosticOnly) { $runArguments += @('--entrypoint','timeout') }
    $runArguments += 'runner'
    if ($DiagnosticOnly) { $runArguments += @('900','java','-Xmx384m','-cp','classes:lib/*','com.hienthai.fastowin.server.ownership.RoomOwnershipLocalDiagnosticKt') }
    foreach ($argument in (@('--host',$endpoint,'compose','--project-name',$project) + $composeArguments + $runArguments)) {
        $start.ArgumentList.Add($argument)
    }
    $runnerProcess = [Diagnostics.Process]::Start($start)
    $stdout = $runnerProcess.StandardOutput.ReadToEndAsync()
    $stderr = $runnerProcess.StandardError.ReadToEndAsync()
    $profileStarted = [Diagnostics.Stopwatch]::StartNew()
    while (-not $runnerProcess.WaitForExit(10000)) {
        if ($Profile) {
            try {
                $ids = @(C @('ps','-q','postgres','redis','backend-a'))
                if ($ids.Count) { D (@('stats','--no-stream','--format','{{json .}}') + $ids) | Add-Content -LiteralPath (Join-Path $out 'docker-resources.jsonl') -Encoding utf8NoBOM }
            } catch { Record-ProfileError 'periodic-docker-statistics' }
            $statistics = 'SELECT json_build_object(''xact_commit'',xact_commit,''blks_read'',blks_read,''blks_hit'',blks_hit,''blk_read_time'',blk_read_time,''blk_write_time'',blk_write_time,''temp_bytes'',temp_bytes,''deadlocks'',deadlocks) FROM pg_stat_database WHERE datname=current_database();'
            try { C @('exec','-T','postgres','psql','-At','-U','fasttowin_room_pilot','-d','fasttowin_room_pilot','-c',$statistics) | Add-Content -LiteralPath (Join-Path $out 'postgres-resources.jsonl') -Encoding utf8NoBOM }
            catch { Record-ProfileError 'periodic-postgres-statistics' }
        }
        if ($profileStarted.Elapsed.TotalSeconds -gt 1000) { throw 'Local baseline process exceeded its bounded runtime' }
    }
    $runnerExit = $runnerProcess.ExitCode
    $stdout.GetAwaiter().GetResult() | Set-Content -LiteralPath $log -Encoding utf8NoBOM
    $stderr.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $out 'runner-stderr.log') -Encoding utf8NoBOM
    $runnerCollected = $true
    D @('cp', "${project}-runner:/opt/runner/receipts/.", $out) | Out-Null
    Save-Profile
    & (Join-Path $PSScriptRoot 'read-room-baseline-receipt.ps1') -Latency -LogPath $log -OutputPath (Join-Path $out 'exported-latency.json') | Out-Null
    if ((Get-FileHash (Join-Path $out 'baseline-latency.json')).Hash -ne (Get-FileHash (Join-Path $out 'exported-latency.json')).Hash) { throw 'Latency export differs from runner file' }
    $summary.latencyExportVerified = $true
    $summary.latencyProbeComplete = (Get-Content -Raw (Join-Path $out 'exported-latency.json') | ConvertFrom-Json).complete
    if ($runnerExit -ne 0) {
        & (Join-Path $PSScriptRoot 'read-room-baseline-receipt.ps1') -Failure -LogPath $log -OutputPath (Join-Path $out 'exported-failure.json') | Out-Null
        if ((Get-FileHash (Join-Path $out 'baseline-failure.json')).Hash -ne (Get-FileHash (Join-Path $out 'exported-failure.json')).Hash) { throw 'Failure export differs from runner file' }
        $summary.failureExportVerified = $true
        $diagnostic = Get-Content -Raw (Join-Path $out 'exported-failure.json') | ConvertFrom-Json
        $summary.failureCategory = $diagnostic.category
        $summary.failurePhase = $diagnostic.phase
        $summary.progress = $diagnostic.progress
        throw "Baseline runner failed. Private diagnostics are in $out"
    }
    & (Join-Path $PSScriptRoot 'read-room-baseline-receipt.ps1') -Diagnostic:$DiagnosticOnly -LogPath $log -OutputPath (Join-Path $out 'exported-load.json') | Out-Null
    if ((Get-FileHash (Join-Path $out 'load.json')).Hash -ne (Get-FileHash (Join-Path $out 'exported-load.json')).Hash) { throw 'Exported receipt differs from runner file' }
    $exported = Get-Content -Raw (Join-Path $out 'exported-load.json')
    $sensitive = @($fixtureId) + $env:FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS.Split(',') + @(
        $env:FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD, $env:FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD,
        $env:FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET
    )
    if (@($sensitive | Where-Object { $exported.Contains($_) }).Count) { throw 'Receipt contains a fixture identifier or credential' }
    $receipt = Get-Content -Raw (Join-Path $out 'load.json') | ConvertFrom-Json
    $summary.baselineAccepted = -not $DiagnosticOnly
    $summary.diagnosticCompleted = $DiagnosticOnly.IsPresent
    $summary.selectionCount = $receipt.selectionCount
    $summary.actualSelectionsPerSecond = $receipt.actualSelectionsPerSecond
    $summary.receiptSha256 = (Get-FileHash (Join-Path $out 'load.json')).Hash.ToLowerInvariant()
} finally {
    $cleanupErrors = @()
    # Infrastructure/telemetry failures must not discard the only diagnostic
    # copy. Stop our runner, drain bounded logs and copy available files first.
    if ($runnerProcess -and -not $runnerCollected) {
        try {
            if (-not $runnerProcess.HasExited) { D @('stop','--time','10',"$project-runner") | Out-Null }
            if (-not $runnerProcess.WaitForExit(5000)) { $runnerProcess.Kill() }
            if ($stdout.Wait(5000)) { $stdout.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $out 'runner.log') -Encoding utf8NoBOM }
            if ($stderr.Wait(5000)) { $stderr.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $out 'runner-stderr.log') -Encoding utf8NoBOM }
            D @('cp',"${project}-runner:/opt/runner/receipts/.",$out) | Out-Null
        } catch { Record-ProfileError 'fallback-runner-collection' }
    }
    if ($runnerProcess) { Save-Profile }
    if ($fixtureConfigured) {
        try { C @('down', '--volumes', '--remove-orphans', '--timeout', '30') | Out-Null } catch { $cleanupErrors += 'compose-down' }
    }
    if ($runnerProcess) { if (-not $runnerProcess.HasExited) { $runnerProcess.Kill() }; $runnerProcess.Dispose() }
    try {
        $ownedImages = @(D @('image', 'ls', '-q', '--filter', "label=fasttowin.baseline=$project") | Select-Object -Unique)
        foreach ($id in $ownedImages) { D @('image', 'rm', $id) | Out-Null }
        foreach ($operation in @(@('ps', '-aq'), @('network', 'ls', '-q'), @('volume', 'ls', '-q'))) {
            if (D ($operation + @('--filter', "label=com.docker.compose.project=$project"))) { throw 'Owned resource remains' }
        }
        if (D @('image', 'ls', '-q', '--filter', "label=fasttowin.baseline=$project")) { throw 'Owned image remains' }
    } catch { $cleanupErrors += 'owned-resource-check' }
    $summary.cleanupVerified = $cleanupErrors.Count -eq 0
    $summary.cleanupErrors = $cleanupErrors
    $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $out 'summary.json') -Encoding utf8NoBOM
    foreach ($entry in $saved.GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process') }
    if ($cleanupErrors.Count) { throw "Fixture cleanup needs attention. See $out" }
}
Write-Output "Load receipt verified and cleanup complete; baselineAccepted=$($summary.baselineAccepted). Receipts: $out"

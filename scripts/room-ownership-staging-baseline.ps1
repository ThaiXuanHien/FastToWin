#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BackendContext,
    [Parameter(Mandatory)][string]$BackendManifest,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$BackendManifestSha256
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
$backendImage = "${project}-backend:local"
$runnerImage = "${project}-runner:local"
$summary = [ordered]@{ sourceRevision = $manifest.sourceRevision; backendManifestSha256 = $BackendManifestSha256; baselineAccepted = $false; cleanupVerified = $false }
function Set-E([string]$Name, [string]$Value) {
    $saved[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}
function D([string[]]$Arguments) {
    $result = & docker --host $endpoint @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Local fixture Docker operation failed: $($Arguments[0])" }
    return $result
}
function C([string[]]$Arguments) { D (@('compose', '--project-name', $project, '--file', $compose) + $Arguments) }
function Hex { [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant() }
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
    & git diff --binary HEAD -- server/src/test/kotlin/com/hienthai/fastowin/server/ownership deploy/room-ownership scripts > (Join-Path $out 'tested.patch')
    $summary.runnerManifestSha256 = (Get-FileHash (Join-Path $out 'runner-manifest.json')).Hash.ToLowerInvariant()
    $sourceFiles = @(
        'scripts/room-ownership-staging-baseline.ps1', 'scripts/read-room-baseline-receipt.ps1',
        'deploy/room-ownership/compose.staging-baseline.yaml', 'deploy/room-ownership/staging-runner.Dockerfile',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RailwayRoomSmoke.kt',
        'server/src/test/kotlin/com/hienthai/fastowin/server/ownership/RailwayRoomBaseline.kt',
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
    $fixtureConfigured = $true
    foreach ($pair in @(@($backendImage, $context), @($runnerImage, $runnerContext))) {
        D @('build', '--pull=false', '--network', 'none', '--label', "fasttowin.baseline=$project", '-t', $pair[0], $pair[1]) | Out-Null
    }
    C @('up', '-d', '--wait', '--wait-timeout', '120', 'postgres', 'redis') | Out-Null
    C @('run', '--rm', '--no-deps', 'runner', '--seed') | Set-Content -LiteralPath (Join-Path $out 'seed.log')
    C @('up', '-d', '--wait', '--wait-timeout', '120', 'backend-a', 'backend-b') | Out-Null
    # Both peers resolve at startup. B is stopped before baseline traffic begins.
    C @('stop', '--timeout', '30', 'backend-b') | Out-Null
    $active = @(C @('ps', '--services', '--status', 'running'))
    if ('backend-b' -in $active -or 'backend-a' -notin $active) { throw 'Baseline requires exactly one active backend' }
    Write-Output 'Running private-DNS baseline: one backend, twenty rooms, six hundred seconds'
    $log = Join-Path $out 'runner.log'
    $runnerOutput = & docker --host $endpoint compose --project-name $project --file $compose run --name "$project-runner" --no-deps -e "FTW_PILOT_BACKEND_A_HOST=$project-a.railway.internal" runner 2>&1
    $runnerExit = $LASTEXITCODE
    $runnerOutput | Set-Content -LiteralPath $log -Encoding utf8NoBOM
    D @('cp', "${project}-runner:/opt/runner/receipts/.", $out) | Out-Null
    if ($runnerExit -ne 0) { throw "Baseline runner failed. Private diagnostics are in $out" }
    & (Join-Path $PSScriptRoot 'read-room-baseline-receipt.ps1') -LogPath $log -OutputPath (Join-Path $out 'exported-load.json') | Out-Null
    if ((Get-FileHash (Join-Path $out 'load.json')).Hash -ne (Get-FileHash (Join-Path $out 'exported-load.json')).Hash) { throw 'Exported receipt differs from runner file' }
    $exported = Get-Content -Raw (Join-Path $out 'exported-load.json')
    $sensitive = @($fixtureId) + $env:FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS.Split(',') + @(
        $env:FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD, $env:FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD,
        $env:FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET
    )
    if (@($sensitive | Where-Object { $exported.Contains($_) }).Count) { throw 'Receipt contains a fixture identifier or credential' }
    $receipt = Get-Content -Raw (Join-Path $out 'load.json') | ConvertFrom-Json
    $summary.baselineAccepted = $true
    $summary.selectionCount = $receipt.selectionCount
    $summary.actualSelectionsPerSecond = $receipt.actualSelectionsPerSecond
    $summary.receiptSha256 = (Get-FileHash (Join-Path $out 'load.json')).Hash.ToLowerInvariant()
} finally {
    $cleanupErrors = @()
    if ($fixtureConfigured) {
        try { C @('down', '--volumes', '--remove-orphans', '--timeout', '30') | Out-Null } catch { $cleanupErrors += 'compose-down' }
    }
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
Write-Output "Baseline accepted and cleanup verified. Receipts: $out"

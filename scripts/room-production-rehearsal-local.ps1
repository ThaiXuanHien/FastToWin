#Requires -Version 7.0
[CmdletBinding()]
param([ValidateSet('Seed', 'Fault', 'Load')][string]$Profile = 'Seed')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runId = [guid]::NewGuid().ToString()
$suffix = $runId.Replace('-', '').Substring(0, 12)
$name = if ($Profile -ceq 'Seed') { "room-prod-$suffix" } else { "room-pilot-$suffix" }
$database = "room_rehearsal_$suffix"
$output = Join-Path $repo ".artifacts/room-production-local/$name"
$context = Join-Path $output 'runner'
$label = "fasttowin.production-rehearsal=$runId"
$tag = "ftw-production-rehearsal:$suffix"
$oldEnvironment = @{}
$created = $false
$primaryFailure = $null

function Docker([string[]]$Arguments) {
    $text = & docker.exe --host $script:endpoint @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Local Docker operation failed ($($Arguments[0]))" }
    return ($text -join [Environment]::NewLine).Trim()
}
function Set-RunVariable([string]$Key, [string]$Value) {
    if (!$oldEnvironment.ContainsKey($Key)) {
        $oldEnvironment[$Key] = [Environment]::GetEnvironmentVariable($Key, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Key, $Value, 'Process')
}
function Hash([string]$File) { (Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash.ToLowerInvariant() }

Push-Location $repo
try {
    foreach ($key in 'DOCKER_HOST', 'DOCKER_CONTEXT') {
        if ([Environment]::GetEnvironmentVariable($key)) { throw 'Unexpected Docker endpoint override' }
    }
    $script:endpoint = (& docker.exe context inspect --format '{{.Endpoints.docker.Host}}').Trim()
    if ($LASTEXITCODE -ne 0 -or $script:endpoint -cnotmatch '^npipe:/{4}\./pipe/[A-Za-z0-9_.-]+$') {
        throw 'Expected a local Docker named pipe'
    }
    if ((Docker @('info', '--format', '{{.OSType}}')) -cne 'linux') { throw 'Linux Docker required' }
    New-Item -ItemType Directory -Path "$context/classes", "$context/lib" | Out-Null
    if ($Profile -cne 'Seed') {
        $sourceRoot = Join-Path $output 'tested-source'
        $launcherSources = @(
            'scripts/room-production-rehearsal-local.ps1',
            'scripts/room-production-rehearsal-local.mjs',
            'scripts/read-room-rehearsal-receipt.mjs',
            'scripts/lib/room-production-supervisor.mjs',
            'scripts/lib/room-supervised-local.mjs'
        )
        $sourceManifest = foreach ($relative in $launcherSources) {
            $original = Get-Item -LiteralPath (Join-Path $repo $relative)
            if ($original.PSIsContainer -or ($original.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                throw 'Rehearsal launcher source must be a regular file'
            }
            $snapshot = Join-Path $sourceRoot $relative
            New-Item -ItemType Directory -Path (Split-Path -Parent $snapshot) -Force | Out-Null
            Copy-Item -LiteralPath $original.FullName -Destination $snapshot
            [ordered]@{ path = $relative; sha256 = Hash $snapshot }
        }
        $sourceManifest | ConvertTo-Json -Depth 4 |
            Set-Content (Join-Path $output 'source-manifest.json') -Encoding utf8NoBOM
    }
    Set-RunVariable 'JAVA_HOME' 'C:/Program Files/Android/Android Studio/jbr'
    Set-RunVariable 'GRADLE_USER_HOME' 'C:/Users/Hien Hue/.gradle'
    $tempDirectory = Join-Path $repo '.artifacts/java-tmp'
    New-Item -ItemType Directory -Path $tempDirectory -Force | Out-Null
    Set-RunVariable 'TEMP' $tempDirectory
    Set-RunVariable 'TMP' $tempDirectory
    & ./gradlew.bat :server:roomOwnershipAcceptanceClasspath :server:installDist --offline --no-daemon --no-configuration-cache --max-workers=1 --console=plain *> "$output/compile.log"
    if ($LASTEXITCODE -ne 0) { throw 'Runner compile failed. See compile.log' }

    Copy-Item -Path 'server/build/classes/kotlin/test/*' -Destination "$context/classes" -Recurse
    Copy-Item -Path 'server/build/install/server/lib/*' -Destination "$context/lib"
    $classpath = (Get-Content 'server/build/room-ownership-acceptance-classpath.txt' -Raw).Trim().Split([IO.Path]::PathSeparator)
    foreach ($entry in $classpath) {
        if ([IO.Path]::GetExtension($entry) -cne '.jar') { continue }
        $target = Join-Path "$context/lib" ([IO.Path]::GetFileName($entry))
        if (Test-Path -LiteralPath $target) {
            if ((Hash $entry) -cne (Hash $target)) { throw 'Runner dependency collision' }
        } else { Copy-Item -LiteralPath $entry -Destination $target }
    }
    @'
FROM eclipse-temurin:17-jre-alpine@sha256:c4a33e6ddb738fe4be0a28a613ea0940cec42d25d7263c3e2238c43354cb19b0
RUN addgroup -S runner && adduser -S -G runner runner
WORKDIR /opt/runner
COPY classes/ classes/
COPY lib/ lib/
RUN mkdir receipts && chown runner:runner receipts
USER runner
ENTRYPOINT ["timeout","1800","java","-Xmx384m","-cp","classes:lib/*","com.hienthai.fastowin.server.ownership.ProductionRoomRehearsalKt"]
'@ | Set-Content "$context/Dockerfile" -Encoding utf8NoBOM
    $base = [IO.Path]::GetFullPath($context)
    $files = @(Get-ChildItem -LiteralPath $context -Recurse -File | Sort-Object FullName | ForEach-Object {
        if ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Runner context contains a reparse point' }
        [ordered]@{ path = $_.FullName.Substring($base.Length + 1).Replace([char]92, [char]47)
            bytes = $_.Length; sha256 = Hash $_.FullName }
    })
    $files | ConvertTo-Json -Depth 5 | Set-Content "$output/runner-manifest.json" -Encoding utf8NoBOM
    $manifest = Hash "$output/runner-manifest.json"
    $jar = Hash "$context/lib/server.jar"
    if ($jar -cne (Hash 'server/build/libs/server.jar')) { throw 'Packaged server JAR mismatch' }
    $created = $true
    Docker @('build', '--pull=false', '--network', 'none', '--label', $label, '--tag', $tag, $context) | Set-Content "$output/build.log"
    $image = (Docker @('image', 'inspect', $tag) | ConvertFrom-Json)[0]
    if ($image.Config.User -cne 'runner' -or $image.Config.Entrypoint[-1] -cne 'com.hienthai.fastowin.server.ownership.ProductionRoomRehearsalKt') {
        throw 'Runner image configuration differs'
    }
    if ($Profile -cne 'Seed') {
        & node (Join-Path $sourceRoot 'scripts/room-production-rehearsal-local.mjs') $output $Profile $runId $tag $script:endpoint $repo
        if ($LASTEXITCODE -ne 0) { throw 'Production workload failed. See its classified receipt' }
    } else {
    Docker @('network', 'create', '--internal', '--label', $label, $name) | Out-Null
    Set-RunVariable 'POSTGRES_PASSWORD' ([Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant())
    $pg = "$name-pg"
    Docker @('run', '-d', '--pull', 'never', '--name', $pg, '--label', $label, '--network', $name,
        '--network-alias', "$pg.railway.internal", '--user', 'postgres', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges:true',
        '--cpus', '2', '--memory', '512000000', '--memory-swap', '512000000',
        '--tmpfs', '/var/lib/postgresql:rw,noexec,nosuid,size=64m', '-e', 'POSTGRES_PASSWORD',
        '-e', "POSTGRES_USER=$database", '-e', "POSTGRES_DB=$database", '-e', 'PGDATA=/tmp/rehearsal-data',
        'postgres@sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395') | Out-Null
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker.exe --host $script:endpoint exec $pg pg_isready -h 127.0.0.1 -U $database *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'Rehearsal PostgreSQL startup failed' }
    $values = [ordered]@{
        RAILWAY_PROJECT_ID = 'b5c5db71-2df8-46da-84fb-d9b9df6d7951'
        RAILWAY_ENVIRONMENT_ID = '8bcf4119-3a6b-49b3-b4ff-8ea20080f3b9'
        RAILWAY_ENVIRONMENT_NAME = 'staging'
        RAILWAY_SERVICE_NAME = "$name-runner"
        FTW_REHEARSAL_ID = $runId
        FTW_REHEARSAL_MANIFEST_SHA256 = $manifest
        FASTTOWIN_ENV = 'prod'
        FTW_REHEARSAL_SCOPE = 'room-ownership-production'
        DATABASE_URL = "jdbc:postgresql://$pg.railway.internal:5432/$database"
        DATABASE_USER = $database
        DATABASE_PASSWORD = $env:POSTGRES_PASSWORD
        FASTTOWIN_ROOM_OWNERSHIP_MODE = 'production'
        FASTTOWIN_ROOM_OWNERSHIP_ROLE = 'ingress'
        FASTTOWIN_ROOM_OWNERSHIP_NODE_ID = '00000000-0000-4000-8000-00000000000a'
        FASTTOWIN_ROOM_OWNERSHIP_PEER_ID = '00000000-0000-4000-8000-00000000000b'
        FASTTOWIN_ROOM_OWNERSHIP_PEER_URL = "http://$name-b.railway.internal:8080"
        FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
        FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS = (@(1..40 | ForEach-Object { [guid]::NewGuid().ToString() }) -join ',')
        FASTTOWIN_ROOM_OWNERSHIP_REHEARSAL_PASSWORD = 'Rehearsal1!' + [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
    }
    $arguments = @('run', '--name', "$name-runner", '--label', $label, '--network', $name, '--user', 'runner',
        '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges:true',
        '--cpus', '0.5', '--memory', '500000000', '--memory-swap', '500000000')
    foreach ($key in $values.Keys) {
        Set-RunVariable $key $values[$key]
        $arguments += @('-e', $key)
    }
    $arguments += @($tag, '--seed-production')
    $receiptText = Docker $arguments
    $receipt = $receiptText | ConvertFrom-Json
    if ($receipt.productionSeeded -ne $true -or $receipt.manifestSha256 -cne $manifest -or $receipt.runId -cne $runId) {
        throw 'Production seed receipt does not match the package'
    }
    if ($receipt.admissionGeneration -lt 1 -or $receipt.topologySha256 -cnotmatch '^[a-f0-9]{64}$' -or
        $receipt.policySha256 -cnotmatch '^[a-f0-9]{64}$') { throw 'Production seed authority receipt is incomplete' }
    $receiptText | Set-Content "$output/seed-receipt.json" -Encoding utf8NoBOM
    $sql = 'SELECT (SELECT count(*) FROM users), (SELECT count(*) FROM profiles), (SELECT count(*) FROM room_ownership_pilot_fixture)'
    $rows = Docker @('exec', $pg, 'psql', '-X', '-At', '-U', $database, '-d', $database, '-c', $sql)
    if ($rows -cne '40|40|0') { throw 'Unexpected seeded account or fixture marker count' }
    $authority = Docker @('exec', $pg, 'psql', '-X', '-At', '-U', $database, '-d', $database, '-c',
        "SELECT generation, state, topology_sha256, policy_sha256 FROM owned_admission_policy WHERE singleton")
    $expectedAuthority = "$($receipt.admissionGeneration)|ACTIVE|$($receipt.topologySha256)|$($receipt.policySha256)"
    if ($authority -cne $expectedAuthority) { throw 'Committed seed authority differs from its receipt' }
    $beforeRetry = $rows
    $retry = & docker.exe --host $script:endpoint start --attach "$name-runner" 2>&1
    $retryExit = $LASTEXITCODE
    $exitCode = Docker @('inspect', '--format', '{{.State.ExitCode}}', "$name-runner")
    if ($exitCode -cne '1' -or ($retry -join [Environment]::NewLine).Trim() -cne 'Production rehearsal seed failed') {
        throw 'Replay did not refuse the occupied database'
    }
    if ((Docker @('exec', $pg, 'psql', '-X', '-At', '-U', $database, '-d', $database, '-c', $sql)) -cne $beforeRetry) {
        throw 'Seeder replay changed existing accounts'
    }
    if ((Docker @('exec', $pg, 'psql', '-X', '-At', '-U', $database, '-d', $database, '-c',
        "SELECT generation, state, topology_sha256, policy_sha256 FROM owned_admission_policy WHERE singleton")) -cne $expectedAuthority) {
        throw 'Seeder replay changed admission authority'
    }
    [ordered]@{seedAccepted=$true; replayRefused=$true; accounts=40; profiles=40; fixtureMarkerRows=0
        runnerUser=$image.Config.User; image=$image.Id; serverJarSha256=$jar; runnerManifestSha256=$manifest; runId=$runId
        admissionGeneration=$receipt.admissionGeneration; topologySha256=$receipt.topologySha256; policySha256=$receipt.policySha256
    } | ConvertTo-Json | Set-Content "$output/local-receipt.json" -Encoding utf8NoBOM
    Write-Output "Production seed verified with 40 synthetic accounts and zero marker rows. $output"
    }
} catch {
    $primaryFailure = $_
} finally {
    if ($created) {
        try {
            $ids = Docker @('ps', '-aq', '--filter', "label=$label")
            foreach ($id in @($ids -split '\r?\n' | Where-Object { $_ })) {
                if ((Docker @('inspect', '--format', '{{ index .Config.Labels "fasttowin.production-rehearsal" }}', $id)) -cne $runId) {
                    throw 'Container ownership differs'
                }
                Docker @('rm', '-fv', $id) | Out-Null
            }
            $networks = Docker @('network', 'ls', '-q', '--filter', "label=$label")
            foreach ($id in @($networks -split '\r?\n' | Where-Object { $_ })) { Docker @('network', 'rm', $id) | Out-Null }
            $images = Docker @('image', 'ls', '--all', '--quiet', '--filter', "label=$label")
            foreach ($id in @($images -split '\r?\n' | Where-Object { $_ } | Select-Object -Unique)) {
                Docker @('image', 'rm', $id) | Out-Null
            }
            foreach ($kind in 'container', 'network', 'volume', 'image') {
                $left = Docker @($kind, 'ls', '--quiet', '--filter', "label=$label")
                if ($left) { throw "Owned $kind remains" }
            }
            [ordered]@{containers=0;networks=0;volumes=0;images=0} | ConvertTo-Json |
                Set-Content "$output/cleanup.json" -Encoding utf8NoBOM
        } catch { $primaryFailure = $_ }
    }
    foreach ($key in $oldEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $oldEnvironment[$key], 'Process')
    }
    Pop-Location
}
if ($primaryFailure) { throw $primaryFailure }

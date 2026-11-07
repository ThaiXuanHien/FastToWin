#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Fault', 'Load')][string]$Suite,
    [ValidateRange(1, 3600)][int]$DurationSeconds = 600,
    [ValidateRange(1, 20)][int]$Rooms = 20,
    [ValidateSet('All', 'One', 'Two')][string]$LoadProfiles = 'All'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-OwnedProject([string]$Project) {
    if ($Project -cnotmatch '\Aroom-pilot-[a-f0-9]{12}\z') { throw 'Invalid owned acceptance project.' }
}

function Assert-LocalDockerEndpoint([string]$Endpoint) {
    if ($Endpoint -notmatch '\A(npipe:////\./pipe/[a-zA-Z0-9_.-]+|unix:///[a-zA-Z0-9_./-]+)\z') {
        throw 'Acceptance requires a local Docker named pipe or Unix socket; remote Docker targets are rejected.'
    }
}

function Convert-LoopbackBinding([string]$Binding) {
    if ($Binding -notmatch '\A127\.0\.0\.1:([0-9]{1,5})\z') { throw 'Fixture port must have exactly one IPv4 loopback binding.' }
    $port = [int]$Matches[1]
    if ($port -lt 1 -or $port -gt 65535) { throw 'Invalid fixture port.' }
    return $port
}

function Assert-OwnedArtifactPath([string]$Root, [string]$Path) {
    $rootPath = [IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $fullPath = [IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($rootPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Acceptance receipts must be a child of the owned artifact directory.'
    }
    $cursor = $fullPath
    while ($cursor) {
        $item = Get-Item -LiteralPath $cursor -Force -ErrorAction SilentlyContinue
        if ($null -ne $item) {
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Acceptance receipt path must not traverse a symlink, junction, or reparse point.'
            }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
}

function Set-FixtureEnvironment([string]$Name, [string]$Value) {
    if (-not $script:savedEnvironment.ContainsKey($Name)) {
        $script:savedEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Invoke-Docker([string[]]$Arguments) {
    $result = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Docker operation failed: $($Arguments[0]) $($Arguments[1]); no environment or raw diagnostics recorded." }
    return $result
}

function Invoke-FixtureCompose([string[]]$Arguments) {
    return Invoke-Docker (@('compose', '--project-name', $script:project, '--file', $script:composeBase, '--file', $script:composeOverride) + $Arguments)
}

function Get-FixturePort([string]$Service, [int]$ContainerPort) {
    $binding = (Invoke-FixtureCompose @('port', $Service, "$ContainerPort")) -join "`n"
    return Convert-LoopbackBinding $binding.Trim()
}

function Write-Receipt([string]$Name, $Value) {
    $path = Join-Path $script:receipts $Name
    Assert-OwnedArtifactPath $script:artifactRoot $path
    $Value | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $path -Encoding utf8NoBOM
}

function Get-OwnedResourceIds([string]$Kind) {
    Assert-OwnedProject $script:project
    $command = switch ($Kind) { 'container' { 'ps' } 'network' { 'network' } 'volume' { 'volume' } 'image' { 'image' } }
    $args = if ($Kind -eq 'container') { @('ps', '-aq') } else { @($command, 'ls', '-q') }
    # Compose assigns this exact label to its project resources, including stopped containers.
    $label = if ($Kind -eq 'image') { 'io.fasttowin.room-ownership-acceptance' } else { 'com.docker.compose.project' }
    return @(Invoke-Docker ($args + @('--filter', "label=$label=$script:project")) | Where-Object { "$_".Trim() })
}

function Remove-Fixture {
    Assert-OwnedProject $script:project
    $failure = $false
    try { Invoke-FixtureCompose @('down', '--volumes', '--rmi', 'local', '--remove-orphans', '--timeout', '30') | Out-Null } catch { $failure = $true }
    # Do not delete arbitrary image names or shared pulled images. Build labels identify only this project.
    try {
        foreach ($id in @(Get-OwnedResourceIds 'image' | Sort-Object -Unique)) {
            if ("$id" -notmatch '^(sha256:)?[a-f0-9]{12,64}$') { throw 'Unexpected owned image identity.' }
            $label = (Invoke-Docker @('image', 'inspect', '--format', '{{ index .Config.Labels "io.fasttowin.room-ownership-acceptance" }}', "$id")) -join ''
            if ($label.Trim() -cne $script:project) { throw 'Image does not carry the owned fixture label.' }
            Invoke-Docker @('image', 'rm', "$id") | Out-Null
        }
    } catch { $failure = $true }
    $leftovers = [ordered]@{}
    foreach ($kind in @('container', 'network', 'volume', 'image')) {
        try { $leftovers[$kind] = @(Get-OwnedResourceIds $kind) } catch { $failure = $true; $leftovers[$kind] = @('verification-failed') }
        if ($leftovers[$kind].Count -gt 0) { $failure = $true }
    }
    Write-Receipt 'cleanup.json' ([ordered]@{ project = $script:project; success = -not $failure; leftovers = $leftovers })
    if ($failure) { throw "Fixture cleanup failed for $script:project; inspect cleanup.json in $script:receipts." }
}

function Start-AcceptanceFixture([int]$Backends, [string]$FixtureName) {
    $script:fixtureOwned = $false
    $script:project = 'room-pilot-' + [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(6)).ToLowerInvariant()
    Assert-OwnedProject $script:project
    $script:receipts = Join-Path $script:runDirectory $FixtureName
    Assert-OwnedArtifactPath $script:artifactRoot $script:receipts
    New-Item -ItemType Directory -Path $script:receipts | Out-Null
    $accounts = @(1..40 | ForEach-Object { [guid]::NewGuid().ToString() })
    $fixtureId = [guid]::NewGuid().ToString()
    Set-FixtureEnvironment 'FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD' ([Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)))
    Set-FixtureEnvironment 'FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD' ('Pilot1!' + [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)))
    Set-FixtureEnvironment 'FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID' $fixtureId
    Set-FixtureEnvironment 'FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS' ($accounts -join ',')
    Set-FixtureEnvironment 'FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET' ([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)))
    # Do not inherit caller-selected host bindings or subnet targets.
    Set-FixtureEnvironment 'FASTTOWIN_ROOM_OWNERSHIP_PORT' ''
    Set-FixtureEnvironment 'FASTTOWIN_ROOM_OWNERSHIP_SUBNET_PREFIX' '172.31.231'
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_PROJECT' $script:project
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_COMPOSE_BASE' $script:composeBase
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_COMPOSE_OVERRIDE' $script:composeOverride
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_COMPOSE' ($script:composeBase + [IO.Path]::PathSeparator + $script:composeOverride)
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_SUITE' $Suite
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_BACKENDS' "$Backends"
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_RECEIPTS' $script:receipts
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_DURATION_SECONDS' "$DurationSeconds"
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_ROOMS' "$Rooms"
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_SELECTIONS_PER_SECOND' '5'
    Set-FixtureEnvironment 'FTW_ACCEPTANCE_SEED' '73'
    # Reserve distinct loopback ports before startup. Explicit bindings survive container stop/start;
    # Docker's empty host-port syntax allocates a different port after restart.
    $portListeners = @()
    try {
        foreach ($name in @('A', 'B', 'POSTGRES', 'PROXY_A', 'PROXY_B')) {
            $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
            $listener.Start()
            $portListeners += $listener
            Set-FixtureEnvironment "FTW_ACCEPTANCE_${name}_PORT" "$($listener.LocalEndpoint.Port)"
        }
    } finally { foreach ($listener in $portListeners) { $listener.Stop() } }
    Write-Receipt 'fixture.json' ([ordered]@{ project = $script:project; suite = $Suite; backends = $Backends; fixtureId = $fixtureId; accounts = $accounts; status = 'starting' })
    $revision = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $revision -notmatch '^[a-f0-9]{40}$') { throw 'Cannot identify the acceptance build.' }
    Write-Receipt 'build.json' ([ordered]@{
        parentRevision = $revision
        workingTreeHasChanges = [bool](& git status --porcelain)
        serverJarSha256 = (Get-FileHash -LiteralPath (Join-Path $script:repo 'server/build/install/server/lib/server.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
        protocolJarSha256 = (Get-ChildItem -LiteralPath (Join-Path $script:repo 'server/build/install/server/lib') -Filter 'protocol-jvm*.jar' | Get-FileHash -Algorithm SHA256 | Select-Object -ExpandProperty Hash)
        startedUtc = [DateTime]::UtcNow.ToString('o')
    })
    $fixtureFailure = $null
    try {
        # A collision never grants ownership of existing resources.
        foreach ($kind in @('container', 'network', 'volume', 'image')) {
            if (@(Get-OwnedResourceIds $kind).Count -gt 0) { throw 'Generated project already exists; refusing to adopt it.' }
        }
        $script:fixtureOwned = $true
        Invoke-FixtureCompose @('config', '--quiet') | Out-Null
        # Build images serially first so two JVM images do not compete for Docker memory.
        Invoke-FixtureCompose @('build') | Out-Null
        Invoke-FixtureCompose @('up', '--wait', '--wait-timeout', '180') | Out-Null
        Set-FixtureEnvironment 'FTW_ACCEPTANCE_A' "http://127.0.0.1:$(Get-FixturePort 'backend-a' 8080)"
        Set-FixtureEnvironment 'FTW_ACCEPTANCE_B' "http://127.0.0.1:$(Get-FixturePort 'backend-b' 8080)"
        Set-FixtureEnvironment 'FTW_ACCEPTANCE_POSTGRES_PORT' "$(Get-FixturePort 'postgres' 5432)"
        Set-FixtureEnvironment 'FTW_ACCEPTANCE_PROXY_A' "http://127.0.0.1:$(Get-FixturePort 'postgres-a' 8474)"
        Set-FixtureEnvironment 'FTW_ACCEPTANCE_PROXY_B' "http://127.0.0.1:$(Get-FixturePort 'postgres-b' 8474)"
        if ($Backends -eq 1) { Invoke-FixtureCompose @('stop', 'backend-b') | Out-Null }
        $active = 0
        foreach ($service in @('backend-a', 'backend-b')) {
            $id = (Invoke-FixtureCompose @('ps', '-a', '-q', $service)) -join ''
            if ($id -notmatch '^[a-f0-9]{64}$') { throw 'Missing backend container identity.' }
            $state = (Invoke-Docker @('inspect', '--format', '{{.State.Running}}', $id)) -join ''
            if ($state.Trim() -eq 'true') { $active++ }
        }
        if ($active -ne $Backends) { throw 'Active backend count does not match acceptance fixture.' }
        Set-FixtureEnvironment 'FTW_ACCEPTANCE_ACTIVE_BACKENDS' "$active"
        Write-Receipt 'fixture.json' ([ordered]@{ project = $script:project; suite = $Suite; backends = $Backends; activeBackends = $active; fixtureId = $fixtureId; accounts = $accounts; status = 'ready' })
        $identities = @()
        foreach ($id in @((Invoke-FixtureCompose @('images', '-q')) | Sort-Object -Unique)) {
            if ("$id" -notmatch '^(sha256:)?[a-f0-9]{12,64}$') { throw 'Unexpected fixture image identity.' }
            $safe = (Invoke-Docker @('image', 'inspect', '--format', '{{json .Id}}|{{json .RepoTags}}|{{json .RepoDigests}}', "$id")) -join ''
            $parts = $safe.Split('|')
            $identities += [ordered]@{ id = ($parts[0] | ConvertFrom-Json); tags = @($parts[1] | ConvertFrom-Json); digests = @($parts[2] | ConvertFrom-Json) }
        }
        Write-Receipt 'images.json' $identities
        Write-Host "Running $Suite with $active backend(s); receipts: $script:receipts"
        # Windows process command lines cannot hold this dependency classpath. Java reads an argfile instead.
        $javaArguments = Join-Path $script:receipts 'java.args'
        Assert-OwnedArtifactPath $script:artifactRoot $javaArguments
        if ($script:acceptanceClasspath.Contains('"') -or $script:acceptanceClasspath.Contains("`n")) { throw 'Invalid launcher classpath.' }
        @('-cp', ('"' + $script:acceptanceClasspath.Replace('\', '/') + '"'), 'com.hienthai.fastowin.server.ownership.RoomOwnershipAcceptanceMainKt') |
            Set-Content -LiteralPath $javaArguments -Encoding utf8NoBOM
        & $script:java "@$javaArguments"
        if ($LASTEXITCODE -ne 0) { throw "$Suite acceptance failed for $active backend(s)." }
    } catch {
        $fixtureFailure = $_
    } finally {
        # Only a project we proved absent before startup can be cleaned up by this run.
        if ($script:fixtureOwned) {
            try { Remove-Fixture } catch {
                if ($fixtureFailure) { Write-Error $fixtureFailure -ErrorAction Continue }
                throw
            }
        }
    }
    if ($fixtureFailure) { throw $fixtureFailure }
}

$script:repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:artifactRoot = Join-Path $script:repo '.artifacts/room-ownership-local'
$script:runDirectory = Join-Path $script:artifactRoot ([guid]::NewGuid().ToString('N'))
$script:composeBase = Join-Path $script:repo 'deploy/room-ownership/compose.local.yaml'
$script:composeOverride = Join-Path $script:repo 'deploy/room-ownership/compose.acceptance.yaml'
$script:savedEnvironment = @{}
$script:fixtureOwned = $false
Assert-OwnedArtifactPath $script:artifactRoot $script:runDirectory

Push-Location $script:repo
try {
    Get-Command docker -ErrorAction Stop | Out-Null
    if ($env:DOCKER_HOST) { Assert-LocalDockerEndpoint $env:DOCKER_HOST }
    $context = ((Invoke-Docker @('context', 'inspect')) -join "`n") | ConvertFrom-Json
    Assert-LocalDockerEndpoint $context[0].Endpoints.docker.Host
    if (-not $env:JAVA_HOME) { Set-FixtureEnvironment 'JAVA_HOME' 'C:/Program Files/Android/Android Studio/jbr' }
    $script:java = Join-Path $env:JAVA_HOME 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $script:java)) { throw 'Set JAVA_HOME to an installed JDK 17 or newer.' }
    & (Join-Path $script:repo 'gradlew.bat') ':server:installDist' ':server:roomOwnershipAcceptanceClasspath' '--no-daemon' '--no-configuration-cache' '--max-workers=1'
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance distribution/classpath build failed.' }
    $script:acceptanceClasspath = (Get-Content -LiteralPath (Join-Path $script:repo 'server/build/room-ownership-acceptance-classpath.txt') -Raw).Trim()
    if (-not $script:acceptanceClasspath) { throw 'Acceptance JVM classpath is empty.' }
    if ($Suite -eq 'Fault') { Start-AcceptanceFixture 2 'fault' }
    elseif ($LoadProfiles -eq 'One') { Start-AcceptanceFixture 1 'load-1' }
    elseif ($LoadProfiles -eq 'Two') { Start-AcceptanceFixture 2 'load-2' }
    else { Start-AcceptanceFixture 1 'load-1'; Start-AcceptanceFixture 2 'load-2' }
    Write-Host "Acceptance completed; receipts: $script:runDirectory"
} finally {
    foreach ($entry in $script:savedEnvironment.GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process') }
    Pop-Location
}

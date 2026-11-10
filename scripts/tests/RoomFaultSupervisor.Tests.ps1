#Requires -Version 7.0
# Local-only protocol checks: the cached pinned compiler cannot access any network.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$module = Join-Path $root 'tools/room-fault-supervisor'
$cache = Join-Path $root '.artifacts/room-fault-task-1'
$image = 'golang@sha256:e482b31b5d30c31f9e495f4b8cbdf41429b2d74955dfbcd9c0a904483d738390'
New-Item -ItemType Directory -Path (Join-Path $cache 'go-build'), (Join-Path $cache 'go-mod') -Force | Out-Null
# --pull never makes a missing cached compiler fail rather than downloading anything.
$dockerArgs = @(
    'run', '--rm', '--pull', 'never', '--network', 'none', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges:true',
    '--label', 'fasttowin.fixture=room-fault-task-1', '--label', 'fasttowin.owner=protocol-tests',
    '--mount', "type=bind,source=$module,target=/src,readonly", '--workdir', '/src',
    '--mount', "type=bind,source=$(Join-Path $cache 'go-build'),target=/go-build",
    '--mount', "type=bind,source=$(Join-Path $cache 'go-mod'),target=/go-mod",
    '-e', 'GOTOOLCHAIN=local', '-e', 'GOCACHE=/go-build', '-e', 'GOMODCACHE=/go-mod', '-e', 'CGO_ENABLED=1'
)
foreach ($check in @(
    @{ Name = 'protocol tests'; Arguments = @('go', 'test', '-timeout=90s', '-count=1', './...') },
    @{ Name = 'race checks'; Arguments = @('go', 'test', '-race', '-timeout=90s', '-count=1', './...') },
    @{ Name = 'vet'; Arguments = @('go', 'vet', './...') }
)) {
    Write-Output "Running $($check.Name) in the pinned offline compiler."
    $containerName = 'ftw-room-fault-protocol-' + [guid]::NewGuid().ToString('N')
    try {
        # Coreutils timeout also bounds compiler/vet work before Go's test timer starts.
        & docker @dockerArgs --name $containerName $image timeout 180s @($check.Arguments)
        if ($LASTEXITCODE -ne 0) { throw "Room fault supervisor $($check.Name) failed (exit $LASTEXITCODE)." }
    }
    finally {
        $remaining = @(& docker ps --all --quiet --filter "name=^/$containerName`$")
        if ($LASTEXITCODE -ne 0) { throw 'Could not verify task compiler container cleanup.' }
        if ($remaining.Count -gt 0) {
            & docker rm --force $containerName | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Could not remove task compiler container.' }
        }
    }
}
Write-Output 'PASS: room fault supervisor protocol, race checks and vet.'

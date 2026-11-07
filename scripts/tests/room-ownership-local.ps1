# Guard tests run without a Docker daemon, Gradle, or fixture side effects.
$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot '../room-ownership-local.ps1'
if (-not (Test-Path -LiteralPath $scriptPath)) { throw 'Missing room ownership runner.' }
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw ($errors.Message -join "`n") }
$guardNames = @('Assert-OwnedProject', 'Assert-OwnedArtifactPath', 'Assert-LocalDockerEndpoint', 'Convert-LoopbackBinding')
foreach ($name in $guardNames) {
    $definition = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] }, $true) |
        Where-Object Name -EQ $name
    if (@($definition).Count -ne 1) { throw "Missing guard $name" }
    Invoke-Expression $definition.Extent.Text
}
function Expect-Rejected([scriptblock]$Action) {
    try { & $Action } catch { return }
    throw 'Guard accepted an unsafe input.'
}
Assert-OwnedProject 'room-pilot-012345abcdef'
Expect-Rejected { Assert-OwnedProject 'existing-production' }
Expect-Rejected { Assert-OwnedProject 'room-pilot-012345abcdef --all' }
Expect-Rejected { Assert-OwnedProject "room-pilot-012345abcdef`n" }
Assert-LocalDockerEndpoint 'npipe:////./pipe/dockerDesktopLinuxEngine'
Assert-LocalDockerEndpoint 'unix:///var/run/docker.sock'
Expect-Rejected { Assert-LocalDockerEndpoint 'ssh://remote-host' }
Expect-Rejected { Assert-LocalDockerEndpoint 'tcp://203.0.113.2:2376' }
Expect-Rejected { Assert-LocalDockerEndpoint "unix:///var/run/docker.sock`n" }
if ((Convert-LoopbackBinding '127.0.0.1:49152') -ne 49152) { throw 'Wrong mapped port.' }
Expect-Rejected { Convert-LoopbackBinding '0.0.0.0:49152' }
Expect-Rejected { Convert-LoopbackBinding '[::]:49152' }
Expect-Rejected { Convert-LoopbackBinding '127.0.0.1:0' }
Expect-Rejected { Convert-LoopbackBinding "127.0.0.1:49152`n127.0.0.1:49153" }
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('room-ownership-guard-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $testRoot | Out-Null
    $child = Join-Path $testRoot 'owned/run'
    Assert-OwnedArtifactPath $testRoot $child
    Expect-Rejected { Assert-OwnedArtifactPath $testRoot (Join-Path $testRoot '../outside') }
    Expect-Rejected { Assert-OwnedArtifactPath $testRoot $testRoot }
    # An existing redirect in the output path must never redirect receipt writes.
    $link = Join-Path $testRoot 'redirect'
    try {
        New-Item -ItemType SymbolicLink -Path $link -Target $testRoot -ErrorAction Stop | Out-Null
        Expect-Rejected { Assert-OwnedArtifactPath $testRoot (Join-Path $link 'run') }
    } catch [System.UnauthorizedAccessException] {
        Write-Output 'Symlink test skipped: this Windows account cannot create symlinks.'
    }
} finally {
    # Fixed, validated temporary target only; do not recurse through the link.
    if (Test-Path -LiteralPath (Join-Path $testRoot 'redirect')) { Remove-Item -LiteralPath (Join-Path $testRoot 'redirect') -Force }
    $resolvedTestRoot = [IO.Path]::GetFullPath($testRoot)
    $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedTestRoot.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolvedTestRoot) -notmatch '^room-ownership-guard-[a-f0-9]{32}$') { throw 'Unsafe test cleanup target.' }
    Remove-Item -LiteralPath $resolvedTestRoot -Force -Recurse
}
Write-Output 'Room ownership runner guard tests passed.'

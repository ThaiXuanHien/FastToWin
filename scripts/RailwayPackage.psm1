Set-StrictMode -Version Latest

function Get-RailwayBuildArguments {
    [CmdletBinding()]
    param(
        [ValidateRange(1024, 16384)][int]$CompilerHeapMb = 4096,
        [switch]$ReuseVerifiedWebBuild
    )

    $arguments = @(
        ':server:test'
        ':server:installDist'
    )
    if (-not $ReuseVerifiedWebBuild) {
        $arguments += ':webApp:composeCompatibilityBrowserDistribution'
    }
    $arguments += @(
        "-Pkotlin.daemon.jvmargs=-Xmx${CompilerHeapMb}m"
        '--max-workers=1'
        '--no-parallel'
        '--no-daemon'
        '--no-configuration-cache'
    )
    $arguments
}

function New-RailwayPackage {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$ProjectDirectory,
        [Parameter(Mandatory = $true)][string]$ServerDistribution,
        [Parameter(Mandatory = $true)][string]$WebDistribution,
        [Parameter(Mandatory = $true)][string]$OutputDirectory
    )
    $ErrorActionPreference = 'Stop'
    $project = [IO.Path]::GetFullPath($ProjectDirectory)
    $server = [IO.Path]::GetFullPath($ServerDistribution)
    $web = [IO.Path]::GetFullPath($WebDistribution)
    $output = [IO.Path]::GetFullPath($OutputDirectory)
    $artifactRoot = [IO.Path]::GetFullPath((Join-Path $project '.artifacts')) + [IO.Path]::DirectorySeparatorChar
    if (-not $output.StartsWith($artifactRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'OutputDirectory must be a new directory inside project .artifacts.'
    }
    if (Test-Path -LiteralPath $output) { throw 'OutputDirectory already exists; choose a new release directory.' }
    if (-not (Test-Path -LiteralPath (Join-Path $server 'bin/server') -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $server 'lib') -PathType Container) -or
        @(Get-ChildItem -LiteralPath (Join-Path $server 'lib') -Filter '*.jar' -File -ErrorAction SilentlyContinue).Count -eq 0) {
        throw 'Server distribution is missing; run :server:installDist first.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $web 'index.html') -PathType Leaf) -or
        @(Get-ChildItem -LiteralPath $web -Filter '*.wasm' -File -Recurse -ErrorAction SilentlyContinue).Count -eq 0 -or
        @(Get-ChildItem -LiteralPath $web -Filter '*.mjs' -File -Recurse -ErrorAction SilentlyContinue).Count -eq 0) {
        throw 'Web distribution is missing; build the Wasm + JS compatibility distribution first.'
    }
    foreach ($inputPath in @((Join-Path $server 'bin'), (Join-Path $server 'lib'), $web)) {
        foreach ($entry in Get-ChildItem -LiteralPath $inputPath -Recurse -Force) {
            if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Build contains a symbolic link; refusing to package files outside the distribution.'
            }
            if ($entry.Name -match '(?i)(^\.env($|\.)|service[-_]?account|firebase[-_]?adminsdk|\.(pem|key|p12|pfx)$)') {
                throw 'Build contains a potential secret file; remove it from the distribution before packaging.'
            }
        }
    }
    $templateRoot = Join-Path $project 'deploy/railway'
    $requiredTemplates = @('server/Dockerfile', 'server/railway.json', 'web/Dockerfile', 'web/Caddyfile', 'web/railway.json')
    foreach ($relativePath in $requiredTemplates) {
        if (-not (Test-Path -LiteralPath (Join-Path $templateRoot $relativePath) -PathType Leaf)) {
            throw "Railway template missing: $relativePath"
        }
    }
    $productionConfig = Join-Path $project 'deploy/config.production.js'
    if (-not (Test-Path -LiteralPath $productionConfig -PathType Leaf)) { throw 'Production Web config is missing.' }

    $serverOutput = Join-Path $output 'server'
    $webOutput = Join-Path $output 'web'
    $distributionOutput = Join-Path $serverOutput 'distribution'
    $null = New-Item -ItemType Directory -Path $distributionOutput -Force
    $null = New-Item -ItemType Directory -Path $webOutput -Force
    Copy-Item -LiteralPath (Join-Path $server 'bin') -Destination $distributionOutput -Recurse
    Copy-Item -LiteralPath (Join-Path $server 'lib') -Destination $distributionOutput -Recurse
    Copy-Item -LiteralPath $web -Destination (Join-Path $webOutput 'site') -Recurse
    Copy-Item -LiteralPath $productionConfig -Destination (Join-Path $webOutput 'site/config.js') -Force
    foreach ($relativePath in $requiredTemplates) {
        Copy-Item -LiteralPath (Join-Path $templateRoot $relativePath) -Destination (Join-Path $output $relativePath)
    }
    # Generated artifacts only: normalize the Unix launcher without editing the input build.
    $launcher = Join-Path $distributionOutput 'bin/server'
    $launcherContent = [IO.File]::ReadAllText($launcher).Replace("`r`n", "`n")
    [IO.File]::WriteAllText($launcher, $launcherContent, (New-Object Text.UTF8Encoding($false)))
}

Export-ModuleMember -Function New-RailwayPackage, Get-RailwayBuildArguments

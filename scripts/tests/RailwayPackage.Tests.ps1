$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$modulePath = Join-Path $projectDir 'scripts/RailwayPackage.psm1'
if (-not (Test-Path -LiteralPath $modulePath)) { throw 'Railway packaging is not implemented yet.' }
Import-Module $modulePath -Force

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Assert-Rejected([scriptblock]$Action, [string]$MessageFragment) {
    $caught = $null
    try { & $Action } catch { $caught = $_.Exception.Message }
    Assert-True ($null -ne $caught -and $caught.Contains($MessageFragment)) "Expected rejection containing '$MessageFragment', got '$caught'."
}
function Write-Fixture([string]$Path, [string]$Content) {
    $null = New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($Path)) -Force
    [IO.File]::WriteAllText($Path, $Content)
}
$buildArguments = @(Get-RailwayBuildArguments)
Assert-True ($buildArguments -contains '-Pkotlin.daemon.jvmargs=-Xmx4096m') 'Railway production compiler must receive an explicit 4GB heap instead of inheriting the 2GB Gradle heap.'
Assert-True ($buildArguments -contains ':server:test' -and $buildArguments -contains ':webApp:composeCompatibilityBrowserDistribution') 'Memory configuration must not bypass server tests or the compatibility production build.'
Assert-True ($buildArguments -contains '--max-workers=1') 'Production build must bound concurrent workers.'
$customArguments = @(Get-RailwayBuildArguments -CompilerHeapMb 6144)
Assert-True ($customArguments -contains '-Pkotlin.daemon.jvmargs=-Xmx6144m') 'Custom compiler heap must reach Gradle as one complete argument.'
Assert-Rejected { Get-RailwayBuildArguments -CompilerHeapMb 0 } 'CompilerHeapMb'
Write-Host 'PASS: production build arguments set a configurable compiler heap and retain validation tasks.'
$serverOnlyArguments = @(Get-RailwayBuildArguments -ReuseVerifiedWebBuild)
Assert-True ($serverOnlyArguments -contains ':server:test' -and $serverOnlyArguments -contains ':server:installDist') 'Reusing Web must still rebuild and test the backend.'
Assert-True (-not ($serverOnlyArguments -contains ':webApp:composeCompatibilityBrowserDistribution')) 'Reusing Web must skip only the expensive Web build.'
Assert-True ($serverOnlyArguments -contains '--no-configuration-cache') 'The reduced build must retain deterministic Gradle safeguards.'
Write-Host 'PASS: verified Web artifacts can be reused while the backend is rebuilt and tested.'
$fixtureRoot = Join-Path $projectDir ('.artifacts/railway-tests/' + [guid]::NewGuid().ToString('N'))
$server = Join-Path $fixtureRoot 'input/server'
$web = Join-Path $fixtureRoot 'input/web'
Write-Fixture (Join-Path $server 'bin/server') '#!/bin/sh'
Write-Fixture (Join-Path $server 'lib/server.jar') 'server bytes'
Write-Fixture (Join-Path $server '.env') 'PRIVATE_DATABASE_PASSWORD=must-not-upload'
Write-Fixture (Join-Path $web 'index.html') '<html>production</html>'
Write-Fixture (Join-Path $web 'app.wasm') 'wasm bytes'
Write-Fixture (Join-Path $web 'fallback/app.mjs') 'export const fallback = true;'
Write-Fixture (Join-Path $web 'config.js') 'ws://localhost:8080/game'

$output = Join-Path $fixtureRoot 'release'
New-RailwayPackage -ProjectDirectory $projectDir -ServerDistribution $server -WebDistribution $web -OutputDirectory $output
Assert-True ((Get-Content -LiteralPath (Join-Path $output 'server/distribution/lib/server.jar') -Raw) -eq 'server bytes') 'Server artifact bytes must be preserved.'
Assert-True (-not (Test-Path -LiteralPath (Join-Path $output 'server/distribution/.env'))) 'Server environment secrets must not be uploaded.'
Assert-True (Test-Path -LiteralPath (Join-Path $output 'web/site/fallback/app.mjs')) 'JS fallback must ship with Wasm.'
Assert-True ((Get-Content -LiteralPath (Join-Path $output 'web/site/config.js') -Raw).Contains('wss://')) 'Development Web config must be replaced by same-origin production config.'
Assert-True ((Get-Content -LiteralPath (Join-Path $web 'config.js') -Raw) -eq 'ws://localhost:8080/game') 'Packaging must not mutate the input build.'
foreach ($service in @('server', 'web')) {
    $config = Get-Content -LiteralPath (Join-Path $output "$service/railway.json") -Raw | ConvertFrom-Json
    Assert-True ($config.build.builder -eq 'DOCKERFILE') 'Railway must build only the uploaded runtime Docker context.'
    Assert-True ($config.deploy.healthcheckPath -eq '/health') 'Deploy must wait for the health endpoint.'
}
Write-Host 'PASS: package contains runtime artifacts and production Web config, without input mutation or server secrets.'

Assert-Rejected { New-RailwayPackage -ProjectDirectory $projectDir -ServerDistribution $server -WebDistribution $web -OutputDirectory $output } 'already exists'
Assert-True ((Get-Content -LiteralPath (Join-Path $output 'server/distribution/lib/server.jar') -Raw) -eq 'server bytes') 'A rejected overwrite must preserve the release.'
Write-Host 'PASS: existing release is never overwritten.'

$missingOutput = Join-Path $fixtureRoot 'missing-release'
Assert-Rejected { New-RailwayPackage -ProjectDirectory $projectDir -ServerDistribution (Join-Path $fixtureRoot 'missing') -WebDistribution $web -OutputDirectory $missingOutput } 'Server distribution'
Assert-True (-not (Test-Path -LiteralPath $missingOutput)) 'Failed preflight must not leave a partial package.'
Write-Host 'PASS: missing build fails before writing output.'

Write-Fixture (Join-Path $web 'firebase-service-account.json') '{"private_key":"do-not-upload"}'
$secretOutput = Join-Path $fixtureRoot 'secret-release'
Assert-Rejected { New-RailwayPackage -ProjectDirectory $projectDir -ServerDistribution $server -WebDistribution $web -OutputDirectory $secretOutput } 'secret'
Assert-True (-not (Test-Path -LiteralPath $secretOutput)) 'Secret rejection must not leave an uploadable package.'
Write-Host 'PASS: suspicious secret files in Web build block packaging.'
Write-Host 'All 6 Railway package/build configuration tests passed.'

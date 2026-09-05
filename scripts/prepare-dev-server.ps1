[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$archivePath = [IO.Path]::GetFullPath((Join-Path $projectDir 'server\build\distributions\server.zip'))
$runtimeRoot = [IO.Path]::GetFullPath((Join-Path $projectDir '.artifacts\dev-server'))
$expectedRuntimeRoot = [IO.Path]::GetFullPath((Join-Path $projectDir '.artifacts\dev-server'))
$serverHome = Join-Path $runtimeRoot 'server'

if ($runtimeRoot -ne $expectedRuntimeRoot) {
    throw 'Thu muc runtime server khong hop le.'
}

Set-Location $projectDir

Write-Host '[FastToWin] Dang dong goi server va protocol...'
& (Join-Path $projectDir 'gradlew.bat') :server:distZip --rerun-tasks
if ($LASTEXITCODE -ne 0) {
    throw "Gradle dong goi server that bai ($LASTEXITCODE)."
}

if (-not (Test-Path -LiteralPath $archivePath)) {
    throw "Khong tim thay goi server tai $archivePath"
}

if (Test-Path -LiteralPath $runtimeRoot) {
    Remove-Item -LiteralPath $runtimeRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
Expand-Archive -LiteralPath $archivePath -DestinationPath $runtimeRoot -Force

if (-not (Test-Path -LiteralPath (Join-Path $serverHome 'lib'))) {
    throw "Goi server khong hop le tai $serverHome"
}

Write-Host "[FastToWin] Da chuan bi server tai $serverHome"

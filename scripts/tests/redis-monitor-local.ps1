param([ValidateSet('Native','Docker')][string]$Mode='Docker')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runId=[guid]::NewGuid().ToString('N')
$artifact=[IO.Path]::GetFullPath((Join-Path $repo ".artifacts/redis-monitor-local-20261103/run-$runId"))
$ownedRoot=[IO.Path]::GetFullPath((Join-Path $repo '.artifacts/redis-monitor-local-20261103'))+[IO.Path]::DirectorySeparatorChar
if(-not $artifact.StartsWith($ownedRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid owned fixture path'}
New-Item -ItemType Directory -Path $artifact | Out-Null
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $artifact '/inheritance:r' '/grant:r' "*${sid}:(OI)(CI)F" | Out-Null
if($LASTEXITCODE -ne 0){throw 'Owner-only artifact ACL failed'}
$tool=Get-Content (Join-Path $ownedRoot 'toolchain.json')|ConvertFrom-Json
$env:GOCACHE=$tool.goCache; $env:GOPATH=$tool.goPath; $env:GOTOOLCHAIN='local'; $env:GOTELEMETRY='off'; $env:CGO_ENABLED='0'
Push-Location (Join-Path $repo 'tools/redis-monitor')
try{
  $env:GOOS='windows';$env:GOARCH='amd64'
  & $tool.executable build -trimpath -o (Join-Path $ownedRoot 'redis-monitor.exe') ./cmd/redis-monitor
  if($LASTEXITCODE -ne 0){throw 'Native collector build failed'}
  $env:GOOS='linux'
  & $tool.executable build -trimpath -o (Join-Path $ownedRoot 'redis-monitor-linux-amd64') ./cmd/redis-monitor
  if($LASTEXITCODE -ne 0){throw 'Static Linux collector build failed'}
  & $tool.executable build -trimpath -o (Join-Path $ownedRoot 'redis-monitor-runtime-probe') (Join-Path $PSScriptRoot 'redis-monitor-runtime-probe.go')
  if($LASTEXITCODE -ne 0){throw 'Static acceptance-only probe build failed'}
}finally{Pop-Location}
if($Mode -eq 'Native'){
  Write-Output "Owned fixture: $artifact"
  & node (Join-Path $PSScriptRoot 'redis-monitor-fixture.mjs') native $artifact $repo
  if($LASTEXITCODE -ne 0){throw 'Native fixture incomplete; inspect sanitized artifact evidence'}
}else{
  & node (Join-Path $PSScriptRoot 'redis-monitor-docker.mjs') $artifact $repo $runId
  if($LASTEXITCODE -ne 0){throw 'Real Redis acceptance incomplete; inspect sanitized artifact evidence'}
}

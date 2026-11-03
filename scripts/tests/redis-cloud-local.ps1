param([ValidateSet('Shipping','UI','WALDiagnostic','Storage','FullOnly','FullRecovery')][string]$Mode='Shipping')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runId=[guid]::NewGuid().ToString('N')
$artifact=[IO.Path]::GetFullPath((Join-Path $repo ".artifacts/redis-cloud-local-20261103/run-$runId"))
$ownedRoot=[IO.Path]::GetFullPath((Join-Path $repo '.artifacts/redis-cloud-local-20261103'))+[IO.Path]::DirectorySeparatorChar
if(-not $artifact.StartsWith($ownedRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid owned fixture path'}
New-Item -ItemType Directory -Path $artifact | Out-Null
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $artifact '/inheritance:r' '/grant:r' "*${sid}:(OI)(CI)F" | Out-Null
if($LASTEXITCODE -ne 0){throw 'Owner-only artifact ACL failed'}
Write-Output "Owned local Cloud fixture: $artifact"
& node (Join-Path $PSScriptRoot 'redis-cloud-fixture.mjs') $Mode.ToLowerInvariant() $repo $runId
if($LASTEXITCODE -ne 0){throw 'Local Cloud fixture incomplete; inspect gate receipts'}

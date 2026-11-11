#Requires -Version 7.0
[CmdletBinding()]
param([string]$Resume)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$artifactRoot = Join-Path $repo '.artifacts/room-supervised-local'
$run = if ($Resume) { [IO.Path]::GetFullPath($Resume) } else { Join-Path $artifactRoot ('room-supervised-' + [guid]::NewGuid().ToString('N').Substring(0,12)) }
if (-not $run.StartsWith($artifactRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    [IO.Path]::GetFileName($run) -cnotmatch '^room-supervised-[a-f0-9]{12}$') { throw 'Invalid owned package path' }
$cursor = $run
while ($cursor.Length -ge $repo.Length) {
    if ((Test-Path -LiteralPath $cursor) -and ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Package path traverses a reparse point' }
    $cursor = Split-Path $cursor -Parent
}
Push-Location $repo
$oldJava = $env:JAVA_HOME
$oldTmp = $env:TMP
$oldTemp = $env:TEMP
try {
    if (-not $Resume) {
        New-Item -ItemType Directory -Path "$run/runner/lib", "$run/runner/classes" | Out-Null
        $env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
        $env:TEMP = 'D:/HienTX/Work/Android/FastToWin/.artifacts/java-tmp'
        $env:TMP = $env:TEMP
        & ./gradlew.bat :server:roomOwnershipAcceptanceClasspath --offline --no-daemon --no-configuration-cache --max-workers=1 --console=plain *> "$run/compile.log"
        if ($LASTEXITCODE -ne 0) { throw "Acceptance classpath compilation failed. See $run/compile.log" }
        $frozen = Join-Path $repo '.artifacts/room-ownership-db-latency-20261110/backend-d4b639e'
        $jar = '8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818'
        if ((Get-FileHash -LiteralPath 'server/build/libs/server.jar').Hash.ToLowerInvariant() -cne $jar) { throw 'Working application JAR differs from accepted JAR' }
        Copy-Item -Path "$frozen/distribution/lib/*" -Destination "$run/runner/lib"
        Copy-Item -Path 'server/build/classes/kotlin/test/*' -Destination "$run/runner/classes" -Recurse
        $classpath = (Get-Content -Raw 'server/build/room-ownership-acceptance-classpath.txt').Trim().Split([IO.Path]::PathSeparator)
        foreach ($entry in $classpath) {
            if ([IO.Path]::GetExtension($entry) -ne '.jar') { continue }
            $target = Join-Path "$run/runner/lib" ([IO.Path]::GetFileName($entry))
            if (Test-Path -LiteralPath $target) {
                if ((Get-FileHash -LiteralPath $entry).Hash -cne (Get-FileHash -LiteralPath $target).Hash) { throw 'Frozen runtime library collision' }
            } else { Copy-Item -LiteralPath $entry -Destination $target }
        }
        Copy-Item -LiteralPath 'deploy/room-ownership/staging-runner.Dockerfile' -Destination "$run/runner/Dockerfile"
    }
    Write-Output "Local supervised package $run"
    & node scripts/room-ownership-supervised-local.mjs $run
    if ($LASTEXITCODE -ne 0) { throw "Local supervised verification failed. Evidence retained in $run" }
} finally {
    $env:JAVA_HOME = $oldJava
    $env:TMP = $oldTmp
    $env:TEMP = $oldTemp
    Pop-Location
}

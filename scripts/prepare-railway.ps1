param(
    [switch]$PackageOnly,
    [switch]$ReuseVerifiedWebBuild,
    [ValidateRange(1024, 16384)][int]$CompilerHeapMb = 4096
)

$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Import-Module (Join-Path $PSScriptRoot 'RailwayPackage.psm1') -Force

if ($PackageOnly -and $ReuseVerifiedWebBuild) {
    throw 'PackageOnly va ReuseVerifiedWebBuild khong the dung cung luc.'
}

if (-not $PackageOnly) {
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
    if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        throw 'Khong tim thay JDK. Hay dat JAVA_HOME truoc khi build.'
    }
    if ($env:JAVA_TOOL_OPTIONS -match 'sun\.nio\.ch\.PollSelectorProvider') {
        throw 'JAVA_TOOL_OPTIONS dang dung PollSelectorProvider khong ho tro Windows. Hay xoa cau hinh nay truoc khi build.'
    }
    Push-Location $projectDir
    try {
        $buildArguments = @(Get-RailwayBuildArguments -CompilerHeapMb $CompilerHeapMb -ReuseVerifiedWebBuild:$ReuseVerifiedWebBuild)
        Write-Host "[FastToWin] Build production: Kotlin compiler heap ${CompilerHeapMb}MB, 1 worker."
        if ($ReuseVerifiedWebBuild) {
            Write-Warning 'Dang tai su dung Web artifact da duoc xac minh; backend van duoc build va test lai.'
        }
        & (Join-Path $projectDir 'gradlew.bat') @buildArguments
        if ($LASTEXITCODE -ne 0) { throw "Build production that bai ($LASTEXITCODE); chua tao goi upload." }
    } finally { Pop-Location }
} else {
    Write-Warning 'PackageOnly tai su dung build hien co; chi dung khi ban da build dung revision can deploy.'
}
$release = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$output = Join-Path $projectDir ".artifacts/railway/$release"
New-RailwayPackage -ProjectDirectory $projectDir `
    -ServerDistribution (Join-Path $projectDir 'server/build/install/server') `
    -WebDistribution (Join-Path $projectDir 'webApp/build/dist/composeWebCompatibility/productionExecutable') `
    -OutputDirectory $output
Write-Host "[FastToWin] Da dong goi Railway: $output"
Write-Host '[FastToWin] Chua upload, chua tao dich vu va chua mo public domain.'

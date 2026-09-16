param(
    [Parameter(Mandatory = $true)][string]$CaddyExecutable,
    [int]$Port = 18089
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$caddy = (Resolve-Path -LiteralPath $CaddyExecutable).Path
$fixtureDir = Join-Path $projectDir ('.artifacts/caddy-routing-tests/' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $fixtureDir -Force
[IO.File]::WriteAllText((Join-Path $fixtureDir 'index.html'), 'alpha-static-fixture')
$savedVariables = @{}
foreach ($name in @('PORT', 'BACKEND_UPSTREAM', 'FASTTOWIN_DOMAIN', 'ACME_EMAIL')) {
    $savedVariables[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    $env:PORT = "$Port"
    $env:BACKEND_UPSTREAM = '127.0.0.1:1'
    $env:FASTTOWIN_DOMAIN = "http://127.0.0.1:$Port"
    $env:ACME_EMAIL = 'routing-test@example.invalid'
    foreach ($relativeConfig in @('deploy/railway/web/Caddyfile', 'deploy/Caddyfile')) {
        $adaptedText = (& $caddy adapt --config (Join-Path $projectDir $relativeConfig) --adapter caddyfile) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Caddy adaptation failed for $relativeConfig" }
        # Test the actual adapted handlers, only substituting the fixture root
        # and restricting the listener to loopback instead of exposing a server.
        $rootJson = ConvertTo-Json -InputObject $fixtureDir -Compress
        $adapted = ($adaptedText.Replace('"root":"/srv"', '"root":' + $rootJson)) | ConvertFrom-Json
        foreach ($server in $adapted.apps.http.servers.PSObject.Properties.Value) {
            $server.listen = @("127.0.0.1:$Port")
        }
        $configPath = Join-Path $fixtureDir 'runtime.json'
        [IO.File]::WriteAllText($configPath, ($adapted | ConvertTo-Json -Depth 100), (New-Object Text.UTF8Encoding($false)))
        $process = Start-Process -FilePath $caddy -ArgumentList @('run', '--config', ('"' + $configPath + '"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $fixtureDir 'stdout.log') -RedirectStandardError (Join-Path $fixtureDir 'stderr.log')
        try {
            $ready = $false
            for ($attempt = 0; $attempt -lt 30; $attempt++) {
                if ($process.HasExited) { throw "Caddy exited for $relativeConfig. See $fixtureDir/stderr.log" }
                try {
                    $response = Invoke-WebRequest "http://127.0.0.1:$Port/" -UseBasicParsing -TimeoutSec 1
                    $ready = $response.StatusCode -eq 200
                    if ($ready) { break }
                } catch { Start-Sleep -Milliseconds 100 }
            }
            if (-not $ready) { throw "Caddy did not become ready for $relativeConfig" }
            foreach ($path in @('/', '/rooms')) {
                $response = Invoke-WebRequest "http://127.0.0.1:$Port$path" -UseBasicParsing -TimeoutSec 3
                if ($response.StatusCode -ne 200 -or $response.Content -ne 'alpha-static-fixture') { throw "Static fallback failed for $relativeConfig $path" }
            }
            foreach ($path in @('/internal', '/internal/health', '/internal/metrics')) {
                $status = 0
                try { $status = [int](Invoke-WebRequest "http://127.0.0.1:$Port$path" -UseBasicParsing -TimeoutSec 3).StatusCode }
                catch {
                    if (-not $_.Exception.Response) { throw }
                    $status = [int]$_.Exception.Response.StatusCode
                }
                if ($status -ne 404) { throw "$relativeConfig $path must return 404 before SPA fallback, got $status" }
            }
            Write-Host "PASS: $relativeConfig blocks internal paths and preserves SPA fallback."
        } finally {
            if (-not $process.HasExited) { Stop-Process -Id $process.Id; $process.WaitForExit() }
        }
    }
} finally {
    foreach ($name in $savedVariables.Keys) { [Environment]::SetEnvironmentVariable($name, $savedVariables[$name], 'Process') }
}

param(
    [Parameter(Mandatory = $true)][string]$CaddyExecutable,
    [int]$Port = 18089,
    [string]$NodeExecutable = 'node'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$caddy = (Resolve-Path -LiteralPath $CaddyExecutable).Path
$fixtureDir = Join-Path $projectDir ('.artifacts/caddy-routing-tests/' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $fixtureDir -Force
[IO.File]::WriteAllText((Join-Path $fixtureDir 'index.html'), 'alpha-static-fixture')
foreach ($route in @('privacy', 'delete-account', 'terms')) {
    foreach ($language in @('vi', 'en')) {
        $suffix = if ($language -eq 'en') { '/en' } else { '' }
        $legalFixture = Join-Path $fixtureDir "legal/$route$suffix"
        $null = New-Item -ItemType Directory -Path $legalFixture -Force
        [IO.File]::WriteAllText((Join-Path $legalFixture 'index.html'), "legal-$route-$language")
    }
}
$savedVariables = @{}
foreach ($name in @('PORT', 'BACKEND_UPSTREAM', 'FASTTOWIN_DOMAIN', 'ACME_EMAIL')) {
    $savedVariables[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$backendPort = $Port + 1
$backend = Start-Process -FilePath $NodeExecutable -ArgumentList @(('"' + (Join-Path $PSScriptRoot 'caddy-backend-fixture.mjs') + '"'), "$backendPort") -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $fixtureDir 'backend.stdout.log') -RedirectStandardError (Join-Path $fixtureDir 'backend.stderr.log')
Add-Type -AssemblyName System.Net.Http
$http = New-Object System.Net.Http.HttpClient
$http.Timeout = [TimeSpan]::FromSeconds(3)
function Send-Request([string]$Path, [string]$Method = 'GET', [string]$Body = '', [bool]$AdminDisabled = $false) {
    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Method), "http://127.0.0.1:$Port$Path")
    $request.Headers.Add('Authorization', 'Bearer registered-fixture')
    if ($AdminDisabled) { $request.Headers.Add('X-Routing-Test-Admin', 'disabled') }
    if ($Body) { $request.Content = [System.Net.Http.StringContent]::new($Body, [Text.Encoding]::UTF8, 'application/json') }
    try {
        $response = $http.SendAsync($request).GetAwaiter().GetResult()
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return @{ Status = [int]$response.StatusCode; Content = $content; Headers = $response.Headers }
    } finally { $request.Dispose() }
}
try {
    $env:PORT = "$Port"
    $env:BACKEND_UPSTREAM = "127.0.0.1:$backendPort"
    $env:FASTTOWIN_DOMAIN = "http://127.0.0.1:$Port"
    $env:ACME_EMAIL = 'routing-test@example.invalid'
    foreach ($relativeConfig in @('deploy/railway/web/Caddyfile', 'deploy/Caddyfile')) {
        $adaptedText = (& $caddy adapt --config (Join-Path $projectDir $relativeConfig) --adapter caddyfile) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw "Caddy adaptation failed for $relativeConfig" }
        # Keep the actual adapted handlers; substitute only the static root,
        # loopback backend fixture/listener and the disabled local Caddy admin API.
        $rootJson = ConvertTo-Json -InputObject $fixtureDir -Compress
        $adapted = ($adaptedText.Replace('"root":"/srv"', '"root":' + $rootJson).Replace('server:8080', "127.0.0.1:$backendPort")) | ConvertFrom-Json
        # The production host's Caddy admin API also stays disabled in this local fixture.
        $adapted | Add-Member -NotePropertyName admin -NotePropertyValue @{ disabled = $true } -Force
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
            foreach ($path in @('/', '/rooms', '/gifts', '/gifts/history', '/gifts/mailboxish')) {
                $response = Invoke-WebRequest "http://127.0.0.1:$Port$path" -UseBasicParsing -TimeoutSec 3
                if ($response.StatusCode -ne 200 -or $response.Content -ne 'alpha-static-fixture') { throw "Static fallback failed for $relativeConfig $path" }
            }
            foreach ($route in @('privacy', 'delete-account', 'terms')) {
                foreach ($language in @('vi', 'en')) {
                    $suffix = if ($language -eq 'en') { '/en' } else { '' }
                    foreach ($trailing in @('', '/')) {
                        # No Authorization header: Play reviewers must read these while logged out.
                        $response = Invoke-WebRequest "http://127.0.0.1:$Port/$route$suffix$trailing" -UseBasicParsing -TimeoutSec 3
                        if ($response.Content -ne "legal-$route-$language") { throw "Legal route fell back to SPA: $relativeConfig /$route$suffix$trailing" }
                        if ($response.Headers['Cache-Control'] -notcontains 'no-cache') { throw 'Legal policy must revalidate' }
                    }
                }
            }
            $adminCsp = "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"
            foreach ($path in @('/admin', '/admin/', '/admin/admin.js', '/admin/api/session')) {
                foreach ($disabled in @($false, $true)) {
                    $response = Send-Request -Path $path -AdminDisabled $disabled
                    $expectedStatus = if ($disabled) { 404 } else { 200 }
                    if ($response.Status -ne $expectedStatus) { throw "$relativeConfig $path admin disabled=$disabled status=$($response.Status)" }
                    try { $json = $response.Content | ConvertFrom-Json } catch { throw "$relativeConfig $path returned SPA content instead of backend JSON" }
                    if ($json.source -ne 'backend' -or $json.path -ne $path) { throw "$relativeConfig $path did not reach backend" }
                    if ($response.Headers.CacheControl.ToString() -ne 'no-store') { throw "$relativeConfig $path lost backend no-store" }
                    $csp = @($response.Headers.GetValues('Content-Security-Policy'))
                    if ($csp.Count -ne 1 -or $csp[0] -ne $adminCsp) { throw "$relativeConfig $path changed backend admin CSP: $csp" }
                }
            }
            foreach ($prefix in @('/gifts', '/api/gifts')) {
                foreach ($case in @(
                    @{ Path = "$prefix/mailbox?page=2&pageSize=25"; Method = 'GET'; Body = '' },
                    @{ Path = "$prefix/mailbox/campaign/claim"; Method = 'POST'; Body = '' },
                    @{ Path = "$prefix/mailbox/claim-all"; Method = 'POST'; Body = '{"campaignIds":["campaign"]}' }
                )) {
                    $response = Send-Request -Path $case.Path -Method $case.Method -Body $case.Body
                    if ($response.Status -ne 200) { throw "$relativeConfig $($case.Path) status=$($response.Status)" }
                    try { $json = $response.Content | ConvertFrom-Json } catch { throw "$relativeConfig $($case.Path) returned SPA HTML instead of backend JSON" }
                    if ($json.source -ne 'backend' -or $json.path -ne $case.Path.Split('?')[0] -or $json.method -ne $case.Method -or $json.authorization -ne 'Bearer registered-fixture') { throw "$relativeConfig $($case.Path) failed backend forwarding" }
                    if ($case.Method -eq 'GET' -and $json.query -ne '?page=2&pageSize=25') { throw 'Mailbox query was lost' }
                    if ($case.Body -and $json.body.campaignIds[0] -ne 'campaign') { throw 'Claim-all body was lost' }
                    if ($response.Headers.CacheControl.ToString() -ne 'no-store') { throw 'Mailbox no-store was lost' }
                }
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
            Write-Host "PASS: $relativeConfig forwards both mailbox APIs/admin enabled+disabled, retains JSON/auth/body/CSP/no-store, blocks internal paths and preserves /gifts SPA; serves 12 logged-out legal URLs without SPA fallback."
        } finally {
            if (-not $process.HasExited) { Stop-Process -Id $process.Id; $process.WaitForExit() }
        }
    }
} finally {
    $http.Dispose()
    if (-not $backend.HasExited) { Stop-Process -Id $backend.Id; $backend.WaitForExit() }
    foreach ($name in $savedVariables.Keys) { [Environment]::SetEnvironmentVariable($name, $savedVariables[$name], 'Process') }
}

# Local-only acceptance fixture. Does not touch existing containers or Redis databases.
$ErrorActionPreference = 'Stop'
$runId = [guid]::NewGuid().ToString('N')
$name = "ftw-redis-test-$runId"
$image = 'redis@sha256:be7c1cada5a6eb1656adfa2635b99296af725881301b21f84f35a71fac072c7f'
$id = $null
$previousUrl = $env:FASTTOWIN_TEST_REDIS_URL
try {
    $id = docker run -d --rm --name $name --label "fasttowin.test-run=$runId" -p '127.0.0.1::6379' $image redis-server --save '' --appendonly no --maxmemory 64mb --maxmemory-policy noeviction
    if ($LASTEXITCODE -ne 0 -or $id -notmatch '^[0-9a-f]{64}$') { throw 'Could not start isolated Redis fixture.' }
    $ready = $false
    for ($i = 0; $i -lt 50; $i++) {
        $ping = docker exec $id redis-cli ping 2>$null
        if ($LASTEXITCODE -eq 0 -and $ping -eq 'PONG') { $ready = $true; break }
        Start-Sleep -Milliseconds 100
    }
    if (-not $ready) { throw 'Isolated Redis fixture did not become ready.' }
    $binding = docker port $id 6379/tcp
    if ($LASTEXITCODE -ne 0 -or $binding -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected Redis fixture binding.' }
    $env:FASTTOWIN_TEST_REDIS_URL = "redis://127.0.0.1:$($Matches[1])/0"
    & "$PSScriptRoot/../../gradlew.bat" :server:test --tests '*RedisRateLimitRoutesTest*' --tests '*RedisNettyAcceptanceTest*' --tests '*RedisRateLimiterTest*' --tests '*RedisFaultTest*' --tests '*RedisLoggingTest*' --no-daemon --no-configuration-cache --max-workers=1
    if ($LASTEXITCODE -ne 0) { throw 'Redis acceptance tests failed.' }
} finally {
    if ($null -eq $previousUrl) { Remove-Item Env:FASTTOWIN_TEST_REDIS_URL -ErrorAction SilentlyContinue }
    else { $env:FASTTOWIN_TEST_REDIS_URL = $previousUrl }
    if ($id -and $id -match '^[0-9a-f]{64}$') {
        $label = docker inspect --format '{{ index .Config.Labels "fasttowin.test-run" }}' $id 2>$null
        if ($LASTEXITCODE -eq 0 -and $label -eq $runId) { docker rm -f $id | Out-Null }
    }
}

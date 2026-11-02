param([string]$Bundle = '.artifacts/redis-cloud-acceptance-20261102/runner')
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath '.').Path
$bundlePath = (Resolve-Path -LiteralPath $Bundle).Path
$unique = [Guid]::NewGuid().ToString('N')
$label = "ftw-acceptance-$unique"
$dir = Join-Path $root ".artifacts/redis-cloud-acceptance-20261102/fixture-$unique"
$image = "ftw-redis-acceptance:$unique"
$networkId = $null; $redisId = $null
New-Item -ItemType Directory -Force $dir | Out-Null
try {
    $password = 'sentinel' + [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant()
    @('bind 0.0.0.0', 'protected-mode no', 'port 6379', 'maxmemory 64mb', 'maxmemory-policy noeviction', 'save ""', 'appendonly no', 'aclfile /fixture/users.acl') | Set-Content -Encoding ascii (Join-Path $dir 'redis.conf')
    @('user default off', "user fixture on >$password ~ftw:* +@read +@write +eval +ping +time +select +hello +client|setinfo") | Set-Content -Encoding ascii (Join-Path $dir 'users.acl')
    @("FASTTOWIN_TEST_REDIS_URL=redis://fixture:${password}@redis:6379/3", 'REDIS_ACCEPTANCE_DURATION_SECONDS=1', 'REDIS_ACCEPTANCE_CONSUMES_PER_SECOND=10') | Set-Content -Encoding ascii (Join-Path $dir 'good.env')
    @('FASTTOWIN_TEST_REDIS_URL=redis://fixture:incorrectsentinel@redis:6379/3', 'REDIS_ACCEPTANCE_DURATION_SECONDS=1') | Set-Content -Encoding ascii (Join-Path $dir 'wrong.env')
    'REDIS_ACCEPTANCE_DURATION_SECONDS=1' | Set-Content -Encoding ascii (Join-Path $dir 'missing.env')
    docker build --quiet --label "fasttowin.owner=$label" -f deploy/railway/redis-acceptance/Dockerfile -t $image $bundlePath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Image build failed' }
    if ((docker image inspect --format '{{.Config.User}}' $image) -ne '10001:10001') { throw 'Image is not non-root' }
    $networkId = docker network create --internal --label "fasttowin.owner=$label" $label
    if ($LASTEXITCODE -ne 0) { throw 'Network create failed' }
    $redisId = docker run --detach --network $networkId --network-alias redis --label "fasttowin.owner=$label" --memory 128m --cpus 1 --mount "type=bind,src=$dir,dst=/fixture,readonly" redis@sha256:be7c1cada5a6eb1656adfa2635b99296af725881301b21f84f35a71fac072c7f redis-server /fixture/redis.conf
    if ($LASTEXITCODE -ne 0) { throw 'Redis start failed' }
    function Run-Case([string]$file, [bool]$success, [string]$expectedReason, [bool]$tampered = $false) {
        $arguments = @('run', '--rm', '--network', $networkId, '--env-file', (Join-Path $dir $file))
        if ($tampered) { $arguments += @('--mount', "type=bind,src=$(Join-Path $dir 'bad-revision'),dst=/app/REVISION,readonly") }
        $arguments += $image
        $output = (& docker @arguments 2>&1 | Out-String)
        $code = $LASTEXITCODE
        if ($output.Contains($password) -or $output.Contains('incorrectsentinel') -or $output.Contains('redis://')) { throw 'Secret appeared in process output' }
        if (($code -eq 0) -ne $success) { throw "Unexpected exit for $file : $output" }
        $report = $output.Trim() | ConvertFrom-Json
        if ($report.reason -ne $expectedReason) { throw "Unexpected report for $file : $output" }
        if ($success -and ($report.cases_passed -ne 13 -or $report.cases_failed -ne 0 -or $report.consume_total -ne 10 -or $report.consume_allowed -ne 10 -or $report.consume_limited -ne 0 -or $report.consume_unavailable -ne 0)) { throw 'Missing case or load outcome' }
        $evidenceName = if ($tampered) { 'tampered' } elseif ($success) { 'success' } else { [IO.Path]::GetFileNameWithoutExtension($file) }
        $output.Trim() | Set-Content -Encoding ascii (Join-Path (Split-Path $bundlePath -Parent) "report-$evidenceName.json")
        Write-Output "$file : $($report.status), $($report.reason), cases=$($report.cases_passed), consume=$($report.consume_total)"
    }
    'tampered' | Set-Content -Encoding ascii (Join-Path $dir 'bad-revision')
    Run-Case 'missing.env' $false 'missing_url'
    Run-Case 'wrong.env' $false 'case_failed'
    Run-Case 'good.env' $false 'invalid_bundle' $true
    Run-Case 'good.env' $true 'ok'
} finally {
    if ($redisId) {
        $identity = docker inspect --format '{{.Id}} {{index .Config.Labels "fasttowin.owner"}}' $redisId
        if ($identity -eq "$redisId $label") { docker rm -f $redisId | Out-Null } else { Write-Error 'Redis identity mismatch; retained' }
    }
    if ($networkId) {
        $identity = docker network inspect --format '{{.Id}} {{index .Labels "fasttowin.owner"}}' $networkId
        if ($identity -eq "$networkId $label") { docker network rm $networkId | Out-Null } else { Write-Error 'Network identity mismatch; retained' }
    }
    $resolved = (Resolve-Path -LiteralPath $dir).Path
    if ($resolved -eq $dir -and $dir.StartsWith((Join-Path $root '.artifacts/redis-cloud-acceptance-20261102/fixture-'))) { Remove-Item -LiteralPath $dir -Recurse -Force }
}

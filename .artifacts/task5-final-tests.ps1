$ErrorActionPreference = 'Stop'
$endpoint = 'npipe:////./pipe/docker_engine'
if ($env:DOCKER_HOST -and $env:DOCKER_HOST -cne $endpoint) { throw 'Unexpected Docker endpoint' }
$identity = 'room-task5-tests-' + [guid]::NewGuid().ToString('N')
$label = "fasttowin.task5-tests=$identity"
function D([string[]]$Arguments) {
    & docker --host $endpoint @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Local test Docker command failed' }
}
$beforeVolumes = @(D @('volume','ls','-q'))
$env:POSTGRES_PASSWORD = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:TEST_DATABASE_PASSWORD = $env:POSTGRES_PASSWORD
$env:TEST_DATABASE_USER = 'fasttowin'
$env:FASTTOWIN_TEST_ROOM_OWNERSHIP = 'true'
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:TEMP = 'D:/HienTX/Work/Android/FastToWin/.artifacts/java-tmp'
$env:TMP = $env:TEMP
$owned = $false
try {
    if (@(D @('ps','-aq','--filter',"label=$label")).Count) { throw 'Fixture collision' }
    $owned = $true
    D @('network','create','--internal','--label',$label,$identity) | Out-Null
    $testPort = 40000 + (Get-Random -Minimum 0 -Maximum 1000)
    D @('run','-d','--pull','never','--name',$identity,'--label',$label,'--network',$identity,
        '--cap-drop','ALL','--security-opt','no-new-privileges:true','--user','postgres',
        '-e','POSTGRES_PASSWORD','-e','POSTGRES_USER=fasttowin','-e','POSTGRES_DB=admin_test','-e','PGDATA=/tmp/test-postgres',
        '-p',"127.0.0.1:$testPort`:5432",
        'postgres@sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395') | Out-Null
    $ready = $false
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        & docker --host $endpoint exec $identity pg_isready -h 127.0.0.1 -U fasttowin *> $null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'PostgreSQL test readiness failed' }
    $env:TEST_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$testPort/admin_test"
    & ./gradlew.bat :server:test --tests 'com.hienthai.fastowin.server.ownership.*' --tests 'com.hienthai.fastowin.server.activity.*' --offline --no-daemon --no-configuration-cache --max-workers=1 --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Affected integration tests failed' }
    $counts = @(Get-ChildItem server/build/test-results/test -Filter TEST-*.xml | ForEach-Object {
        [xml]$xml=Get-Content -Raw -LiteralPath $_.FullName
        [ordered]@{name=$xml.testsuite.name;tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped}
    })
    $counts | ConvertTo-Json -Depth 4 | Set-Content .artifacts/task5-final-test-counts.json -Encoding utf8NoBOM
} finally {
    if ($owned) {
        foreach ($id in @(D @('ps','-aq','--filter',"label=$label"))) { D @('rm','-fv',$id) | Out-Null }
        foreach ($id in @(D @('network','ls','-q','--filter',"label=$label"))) { D @('network','rm',$id) | Out-Null }
        if (@(D @('ps','-aq','--filter',"label=$label")).Count -or @(D @('network','ls','-q','--filter',"label=$label")).Count) { throw 'Owned test resource remains' }
        $afterVolumes = @(D @('volume','ls','-q'))
        if (Compare-Object ($beforeVolumes | Sort-Object) ($afterVolumes | Sort-Object)) { throw 'Test volume inventory changed' }
        [ordered]@{containers=0;networks=0;volumesUnchanged=$true} | ConvertTo-Json | Set-Content .artifacts/task5-final-test-cleanup.json -Encoding utf8NoBOM
    }
    Remove-Item Env:POSTGRES_PASSWORD,Env:TEST_DATABASE_PASSWORD,Env:TEST_DATABASE_USER,Env:TEST_DATABASE_URL,Env:FASTTOWIN_TEST_ROOM_OWNERSHIP -ErrorAction SilentlyContinue
}

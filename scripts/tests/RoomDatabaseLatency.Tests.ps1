#Requires -Version 7.0
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$values=@{
    FTW_PILOT_NAME='room-pilot-012345abcdef';FTW_PILOT_BACKEND_IMAGE='fixture-backend:local';FTW_PILOT_RUNNER_IMAGE='fixture-runner:local'
    FTW_PILOT_IPV6_SUBNET='fd88:1234:5678::/64';FTW_PILOT_DEADLINE_UTC='2030-01-01T00:00:00Z'
    FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD='synthetic-only';FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD='synthetic-only'
    FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID='00000000-0000-4000-8000-000000000001';FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS='00000000-0000-4000-8000-000000000002'
    FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET='synthetic-only'
    FTW_PILOT_DATABASE_LATENCY_CPU='1'
    FTW_PILOT_BACKEND_POOL_SIZE='10'
}
$saved=@{}
try{
    foreach($key in $values.Keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$values[$key],'Process')}
    $raw=& docker compose -f (Join-Path $root 'deploy/room-ownership/compose.staging-baseline.yaml') -f (Join-Path $root 'deploy/room-ownership/compose.database-latency.yaml') config --format json
    if($LASTEXITCODE){throw 'Latency compose config failed'}
    $config=($raw -join "`n")|ConvertFrom-Json
    if('room-pilot-012345abcdef-pg.railway.internal' -in $config.services.postgres.networks.fixture.aliases){throw 'PostgreSQL bypass alias remains'}
    if('room-pilot-012345abcdef-pg.railway.internal' -notin $config.services.'database-latency'.networks.fixture.aliases){throw 'Proxy missing strict fixture DNS alias'}
    foreach($name in @('backend-a','backend-b')){if($config.services.$name.environment.DATABASE_URL -ne 'jdbc:postgresql://room-pilot-012345abcdef-pg.railway.internal:5433/fasttowin_room_pilot'){throw 'Backend bypasses latency proxy'}}
    if($config.services.'database-latency'.ports -or -not $config.networks.fixture.internal -or $config.services.'database-latency'.image -notmatch '@sha256:[a-f0-9]{64}$'){throw 'Proxy must be private and digest-pinned'}
    $proxies=Get-Content -Raw (Join-Path $root 'deploy/room-ownership/toxiproxy-database-latency.json')|ConvertFrom-Json
    if(@($proxies).Count -ne 2 -or @($proxies|Where-Object{$_.upstream -ne 'postgres:5432' -or -not $_.enabled}).Count){throw 'Unexpected proxy upstream'}
    if(($proxies|Where-Object name -eq 'backend').listen -ne '[::]:5433' -or ($proxies|Where-Object name -eq 'fixture').listen -ne '[::]:5432'){throw 'Wrong latency paths'}
    if($config.services.'database-latency'.cpus -ne 1){throw 'Default proxy CPU ceiling changed'}
    $env:FTW_PILOT_DATABASE_LATENCY_CPU='2'
    $raw=& docker compose -f (Join-Path $root 'deploy/room-ownership/compose.staging-baseline.yaml') -f (Join-Path $root 'deploy/room-ownership/compose.database-latency.yaml') config --format json
    if($LASTEXITCODE){throw 'Proxy CPU control config failed'}
    $control=($raw -join "`n")|ConvertFrom-Json
    if($control.services.'database-latency'.cpus -ne 2){throw 'Explicit proxy-only 2-CPU control was not applied'}
    foreach($name in @('backend-a','backend-b','postgres','redis','runner')){
        if(($control.services.$name|ConvertTo-Json -Depth 20 -Compress) -cne ($config.services.$name|ConvertTo-Json -Depth 20 -Compress)){throw 'Proxy CPU control altered an application or database service'}
    }
    foreach($name in @('backend-a','backend-b')){if($control.services.$name.environment.DATABASE_POOL_SIZE -ne '10'){throw 'Default backend pool must remain ten'}}
    $env:FTW_PILOT_BACKEND_POOL_SIZE='20'
    $raw=& docker compose -f (Join-Path $root 'deploy/room-ownership/compose.staging-baseline.yaml') -f (Join-Path $root 'deploy/room-ownership/compose.database-latency.yaml') config --format json
    if($LASTEXITCODE){throw 'Backend pool control config failed'}
    $pool=($raw -join "`n")|ConvertFrom-Json
    foreach($name in @('backend-a','backend-b')){
        if($pool.services.$name.environment.DATABASE_POOL_SIZE -ne '20'){throw 'Backend pool control was not applied'}
        $pool.services.$name.environment.DATABASE_POOL_SIZE='10'
    }
    foreach($name in @('backend-a','backend-b','postgres','redis','runner','database-latency')){
        if(($pool.services.$name|ConvertTo-Json -Depth 20 -Compress) -cne ($control.services.$name|ConvertTo-Json -Depth 20 -Compress)){throw 'Backend pool control changed another setting'}
    }
    Write-Output 'PASS: private proxy DNS, separate DB paths, pinned image and no direct PostgreSQL alias.'
}finally{foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}}

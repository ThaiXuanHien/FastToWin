#Requires -Version 7.0
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$values=@{
    FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD='synthetic-only';FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD='synthetic-only'
    FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID='00000000-0000-4000-8000-000000000001';FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS='00000000-0000-4000-8000-000000000002'
    FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET='synthetic-only';FASTTOWIN_ROOM_OWNERSHIP_SUBNET_PREFIX='172.31.231';FASTTOWIN_ROOM_OWNERSHIP_PORT='18080'
    FTW_ACCEPTANCE_PROJECT='room-pilot-012345abcdef';FTW_ACCEPTANCE_POSTGRES_PORT='15432'
    FTW_ACCEPTANCE_A_PORT='18081';FTW_ACCEPTANCE_B_PORT='18082';FTW_ACCEPTANCE_PROXY_A_PORT='18474';FTW_ACCEPTANCE_PROXY_B_PORT='18475'
    FTW_ACCEPTANCE_BACKEND_POOL_SIZE='10'
}
$saved=@{}
function Read-Config {
    $raw=& docker compose -f (Join-Path $root 'deploy/room-ownership/compose.local.yaml') -f (Join-Path $root 'deploy/room-ownership/compose.acceptance.yaml') config --format json
    if($LASTEXITCODE){throw 'Local acceptance config failed'}
    ($raw -join "`n")|ConvertFrom-Json
}
try{
    foreach($key in $values.Keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$values[$key],'Process')}
    $baseline=Read-Config
    foreach($name in @('backend-a','backend-b')){if($baseline.services.$name.environment.DATABASE_POOL_SIZE -ne '10'){throw 'Default local backend pool must remain ten'}}
    $env:FTW_ACCEPTANCE_BACKEND_POOL_SIZE='20'
    $control=Read-Config
    foreach($name in @('backend-a','backend-b')){
        if($control.services.$name.environment.DATABASE_POOL_SIZE -ne '20'){throw 'Local backend pool control was not applied'}
        $control.services.$name.environment.DATABASE_POOL_SIZE='10'
    }
    foreach($name in $baseline.services.PSObject.Properties.Name){
        if(($control.services.$name|ConvertTo-Json -Depth 25 -Compress) -cne ($baseline.services.$name|ConvertTo-Json -Depth 25 -Compress)){throw 'Local backend pool control changed another setting'}
    }
    Write-Output 'PASS: local fault/load pool control changes only the two backend pool settings.'
}finally{foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}}

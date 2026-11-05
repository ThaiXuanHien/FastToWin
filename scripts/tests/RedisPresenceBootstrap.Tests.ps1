param(
 [ValidatePattern('^[a-z0-9-]+$')][string]$EvidenceName = 'green'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$id = [guid]::NewGuid().ToString('N')
$owner = "ftw-presence-acl-$id"
$image = "ftw-presence-acl:$id"
$base = 'redis@sha256:be7c1cada5a6eb1656adfa2635b99296af725881301b21f84f35a71fac072c7f'
$evidence = Join-Path $root '.artifacts/redis-presence-production-20261104/acl'
$private = Join-Path $evidence "private-$id"
$containers = [Collections.Generic.List[string]]::new()
$checks = [Collections.Generic.List[string]]::new()
$volume = $null
$secrets = @()
$failure = $null
$resourcesRemoved = $false
$started = [DateTime]::UtcNow.ToString('o')
function Safe([string]$value) {
 foreach ($secret in $secrets) { if ($secret) { $value = $value.Replace($secret, '[redacted]') } }
 return $value -replace '[0-9a-f]{96}', '[redacted]'
}
function D([string[]]$arguments) {
 $output = & docker @arguments 2>&1
 if ($LASTEXITCODE) { throw (Safe "Docker $($arguments[0]) failed: $($output -join ' ')") }
 return $output
}
function Check([bool]$ok, [string]$message) {
 if (!$ok) { throw $message }
 $checks.Add($message)
 Write-Output "PASS $message"
}
function Remove-OwnedContainer([string]$container) {
 if ((D @('inspect', '--format', '{{.Id}} {{index .Config.Labels "fasttowin.owner"}}', $container)) -ne "$container $owner") { throw 'Container identity mismatch' }
 D @('rm', '-f', $container) | Out-Null
 [void]$containers.Remove($container)
}
function Start-Fixture([string]$envFile, [bool]$persistent = $false) {
 $mount = if ($persistent) { "type=volume,src=$volume,dst=/data" } else { 'type=tmpfs,dst=/data' }
 $container = (D @('run', '-d', '--pull=never', '--label', "fasttowin.owner=$owner", '--env-file', $envFile, '--mount', $mount, $image)) -join ''
 $containers.Add($container)
 return $container
}
function Redis([string]$container, [string]$role, [string[]]$command) {
 $variable = switch ($role) {
  app { 'FASTTOWIN_REDIS_APP_PASSWORD' }
  previous { 'FASTTOWIN_REDIS_APP_PREVIOUS_PASSWORD' }
  monitor { 'FASTTOWIN_REDIS_MONITOR_PASSWORD' }
  backup { 'FASTTOWIN_REDIS_BACKUP_PASSWORD' }
  presence { 'FASTTOWIN_REDIS_PRESENCE_PASSWORD' }
 }
 $username = if ($role -eq 'previous') { 'ftw_app' } else { "ftw_$role" }
 # Only the variable name is placed in argv; credentials remain in the container environment.
 $shell = 'export REDISCLI_AUTH="$' + $variable + '"; exec redis-cli --user ' + $username + ' --raw "$@"'
 return (D (@('exec', $container, 'sh', '-c', $shell, '--') + $command)) -join "`n"
}
function Wait-Ready([string]$container) {
 for ($attempt = 0; $attempt -lt 40; $attempt++) {
  if ((D @('inspect', '--format', '{{.State.Running}}', $container)) -ne 'true') { throw 'Redis fixture exited before readiness' }
  try { if ((Redis $container app @('PING')) -eq 'PONG') { return } }
  catch { if ($_.Exception.Message -notmatch 'Connection refused|Could not connect to Redis') { throw } }
  Start-Sleep -Milliseconds 100
 }
 throw 'Redis fixture readiness timeout'
}
function No-Secrets([string]$text, [string]$location) {
 foreach ($secret in $secrets) { if ($text.Contains($secret)) { throw "Credential leaked in $location" } }
}
try {
 New-Item -ItemType Directory -Force $private | Out-Null
 # Keep generated local fixture credentials private even when inherited workspace ACLs are broad.
 $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
 & icacls $private /inheritance:r /grant:r "*${sid}:(OI)(CI)F" | Out-Null
 if ($LASTEXITCODE) { throw 'Could not restrict private fixture directory' }
 $secrets = @(1..5 | ForEach-Object { [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(48)).ToLowerInvariant() })
 $valid = @("FASTTOWIN_REDIS_APP_PASSWORD=$($secrets[0])", "FASTTOWIN_REDIS_MONITOR_PASSWORD=$($secrets[1])", "FASTTOWIN_REDIS_BACKUP_PASSWORD=$($secrets[2])", 'FASTTOWIN_RATE_LIMIT_NAMESPACE=prod_main')
 $legacyFile = Join-Path $private 'legacy.env'
 $valid | Set-Content -Encoding ascii $legacyFile
 $presentFile = Join-Path $private 'presence.env'
 @($valid + "FASTTOWIN_REDIS_APP_PREVIOUS_PASSWORD=$($secrets[3])" + "FASTTOWIN_REDIS_PRESENCE_PASSWORD=$($secrets[4])") | Set-Content -Encoding ascii $presentFile
 # No pull or network is needed: require the pinned base to already exist locally.
 D @('image', 'inspect', '--format', '{{.Id}}', $base) | Out-Null
 D @('build', '--pull=false', '--network=none', '-q', '--label', "fasttowin.owner=$owner", '-t', $image, (Join-Path $root 'deploy/railway/redis')) | Out-Null
 $manifest = @('Dockerfile', 'boot.sh', 'backup.sh') | ForEach-Object {
  $path = Join-Path $root "deploy/railway/redis/$_"
  "$( (Get-FileHash -Algorithm SHA256 $path).Hash.ToLowerInvariant())  $_"
 }
 $manifest | Set-Content -Encoding ascii (Join-Path $evidence "$EvidenceName-context.sha256")
 No-Secrets ($manifest -join "`n") 'context manifest'
 $imageId = (D @('image', 'inspect', '--format', '{{.Id}}', $image)) -join ''

 $cid = Start-Fixture $legacyFile
 Wait-Ready $cid
 foreach ($role in @('app', 'monitor', 'backup')) { Check ((Redis $cid $role @('PING')) -eq 'PONG') "Legacy $role authenticates without presence configuration" }
 $names = (D @('exec', $cid, 'sh', '-c', "awk '{print `$2}' /run/ftw-redis/users.acl | sort")) -join ','
 Check ($names -eq 'default,ftw_app,ftw_backup,ftw_monitor') 'Absent presence secret preserves exactly the legacy roles'
 foreach ($command in @(@('HSET', 'ftw:other:rl:v1:x', 'f', 'v'), @('HSET', 'ftw:prod_main:presence:v1:x', 'f', 'v'), @('ACL', 'USERS'))) {
  Check ((Redis $cid app $command) -match 'NOPERM') "Legacy app scope denies $($command[0]) outside quota permissions"
 }
 Check ((Redis $cid monitor @('INFO', 'server')) -match 'redis_version:') 'Legacy monitor keeps INFO access'
 Check ((Redis $cid monitor @('GET', 'ftw:prod_main:rl:v1:x')) -match 'NOPERM') 'Legacy monitor has no key access'
 Check ((Redis $cid backup @('SET', 'ftw:prod_main:ops:backup-fence', 'fixture')) -eq 'OK') 'Legacy backup keeps fence write access'
 Check ((Redis $cid backup @('SET', 'ftw:prod_main:rl:v1:x', 'x')) -match 'NOPERM') 'Legacy backup cannot write quota keys'
 No-Secrets ((D @('logs', $cid)) -join "`n") 'legacy stdout/stderr'
 Remove-OwnedContainer $cid

 $invalids = [ordered]@{
  short = 'a' * 95
  long = 'a' * 97
  uppercase = 'A' * 96
  nonhex = 'g' * 96
  whitespace = ' ' + ('a' * 95)
  app_collision = $secrets[0]
  monitor_collision = $secrets[1]
  backup_collision = $secrets[2]
  previous_collision = $secrets[3]
 }
 foreach ($entry in $invalids.GetEnumerator()) {
  $envFile = Join-Path $private 'invalid.env'
  @($valid + "FASTTOWIN_REDIS_APP_PREVIOUS_PASSWORD=$($secrets[3])" + "FASTTOWIN_REDIS_PRESENCE_PASSWORD=$($entry.Value)") | Set-Content -Encoding ascii $envFile
  $bad = Start-Fixture $envFile
  # Bound the check even when a missing validation accidentally allows Redis to boot.
  Start-Sleep -Milliseconds 350
  $state = (D @('inspect', '--format', '{{.State.Running}} {{.State.ExitCode}}', $bad)) -join ''
  $logs = (D @('logs', $bad)) -join "`n"
  No-Secrets $logs 'invalid configuration stdout/stderr'
  Check ($state -eq 'false 1' -and $logs.Trim() -eq 'Invalid Redis configuration') "Reject presence $($entry.Key) before Redis starts"
  Remove-OwnedContainer $bad
 }

 $volume = (D @('volume', 'create', '--label', "fasttowin.owner=$owner", $owner)) -join ''
 $cid = Start-Fixture $presentFile $true
 Wait-Ready $cid
 foreach ($role in @('app', 'previous', 'monitor', 'backup', 'presence')) { Check ((Redis $cid $role @('PING')) -eq 'PONG') "Configured $role authenticates" }
 $names = (D @('exec', $cid, 'sh', '-c', "awk '{print `$2}' /run/ftw-redis/users.acl | sort")) -join ','
 Check ($names -eq 'default,ftw_app,ftw_backup,ftw_monitor,ftw_presence') 'Configured presence adds exactly one role'
 Check ((Redis $cid presence @('HELLO', '2')) -match 'server') 'Presence HELLO handshake'
 Check ((Redis $cid presence @('CLIENT', 'SETINFO', 'LIB-NAME', 'acl-fixture')) -eq 'OK') 'Presence CLIENT SETINFO handshake'
 Check ((Redis $cid presence @('SELECT', '0')) -eq 'OK') 'Presence selects configured database'
 Check (((D @('exec', $cid, 'redis-cli', 'PING')) -join '') -match 'NOAUTH') 'Default unauthenticated user remains disabled'
 $update = [IO.File]::ReadAllText((Join-Path $root 'server/src/main/resources/redis/presence_update.lua'))
 $read = [IO.File]::ReadAllText((Join-Path $root 'server/src/main/resources/redis/presence_read.lua'))
 $quota = [IO.File]::ReadAllText((Join-Path $root 'server/src/main/resources/redis/rate_limit.lua'))
 $keys = @('ftw:prod_main:presence:v1:fixture:online', 'ftw:prod_main:presence:v1:fixture:room', 'ftw:prod_main:presence:v1:fixture:match')
 foreach ($state in @('1', '2', '3')) {
  Check ((Redis $cid presence (@('EVAL', $update, '3') + $keys + @('socket-a', $state, '60000'))) -eq '1') "Actual Lua renew state $state"
  Check ((Redis $cid presence (@('EVAL', $read, '3') + $keys)) -eq $state) "Actual Lua read state $state"
 }
 Redis $cid presence (@('EVAL', $update, '3') + $keys + @('socket-b', '2', '60000')) | Out-Null
 Check ((Redis $cid presence (@('EVAL', $update, '3') + $keys + @('socket-a', '0', '60000'))) -eq '1') 'Actual Lua revokes old socket'
 Check ((Redis $cid presence (@('EVAL', $read, '3') + $keys)) -eq '2') 'Old socket revoke preserves newer socket lease'
 Redis $cid presence (@('EVAL', $update, '3') + $keys + @('socket-b', '0', '60000')) | Out-Null
 Check ((Redis $cid presence (@('EVAL', $read, '3') + $keys)) -eq '0') 'Actual Lua revoke clears final socket'
 foreach ($role in @('app', 'monitor', 'backup', 'presence')) {
  foreach ($command in @(@('FLUSHALL'), @('FLUSHDB'), @('CONFIG', 'GET', '*'), @('ACL', 'USERS'), @('ACL', 'SETUSER', 'unexpected', 'on'), @('GET', 'ftw:other:presence:v1:x'))) {
   Check ((Redis $cid $role $command) -match 'NOPERM') "$role denies $($command[0]) administrative or out-of-scope command"
  }
 }
 foreach ($key in @('ftw:other:presence:v1:x', 'ftw:prod_main:rl:v1:x', 'ftw:prod_main:ops:backup-fence')) {
  Check ((Redis $cid presence @('ZADD', $key, '1', 'socket')) -match 'NOPERM') "Presence denies write to $key"
  Check ((Redis $cid presence @('ZCOUNT', $key, '-inf', '+inf')) -match 'NOPERM') "Presence denies read of $key"
  Check ((Redis $cid presence @('EVAL', $read, '3', $key, $key, $key)) -match 'NOPERM') "Presence EVAL denies $key"
 }
 Check ((Redis $cid app (@('EVAL', $read, '3') + $keys)) -match 'NOPERM') 'App EVAL cannot access presence keys'
 Check ((Redis $cid app @('HSET', $keys[0], 'f', 'v')) -match 'NOPERM') 'App cannot write presence keys'
 Check ((Redis $cid monitor @('INFO', 'memory')) -match 'used_memory:') 'Configured monitor retains INFO'
 Check ((Redis $cid backup @('SET', 'ftw:prod_main:ops:backup-fence', 'fixture')) -eq 'OK') 'Configured backup retains fence write'
 Check ((Redis $cid backup @('SET', $keys[0], 'x')) -match 'NOPERM') 'Configured backup cannot write presence keys'
 $quotaKey = 'ftw:prod_main:rl:v1:restart-fixture'
 Check ((Redis $cid app @('EVAL', $quota, '1', $quotaKey, '1', '86400000')) -eq "1`n0") 'Actual quota Lua consumes capacity one'
 Check ((Redis $cid app @('EVAL', $quota, '1', $quotaKey, '1', '86400000')) -match '^0\n[1-9][0-9]*$') 'Actual quota Lua rejects exhausted capacity'
 # Only the test invocation shortens the lease; production Lua and bootstrap stay unchanged.
 Redis $cid presence (@('EVAL', $update, '3') + $keys + @('socket-before-restart', '1', '3000')) | Out-Null
 Check ((Redis $cid presence (@('EVAL', $read, '3') + $keys)) -eq '1') 'Lease exists before restart'
 # Delete generated /run config before restart: success proves boot regenerates role credentials from env.
 D @('exec', $cid, 'rm', '/run/ftw-redis/users.acl', '/run/ftw-redis/redis.conf') | Out-Null
 D @('restart', $cid) | Out-Null
 Wait-Ready $cid
 Check ((Redis $cid presence @('PING')) -eq 'PONG') 'Restart regenerates presence authentication on the same volume'
 Check ((Redis $cid app @('EVAL', $quota, '1', $quotaKey, '1', '86400000')) -match '^0\n[1-9][0-9]*$') 'Restart preserves exhausted quota in AOF'
 Start-Sleep -Milliseconds 3100
 Check ((Redis $cid presence (@('EVAL', $read, '3') + $keys)) -eq '0') 'Old lease expires naturally across restart'
 Check ((Redis $cid presence (@('EVAL', $update, '3') + $keys + @('socket-after-restart', '2', '60000'))) -eq '1') 'New socket renews after restart'
 Check ((Redis $cid presence (@('EVAL', $read, '3') + $keys)) -eq '2') 'New socket is readable after restart'
 $tag = [guid]::NewGuid().ToString('N')
 $backupResult = (D @('exec', $cid, 'sh', '/usr/local/bin/ftw-redis-backup.sh', $tag)) -join ''
 No-Secrets $backupResult 'backup receipt'
 Check ($backupResult -match '"backup":1') 'Production backup runs with presence configured'
 $entries = @(D @('exec', $cid, 'tar', '-tzf', "/tmp/ftw-backup-$tag.tar.gz"))
 Check (@($entries | Where-Object { $_ -notmatch '^(appendonlydir/|appendonlydir/appendonly[.]aof[.](manifest|[0-9]+[.](base[.]rdb|incr[.]aof))|SHA256SUMS)$' }).Count -eq 0) 'Backup archive contains only AOF data and checksum manifest'
 D @('exec', $cid, 'mkdir', '/tmp/acl-backup-check') | Out-Null
 D @('exec', $cid, 'tar', '-xzf', "/tmp/ftw-backup-$tag.tar.gz", '-C', '/tmp/acl-backup-check') | Out-Null
 $verified = @(D @('exec', $cid, 'sh', '-c', 'cd /tmp/acl-backup-check && sha256sum -c SHA256SUMS'))
 Check (@($verified | Where-Object { $_ -notmatch ': OK$' }).Count -eq 0) 'Backup archive data checksums verify'
 $scan = 'if grep -R -a -F -q -e "$FASTTOWIN_REDIS_APP_PASSWORD" -e "$FASTTOWIN_REDIS_MONITOR_PASSWORD" -e "$FASTTOWIN_REDIS_BACKUP_PASSWORD" -e "$FASTTOWIN_REDIS_APP_PREVIOUS_PASSWORD" -e "$FASTTOWIN_REDIS_PRESENCE_PASSWORD" /tmp/acl-backup-check; then exit 1; else test "$?" = 1; fi'
 D @('exec', $cid, 'sh', '-c', $scan) | Out-Null
 Check $true 'Backup archive contains no fixture credentials'
 No-Secrets ((D @('logs', $cid)) -join "`n") 'configured stdout/stderr'
 Check $true 'Bootstrap and backup stdout/stderr contain no fixture credentials'
} catch {
 $failure = Safe $_.Exception.Message
 Write-Output "FAIL $failure"
} finally {
 try {
  foreach ($container in @($containers.ToArray())) { Remove-OwnedContainer $container }
  if ($volume) {
   if ((D @('volume', 'inspect', '--format', '{{.Name}} {{index .Labels "fasttowin.owner"}}', $volume)) -ne "$volume $owner") { throw 'Volume identity mismatch' }
   D @('volume', 'rm', $volume) | Out-Null
  }
  $imageOwner = & docker image inspect --format '{{index .Config.Labels "fasttowin.owner"}}' $image 2>$null
  if ($LASTEXITCODE -eq 0) {
   if ($imageOwner -ne $owner) { throw 'Image owner mismatch' }
   D @('image', 'rm', $image) | Out-Null
  }
  $resourcesRemoved = $true
  if (Test-Path -LiteralPath $private) {
   $resolved = (Resolve-Path -LiteralPath $private).Path
   $allowed = [IO.Path]::GetFullPath($evidence).TrimEnd('\') + '\'
   if (!$resolved.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path $resolved -Leaf) -ne "private-$id") { throw 'Private cleanup target outside owned workspace directory' }
   Remove-Item -LiteralPath $resolved -Recurse -Force
  }
 } catch { $failure = Safe "Cleanup failed: $($_.Exception.Message); test failure: $failure" }
 $receipt = [ordered]@{ startedUtc = $started; finishedUtc = [DateTime]::UtcNow.ToString('o'); result = $(if ($failure) { 'fail' } else { 'pass' }); base = $base; imageId = $imageId; checks = $checks.ToArray(); failure = $failure; fixtureOwner = $owner; dockerResourcesRemoved = $resourcesRemoved; cleanup = $(if (Test-Path -LiteralPath $private) { 'private directory retained; inspect locally' } else { 'private directory removed' }) }
 $json = $receipt | ConvertTo-Json -Depth 4
 No-Secrets $json 'evidence receipt'
 $json | Set-Content -Encoding utf8 (Join-Path $evidence "$EvidenceName.json")
}
if ($failure) { throw $failure }
Write-Output "All $($checks.Count) Redis presence bootstrap checks passed."

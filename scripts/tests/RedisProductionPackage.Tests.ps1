$ErrorActionPreference='Stop'
$root=(Resolve-Path '.').Path; $id=[guid]::NewGuid().ToString('N'); $label="ftw-prod-$id"; $dir=Join-Path $root ".artifacts/$label"; $image="ftw-prod:$id"; $cid=$null; $volume=$null
New-Item -ItemType Directory -Force $dir | Out-Null
function D([string[]]$args_) { $o=& docker @args_ 2>&1; if($LASTEXITCODE){$safe=$o -join " ";foreach($secret in $secrets){$safe=$safe.Replace($secret,"[redacted]")};throw "Docker $($args_[0]) failed: $safe"}; return $o }
function Check([bool]$value,[string]$message){if(!$value){throw $message}; Write-Output "PASS $message"}
function Remove-Owned([string]$id_){if((D @('inspect','--format','{{.Id}} {{index .Config.Labels "fasttowin.owner"}}',$id_)) -ne "$id_ $label"){throw 'Container identity mismatch'}; D @('rm','-f',$id_)|Out-Null}
try {
 $secrets=@(1..4|ForEach-Object {[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(48)).ToLowerInvariant()}); $app=$secrets[0];$monitor=$secrets[1];$backup=$secrets[2];$previous=$secrets[3]
 $valid=@("FASTTOWIN_REDIS_APP_PASSWORD=$app","FASTTOWIN_REDIS_MONITOR_PASSWORD=$monitor","FASTTOWIN_REDIS_BACKUP_PASSWORD=$backup",'FASTTOWIN_RATE_LIMIT_NAMESPACE=prod_main')
 $valid|Set-Content -Encoding ascii "$dir/valid.env"
 $crlfDir=Join-Path $dir 'crlf'; New-Item -ItemType Directory $crlfDir | Out-Null
 Copy-Item 'deploy/railway/redis/Dockerfile' $crlfDir
 foreach($name in @('boot.sh','backup.sh')) { $raw=[IO.File]::ReadAllText((Resolve-Path "deploy/railway/redis/$name")).Replace("`r`n","`n").Replace("`n","`r`n"); [IO.File]::WriteAllText((Join-Path $crlfDir $name),$raw,[Text.Encoding]::ASCII) }
 $crlfImage="ftw-prod-crlf:$id"; $crlfCid=$null
 try {
  D @('build','-q','--label',"fasttowin.owner=$label",'-t',$crlfImage,$crlfDir)|Out-Null
  $crlfCid=D @('run','-d','--label',"fasttowin.owner=$label",'--env-file',"$dir/valid.env",$crlfImage); Start-Sleep -Seconds 2
  Check ((D @('inspect','--format','{{.State.Running}}',$crlfCid)) -eq 'true') 'CRLF fixture builds and boots'
  $crlfTag=[guid]::NewGuid().ToString('N');$crlfOut=D @('exec',$crlfCid,'sh','/usr/local/bin/ftw-redis-backup.sh',$crlfTag)
  Check (($crlfOut -join '') -match '"backup":1') 'CRLF fixture backup executes'
 } finally {
  if($crlfCid){Remove-Owned $crlfCid}
  $crlfOwner=& docker image inspect --format '{{index .Config.Labels "fasttowin.owner"}}' $crlfImage 2>$null
  if($LASTEXITCODE -eq 0 -and $crlfOwner -eq $label){D @('image','rm',$crlfImage)|Out-Null}
 }
 D @('build','-q','--label',"fasttowin.owner=$label",'-t',$image,'deploy/railway/redis')|Out-Null
 $invalids=@(@($valid[1..3]),@($valid[0..2]+'FASTTOWIN_RATE_LIMIT_NAMESPACE=-bad'),@($valid[0..2]+'FASTTOWIN_RATE_LIMIT_NAMESPACE=UPPER'),@($valid[0..2]+('FASTTOWIN_RATE_LIMIT_NAMESPACE='+('a'*49))),@($valid[0..1]+'FASTTOWIN_REDIS_BACKUP_PASSWORD=xyz'+$valid[3]),@($valid[0..1]+"FASTTOWIN_REDIS_BACKUP_PASSWORD=$app"+$valid[3]),@($valid+"FASTTOWIN_REDIS_APP_PREVIOUS_PASSWORD=$backup"))
 foreach($entry in $invalids){$entry|Set-Content -Encoding ascii "$dir/invalid.env"; $bad=D @('run','-d','--label',"fasttowin.owner=$label",'--env-file',"$dir/invalid.env",$image); try {$exit=D @('wait',$bad); Check ($exit -ne '0') 'Invalid credential or namespace rejected'} finally {Remove-Owned $bad}}
 $volume=D @('volume','create','--label',"fasttowin.owner=$label",$label)
 $cid=D @('run','-d','--label',"fasttowin.owner=$label",'--env-file',"$dir/valid.env",'-e','FTW_REDIS_DEADLINE_EPOCH=1','-e','FTW_REDIS_RESTORE_TAG=ignored','-p','127.0.0.1::6379','--mount',"type=volume,src=$volume,dst=/data",$image)
 Start-Sleep -Seconds 2
 Check ((D @('inspect','--format','{{.State.Running}}',$cid)) -eq 'true') 'Boot ignores rehearsal deadline and restore'
 Check (((D @('exec',$cid,'sh','-c','test $(awk ''/^Uid:/{print $2}'' /proc/1/status) = $(id -u redis) && echo yes')) -join '').Trim() -eq 'yes') 'Redis runs unprivileged'
 function RedisCommand([string]$role,[string]$command){$var=switch($role){app{'FASTTOWIN_REDIS_APP_PASSWORD'} monitor{'FASTTOWIN_REDIS_MONITOR_PASSWORD'} backup{'FASTTOWIN_REDIS_BACKUP_PASSWORD'} wrong{'FASTTOWIN_REDIS_MONITOR_PASSWORD'}}; $name=if($role -eq 'wrong'){'ftw_app'}else{"ftw_$role"}; return (D @('exec',$cid,'sh','-c',"export REDISCLI_AUTH=`"`$$var`"; redis-cli --user $name --raw $command")) -join "`n"}
 foreach($role in @('app','monitor','backup')){Check ((RedisCommand $role 'PING') -eq 'PONG') "$role authentication"}
 Check ((RedisCommand wrong 'PING') -match 'AUTH failed|NOAUTH|WRONGPASS') 'Wrong password denied'
 Check (((D @('exec',$cid,'redis-cli','PING')) -join '') -match 'NOAUTH') 'Default user denied'
 foreach($command in @('FLUSHDB','FLUSHALL','CONFIG GET *','ACL USERS','SET ftw:prod_main:ops:backup-fence x','HSET ftw:other:rl:v1:x field x')){Check ((RedisCommand app $command) -match 'NOPERM') "App denied $command"}
 Check ((RedisCommand monitor 'GET ftw:prod_main:rl:v1:x') -match 'NOPERM') 'Monitor key access denied'
 Check ((RedisCommand backup 'SET ftw:prod_main:rl:v1:x x') -match 'NOPERM') 'Backup key scope enforced'
 $bundle=Join-Path $root '.artifacts/redis-cloud-acceptance-20261102/runner'; $revision=(Get-Content "$bundle/REVISION" -Raw).Trim(); $delta=& git diff --name-only $revision -- server/src/main protocol
 Check ($LASTEXITCODE -eq 0 -and !$delta) 'Frozen runtime source unchanged'
 $expected=@{}; foreach($line in (Get-Content "$bundle/SHA256SUMS")){if($line -notmatch '^([0-9a-f]{64})  (lib/[A-Za-z0-9_.+\-]+\.jar|REVISION)$' -or $expected.ContainsKey($Matches[2])){throw 'Invalid bundle manifest'};$expected[$Matches[2]]=$Matches[1]}
 $actual=@(Get-ChildItem $bundle -Recurse -File|ForEach-Object{[IO.Path]::GetRelativePath($bundle,$_.FullName).Replace('\','/')}|Where-Object{$_ -ne 'SHA256SUMS'})
 Check ($actual.Count -eq $expected.Count) 'Frozen bundle file count'
 foreach($name in $actual){if(!$expected.ContainsKey($name) -or (Get-FileHash -Algorithm SHA256 (Join-Path $bundle $name)).Hash.ToLowerInvariant() -ne $expected[$name]){throw 'Frozen bundle checksum mismatch'}}
 Write-Output 'PASS frozen bundle checksums'
 $port=(D @('port',$cid,'6379/tcp')) -replace '^127\.0\.0\.1:',''
 @"
import com.hienthai.fastowin.server.*;
import com.hienthai.fastowin.server.ratelimit.*;
import kotlin.coroutines.*;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import java.util.concurrent.*;
public class RedisProbe {
 static RateLimitResult consume(RedisRateLimiter r) throws Exception {
  CompletableFuture<Object> f = new CompletableFuture<>();
  Object v = r.consume("login-ip", "persistent-fixture", new RateLimitPolicy(1, 86400000L), new Continuation<RateLimitResult>() {
   public CoroutineContext getContext() { return EmptyCoroutineContext.INSTANCE; }
   public void resumeWith(Object result) { try { kotlin.ResultKt.throwOnFailure(result); f.complete(result); } catch (Throwable e) { f.completeExceptionally(e); } }
  });
  if (v == IntrinsicsKt.getCOROUTINE_SUSPENDED()) v = f.get(3, TimeUnit.SECONDS);
  return (RateLimitResult)v;
 }
 public static void main(String[] args) throws Exception {
  try (RedisRateLimiter r = new RedisRateLimiter(new RedisConfiguration(System.getenv("PROBE_URL"), "prod_main"))) {
   if (consume(r).getAllowed() != Boolean.parseBoolean(args[0])) throw new AssertionError("Unexpected quota result");
  }
  System.out.println("Runtime handshake and EVAL passed");
 }
}
"@|Set-Content -Encoding ascii "$dir/RedisProbe.java"
 $java='C:/Program Files/Android/Android Studio/jbr/bin';$classpath="$bundle/lib/*;$dir"
 & "$java/javac.exe" --release 17 -cp $classpath "$dir/RedisProbe.java";if($LASTEXITCODE){throw 'Probe compilation failed'}
 @("PROBE_URL=redis://ftw_app:${app}@127.0.0.1:6379/0") | Set-Content -Encoding ascii "$dir/probe.env"
 $jre='eclipse-temurin:17-jre-alpine@sha256:c4a33e6ddb738fe4be0a28a613ea0940cec42d25d7263c3e2238c43354cb19b0'
 foreach($expectedResult in @('true','false','false','false')) {
  $result=D @('run','--rm','--network',"container:$cid",'--label',"fasttowin.owner=$label",'--env-file',"$dir/probe.env",'--mount',"type=bind,src=$bundle,dst=/bundle,readonly",'--mount',"type=bind,src=$dir,dst=/probe,readonly",'--entrypoint','java',$jre,'-cp','/bundle/lib/*:/probe','RedisProbe',$expectedResult)
  Check (($result -join '') -match 'Runtime handshake and EVAL passed') 'Actual JVM handshake and EVAL'
  if($expectedResult -eq 'false'){D @('restart',$cid)|Out-Null;Start-Sleep -Seconds 2}
 }
 Write-Output 'PASS repeated restart preserves exhausted quota'
 $tag=[guid]::NewGuid().ToString('N');$out=D @('exec',$cid,'sh','/usr/local/bin/ftw-redis-backup.sh',$tag)
 foreach($secret in $secrets){if(($out -join '').Contains($secret)){throw 'Secret leaked'}}
 Check (($out -join '') -match '"backup":1') 'AOF backup created'
 $verified=D @('exec',$cid,'sh','-c',"mkdir /tmp/check; tar xzf /tmp/ftw-backup-$tag.tar.gz -C /tmp/check; cd /tmp/check; sha256sum -c SHA256SUMS")
 Check (@($verified|Where-Object{$_ -notmatch ': OK$'}).Count -eq 0) 'Archive hashes verified'
 "FASTTOWIN_REDIS_BACKUP_PASSWORD=$app" | Set-Content -Encoding ascii "$dir/wrong-backup.env"
 $badtag=[guid]::NewGuid().ToString('N'); $sw=[Diagnostics.Stopwatch]::StartNew()
 $badout=& docker exec --env-file "$dir/wrong-backup.env" $cid sh /usr/local/bin/ftw-redis-backup.sh $badtag 2>&1; $badexit=$LASTEXITCODE; $sw.Stop()
 Check ($badexit -ne 0 -and $sw.Elapsed.TotalSeconds -lt 12) 'Wrong backup credential fails within bounded time'
 D @('exec',$cid,'sh','-c','ln -s /etc/passwd /data/appendonlydir/unexpected')|Out-Null
 $badtag=[guid]::NewGuid().ToString('N'); $badout=& docker exec $cid sh /usr/local/bin/ftw-redis-backup.sh $badtag 2>&1;$badexit=$LASTEXITCODE
 Check ($badexit -ne 0) 'Backup rejects symlink'
 D @('exec',$cid,'rm','/data/appendonlydir/unexpected')|Out-Null
 Check ((RedisCommand app 'HSET ftw:prod_main:rl:v1:after-failure f x') -eq '1') 'Writes unpaused after failed backup'
 Remove-Owned $cid; $cid=$null
 @("FASTTOWIN_REDIS_APP_PASSWORD=$previous","FASTTOWIN_REDIS_APP_PREVIOUS_PASSWORD=$app","FASTTOWIN_REDIS_MONITOR_PASSWORD=$monitor","FASTTOWIN_REDIS_BACKUP_PASSWORD=$backup",'FASTTOWIN_RATE_LIMIT_NAMESPACE=prod_main') | Set-Content -Encoding ascii "$dir/rotate.env"
 $cid=D @('run','-d','--label',"fasttowin.owner=$label",'--env-file',"$dir/rotate.env",'--mount',"type=volume,src=$volume,dst=/data",$image); Start-Sleep -Seconds 2
 Check ((RedisCommand app 'PING') -eq 'PONG') 'New app credential accepted during overlap'
 $old=D @('exec',$cid,'sh','-c','export REDISCLI_AUTH="$FASTTOWIN_REDIS_APP_PREVIOUS_PASSWORD"; redis-cli --user ftw_app --raw PING')
 Check (($old -join '') -eq 'PONG') 'Previous app credential accepted during overlap'
 Remove-Owned $cid; $cid=$null
 @("FASTTOWIN_REDIS_APP_PASSWORD=$previous","FASTTOWIN_REDIS_MONITOR_PASSWORD=$monitor","FASTTOWIN_REDIS_BACKUP_PASSWORD=$backup",'FASTTOWIN_RATE_LIMIT_NAMESPACE=prod_main') | Set-Content -Encoding ascii "$dir/rotated.env"
 $cid=D @('run','-d','--label',"fasttowin.owner=$label",'--env-file',"$dir/rotated.env",'--mount',"type=volume,src=$volume,dst=/data",$image); Start-Sleep -Seconds 2
 $oldEnv=Join-Path $dir 'old.env'; "REDISCLI_AUTH=$app" | Set-Content -Encoding ascii $oldEnv
 # Supply old password by env-file, never as a command argument.
 $old=D @('run','--rm','--network',"container:$cid",'--label',"fasttowin.owner=$label",'--env-file',$oldEnv,'--entrypoint','redis-cli','redis@sha256:be7c1cada5a6eb1656adfa2635b99296af725881301b21f84f35a71fac072c7f','--user','ftw_app','--raw','PING')
 Check (($old -join '') -match 'AUTH failed|NOAUTH|WRONGPASS') 'Old credential revoked after overlap'
 Write-Output 'All production package checks passed.'
} finally {
 if($cid){Remove-Owned $cid}
 if($volume){if((D @('volume','inspect','--format','{{.Name}} {{index .Labels "fasttowin.owner"}}',$volume)) -ne "$volume $label"){throw 'Volume identity mismatch'};D @('volume','rm',$volume)|Out-Null}
 $owner=& docker image inspect --format '{{index .Config.Labels "fasttowin.owner"}}' $image 2>$null;if($LASTEXITCODE -eq 0 -and $owner -eq $label){D @('image','rm',$image)|Out-Null}
 if(Test-Path -LiteralPath $dir){Remove-Item -LiteralPath $dir -Recurse -Force}
}

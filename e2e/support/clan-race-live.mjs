// Opt-in actual admin browser -> Ktor/PostgreSQL -> Kotlin/Wasm weekly race acceptance.
// All clocks/settlement controls are test-source/stdin only; no credentials in evidence/traces.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createServer as httpServer} from 'node:http';
import {createServer as portProbe} from 'node:net';
import {createReadStream,existsSync} from 'node:fs';
import {readFile,writeFile,mkdir,unlink,stat} from 'node:fs/promises';
import {dirname,resolve,join,extname,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomUUID,createHash} from 'node:crypto';
import {chromium,expect,selectors} from 'playwright/test';
import {openLocalPostgres} from './local-postgres.mjs';
import {click,tag,login,fill} from './game.mjs';
import {finishModerationAcceptance} from './admin-moderation-cleanup.mjs';
const repo=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const artifact=join(repo,'.artifacts/clan-weekly-race/task-5-live');
const api='http://127.0.0.1:18088',web='http://127.0.0.1:18089';
process.env.E2E_API_URL=api;
selectors.setTestIdAttribute('id');
assert.ok(process.env.E2E_POSTGRES_SETTINGS,'E2E_POSTGRES_SETTINGS required; live proof never skips');
assert.ok(process.env.JAVA_HOME,'JAVA_HOME required');
const psql=process.env.E2E_PSQL||'psql';
await mkdir(artifact,{recursive:true});
assert.equal(existsSync(join(artifact,'owned-owner-password.txt')),false,'Existing acceptance password must not be overwritten');
for(const port of [18088,18089])await new Promise((yes,no)=>{const probe=portProbe();probe.once('error',()=>no(Error('Owned port occupied: '+port)));probe.listen(port,'127.0.0.1',()=>probe.close(yes));});
const database=await openLocalPostgres(process.env.E2E_POSTGRES_SETTINGS,psql);
const schema=new URL(database.serverEnvironment.DATABASE_URL.slice(5)).searchParams.get('currentSchema');assert.match(schema,/^webe2e_[a-f0-9]{32}$/);
const environment={...Object.fromEntries(['PATH','Path','SystemRoot','WINDIR','TEMP','TMP','JAVA_TOOL_OPTIONS'].filter(k=>process.env[k]).map(k=>[k,process.env[k]])),...database.serverEnvironment,FASTTOWIN_ENV:'dev',SERVER_HOST:'127.0.0.1',PORT:'18088',FASTTOWIN_ADMIN_ENABLED:'true',FASTTOWIN_ADMIN_ORIGIN:api,FASTTOWIN_CONTENT_TERMS_ENFORCED:'false'};
const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Bangkok',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const publishTime=date+'T05:00:00Z';
const monday=new Date(date+'T00:00:00Z');monday.setUTCDate(monday.getUTCDate()+7-(monday.getUTCDay()+6)%7);
const week=monday.toISOString().slice(0,10),start=Date.parse(week+'T00:00:00+07:00'),cutoff=start+604800000;
let economyTime=publishTime;
async function setClock(value){economyTime=typeof value==='number'?new Date(value).toISOString():value;await writeFile(clockFile,economyTime);}
const clockFile=join(artifact,'race-clock.txt'),passwordFile=join(artifact,'owned-owner-password.txt');
const ownerPassword=randomUUID()+'Aa9!',email='race-owner-'+randomUUID()+'@example.invalid';
const privateValues=new Set([ownerPassword]);
const sanitize=value=>{let text=String(value);for(const secret of privateValues)if(secret)text=text.replaceAll(secret,'[REDACTED]');return text;};
const evidence={status:'RUNNING',database:'owned loopback PostgreSQL/UUID schema',schema,app:'actual Kotlin/Wasm development executable',clock:{publish:publishTime,week,cutoff,control:'test-source file; authentication real current time'},traces:'off',provider:'none',stages:[],http:[],invariants:{}};
let backend,browser,context,appContext,frontend,failure;const sockets=[];let backendText='';
function run(executable,args,env=environment,input){return new Promise((yes,no)=>{const child=spawn(executable,args,{cwd:repo,env,windowsHide:true,stdio:['pipe','pipe','pipe']});let output='';child.stdout.on('data',b=>output+=b);child.stderr.resume();const timer=setTimeout(()=>{child.kill();no(Error('Owned subprocess timed out'));},30000);child.on('error',()=>{clearTimeout(timer);no(Error('Owned subprocess start failed'));});child.on('exit',code=>{clearTimeout(timer);code===0?yes(output.trim()):no(Error('Owned subprocess failed: '+code));});child.stdin.on('error',()=>{});child.stdin.end(input);});}
async function sql(query){return (await run(psql,['-X','-q','-t','-A','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p','15432','-d','admin_test','-U',environment.DATABASE_USER],{...environment,PGPASSWORD:environment.DATABASE_PASSWORD},`SET search_path TO ${schema};\nBEGIN; SELECT pg_advisory_xact_lock(7421092187355); SET LOCAL fasttowin.race_now = '${economyTime}';\n${query}\nCOMMIT;`)).trim();}
async function until(predicate,message){for(let i=0;i<200;i++){if(await predicate())return;await new Promise(r=>setTimeout(r,150));}throw Error(message);}
async function stop(){if(!backend?.pid||backend.exitCode!==null||backend.signalCode!==null)return;await new Promise((yes,no)=>{const timer=setTimeout(()=>no(Error('Backend did not stop; schema retained')),10000);backend.once('exit',()=>{clearTimeout(timer);yes();});backend.kill();});}
async function command(value){const marker='RACE_FIXTURE_'+value.operation.toUpperCase()+'_DONE';backendText='';backend.stdin.write(JSON.stringify(value)+'\n');await until(()=>backendText.includes(marker),'Test-source settlement command timed out');}
async function playerRequest(session,path,body){const response=await fetch(api+path,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+session.accessToken,'Cache-Control':'no-store',...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});const value=await response.json();evidence.http.push({route:path,method:body?'POST':'GET',status:response.status,cache:response.headers.get('cache-control'),body:body||undefined,response:value});return {response,value};}
async function register(label){const account={email:'race-'+randomUUID()+'@example.invalid',password:randomUUID()+'Aa9!',displayName:'LOCAL '+label,devicePlatform:'web-e2e',gender:'MALE'};const response=await fetch(api+'/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(account)});assert.equal(response.status,201);const session=await response.json();for(const secret of [account.password,session.accessToken,session.refreshToken])privateValues.add(secret);const request=await fetch(api+'/auth/email-verification/request',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({accessToken:session.accessToken})});assert.equal(request.status,200);const code=(await request.json()).devEmailVerificationCode;assert.match(code,/^\d{6}$/);const verified=await fetch(api+'/auth/email-verification/confirm',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({accessToken:session.accessToken,verificationCode:code})});assert.equal(verified.status,200);return {account,session};}
async function serve(){const root=join(repo,'webApp/build/kotlin-webpack/wasmJs/developmentExecutable'),resources=join(repo,'webApp/build/processedResources/wasmJs/main');assert.ok(existsSync(join(root,'fast-to-win.js')));frontend=httpServer(async(req,res)=>{try{const path=decodeURIComponent(new URL(req.url,web).pathname);res.setHeader('Cache-Control','no-store');if(path==='/config.js'){res.setHeader('Content-Type','text/javascript');return res.end(`globalThis.FASTTOWIN_CONFIG=${JSON.stringify({serverUrl:api.replace('http:','ws:')+'/game'})};`);}let file;for(const base of [root,resources]){const candidate=resolve(base,'.'+path);if(candidate!==base&&!candidate.startsWith(base+sep)){res.writeHead(403);return res.end();}if((await stat(candidate).catch(()=>null))?.isFile()){file=candidate;break;}}if(!file&&!extname(path))file=join(resources,'index.html');if(!file){res.writeHead(404);return res.end();}res.setHeader('Content-Type',({'.html':'text/html','.js':'text/javascript','.mjs':'text/javascript','.wasm':'application/wasm','.css':'text/css','.json':'application/json','.png':'image/png','.webp':'image/webp','.svg':'image/svg+xml','.woff2':'font/woff2'})[extname(file)]||'application/octet-stream');createReadStream(file).on('error',()=>res.destroy()).pipe(res);}catch{res.writeHead(400);res.end();}});await new Promise(yes=>frontend.listen(18089,'127.0.0.1',yes));}


async function startBackend(){
 await writeFile(passwordFile,ownerPassword);
 const classpath=(await readFile(join(repo,'server/build/clan-race-acceptance-classpath.txt'),'utf8')).trim();
 await new Promise((yes,no)=>{
  backend=spawn(join(process.env.JAVA_HOME,'bin/java.exe'),['-cp',classpath,'com.hienthai.fastowin.server.clanrace.ClanRaceAcceptanceLauncherKt'],{cwd:repo,env:{...environment,RACE_FIXTURE_CLOCK:clockFile,RACE_FIXTURE_OWNER_EMAIL:email,RACE_FIXTURE_OWNER_PASSWORD_FILE:passwordFile},windowsHide:true,stdio:['pipe','pipe','pipe']});
  backend.once('error',error=>no(Error('Owned race backend failed to start: '+error.code)));backend.once('spawn',yes);
  // Do not persist unfiltered server logs: stdout is only consumed for fixed stdin acknowledgements.
  backend.stdout.on('data',b=>backendText+=b);backend.stderr.resume();
 });
 await until(async()=>{if(backend.exitCode!==null)throw Error('Owned backend exited');return (await fetch(api+'/health').catch(()=>null))?.ok;},'Backend readiness timeout');
 await unlink(passwordFile);
}
try{
 await setClock(publishTime);await startBackend();
 await serve();browser=await chromium.launch();
 context=await browser.newContext({viewport:{width:1440,height:900},reducedMotion:'reduce',serviceWorkers:'block'});
 const admin=await context.newPage();await admin.clock.setFixedTime(new Date(publishTime));
 for(const name of ['admin.js','admin.css','admin-clan-race.js']){
  const response=await context.request.get(api+'/admin/'+name);assert.equal(response.status(),200);
  const hash=b=>createHash('sha256').update(b).digest('hex');assert.equal(hash(await response.body()),hash(await readFile(join(repo,'server/src/main/resources/admin',name))));
 }
 await admin.goto(api+'/admin/');await admin.getByLabel('Email quản trị',{exact:true}).fill(email);await admin.getByLabel('Mật khẩu',{exact:true}).fill(ownerPassword);await admin.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 await admin.getByRole('button',{name:'Đua top bang',exact:true}).click();await admin.getByRole('button',{name:'Tạo tuần đua',exact:true}).click();
 for(const [label,value] of [['Tuần bắt đầu (thứ Hai)',week],['Điểm / 1 Vàng','0.000001'],['Điểm / 1 Gem','1'],['Lý do thay đổi','Owned local weekly race acceptance']])await admin.getByLabel(label,{exact:true}).fill(value);
 const rewards=[{xp:30,gold:300,gems:3},{xp:20,gold:200,gems:2},{xp:10,gold:100,gems:1}];
 for(let rank=1;rank<=3;rank++)for(const [key,label] of [['xp','XP'],['gold','Vàng'],['gems','Gem']])await admin.getByLabel(`Top ${rank} · ${label}`,{exact:true}).fill(String(rewards[rank-1][key]));
 const create=admin.waitForResponse(r=>r.url()===api+'/admin/api/clan-race'&&r.request().method()==='POST');await admin.getByRole('button',{name:'Lưu bản nháp',exact:true}).click();const draft=await create;assert.equal(draft.status(),200);evidence.http.push({route:'/admin/api/clan-race',response:await draft.json()});
 const preview=admin.waitForResponse(r=>r.url().endsWith('/'+week+'/preview'));await admin.getByRole('button',{name:'Xem trước',exact:true}).click();const projected=await (await preview).json();assert.deepEqual(projected.maximumBudget,{xp:'3000',gold:'30000',gems:'300'});assert.equal(projected.gold100Points,'0.0001');evidence.http.push({route:'preview',response:projected});
 for(const width of [375,1440]){await admin.setViewportSize({width,height:900});await admin.getByRole('button',{name:'Tiếp tục phát hành',exact:true}).scrollIntoViewIfNeeded();await admin.screenshot({animations:'disabled',path:join(artifact,'admin-preview-'+width+'.png')});}
 await admin.getByRole('button',{name:'Tiếp tục phát hành',exact:true}).click();await admin.getByLabel('Lý do thay đổi',{exact:true}).fill('Owned local publication');const publish=admin.waitForResponse(r=>r.url().endsWith('/'+week+'/publish'));await admin.getByRole('button',{name:'Phát hành tuần đua',exact:true}).click();const published=await (await publish).json();assert.equal(published.state,'PUBLISHED');assert.deepEqual(published.config.rewards,rewards);evidence.http.push({route:'publish',response:published});
 // One mission is configured through the real admin UI; no score/progress rows are fabricated.
 await sql(`UPDATE mission_definitions SET archived_from='${week}';`);
 await admin.getByRole('button',{name:'Nhiệm vụ',exact:true}).click();await admin.getByRole('button',{name:'Bang hội',exact:true}).click();await admin.getByRole('button',{name:'Tạo nhiệm vụ',exact:true}).click();
 for(const [label,value] of [['Mã nhiệm vụ','LIVE_RACE_DONATION'],['Mục tiêu','100'],['XP','3'],['Vàng','0'],['Gem','2'],['Điểm cống hiến','7'],['Lý do thay đổi','Owned race contributor mission']])await admin.getByLabel(label,{exact:true}).fill(value);
 await admin.getByLabel('Nhóm nhiệm vụ',{exact:true}).selectOption('CLAN');await admin.getByLabel('Loại hành động',{exact:true}).selectOption('DONATE_GOLD');await admin.getByLabel('Độ khó',{exact:true}).selectOption('NORMAL');await admin.getByRole('button',{name:'Lưu bản nháp',exact:true}).click();await admin.getByRole('button',{name:'Xem trước',exact:true}).click();await admin.getByRole('button',{name:'Tiếp tục phát hành',exact:true}).click();await admin.getByLabel('Lý do thay đổi',{exact:true}).fill('Owned race mission publish');await admin.getByRole('button',{name:'Phát hành nhiệm vụ',exact:true}).click();await expect(admin.locator('main')).toContainText('Revision đã phát hành');
 evidence.stages.push('actual-admin-create-preview-publish-config-and-mission');
 const labels=['Player','Before','After','OldTenure','NewTenure','NonContributor','TieA','TieB','Zero'];const actors=[];for(const label of labels)actors.push(await register(label));
 const [player,before,after,oldTenure,newTenure,nonContributor,tieA,tieB,zero]=actors;
 const uid=a=>a.session.userId;const clan=randomUUID(),clanA=randomUUID(),clanB=randomUUID(),clanZero=randomUUID();
 await setClock(start-1000);
 for(const [id,owner,name] of [[clan,player,'LOCAL Race Alpha'],[clanA,tieA,'LOCAL Race Tie A'],[clanB,tieB,'LOCAL Race Tie B'],[clanZero,zero,'LOCAL Race Zero']])await sql(`INSERT INTO clans(id,name,owner_id,created_at) VALUES('${id}','${name}','${uid(owner)}',CURRENT_TIMESTAMP); INSERT INTO clan_members(clan_id,user_id,role,joined_at) VALUES('${id}','${uid(owner)}','LEADER',CURRENT_TIMESTAMP);`);
 for(const actor of [before,after,oldTenure,newTenure,nonContributor])await sql(`INSERT INTO clan_members(clan_id,user_id,role,joined_at) VALUES('${clan}','${uid(actor)}','MEMBER',CURRENT_TIMESTAMP);`);
 for(const actor of actors)await sql(`INSERT INTO player_stats(user_id,gold,gems,experience_points,updated_at) VALUES('${uid(actor)}',2000,5,0,CURRENT_TIMESTAMP) ON CONFLICT(user_id) DO UPDATE SET gold=2000,gems=5,experience_points=0;`);
 const donate=async(actor,id=clan)=>command({operation:'donation',player:uid(actor),clan:id,request:randomUUID()});
 assert.equal((await playerRequest(player.session,'/api/clan-race')).value.state,'INACTIVE');await donate(zero,clanZero);assert.equal(await sql('SELECT count(*) FROM clan_race_events;'),'0');
 await setClock(start);assert.equal((await playerRequest(player.session,'/api/clan-race')).value.state,'ACTIVE');
 await playerRequest(player.session,'/api/missions/clan');await donate(player);
 const mission=(await playerRequest(player.session,'/api/missions/clan')).value.items.find(row=>row.code==='LIVE_RACE_DONATION');assert.ok(mission);assert.equal(mission.personalContribution,100);
 const claimPath='/api/missions/'+mission.instanceId+'/claim';
 const contributor=await playerRequest(player.session,claimPath,{requestId:'contributor'});assert.equal(contributor.response.status,200);
 const nonClaim=await playerRequest(nonContributor.session,claimPath,{requestId:'non-contributor'});assert.equal(nonClaim.response.status,200);
 await playerRequest(player.session,claimPath,{requestId:'contributor-replay'});
 assert.equal(await sql(`SELECT count(*) FROM clan_race_events WHERE source_kind='MISSION' AND user_id='${uid(player)}';`),'1');assert.equal(await sql(`SELECT count(*) FROM clan_race_events WHERE user_id='${uid(nonContributor)}';`),'0');
 await setClock(start+1000);for(const actor of [before,after,oldTenure,newTenure])await donate(actor);
 await donate(tieA,clanA);await donate(tieB,clanB);
 const standings=(await playerRequest(player.session,'/api/clan-race/standings')).value;
 assert.equal(standings.total,3);assert.equal(standings.ownClan.rank,1);assert.equal(standings.items[0].points,'7.0005');assert.deepEqual(standings.items.slice(1).map(r=>r.clanId),[clanA,clanB].sort());assert.ok(standings.items.slice(1).every(r=>r.points==='0.0001'));
 for(const actor of [oldTenure,newTenure]){await command({operation:'kick',clan,owner:uid(player),player:uid(actor)});await command({operation:'join',player:uid(actor),clan,owner:uid(player)});}
 await setClock(start+2000);await donate(newTenure);
 let contributions=(await playerRequest(player.session,'/api/clan-race/contributions')).value.items;
 assert.equal(contributions.find(r=>r.userId===uid(oldTenure)&&r.currentMember).eligible,false);assert.equal(contributions.find(r=>r.userId===uid(newTenure)&&r.currentMember).eligible,true);
 assert.equal(await sql("SELECT count(*) FROM clan_race_events WHERE source_kind='DONATION';"),'8');
 evidence.invariants.sources={donationEvents:Number(await sql("SELECT count(*) FROM clan_race_events WHERE source_kind='DONATION';")),missionEvents:1,nonContributorEvents:0,rankPoints:'7.0006',tiePoints:'0.0001'};
 evidence.stages.push('real-source-donation-replay-mission-claim-contributor-and-new-tenure');
 appContext=await browser.newContext({baseURL:web,viewport:{width:375,height:900},reducedMotion:'reduce',locale:'vi-VN',serviceWorkers:'block'});const app=await appContext.newPage();let activeSession=player.session;
 const failedRequests=[];evidence.failedRequests=failedRequests;app.on('requestfailed',request=>failedRequests.push({route:new URL(request.url()).pathname,method:request.method(),failure:request.failure()?.errorText,stage:evidence.stages.at(-1)}));
 const appErrors=[];evidence.browserErrors=appErrors;app.on('pageerror',e=>appErrors.push({message:e.message,stack:e.stack,stage:evidence.stages.at(-1),url:new URL(app.url()).pathname}));const appResponses=[];evidence.appHttp=appResponses;
 app.on('request',request=>{if(new URL(request.url()).pathname.startsWith('/api/')){const bearer=request.headers()['authorization'];if(bearer?.startsWith('Bearer ')){activeSession.accessToken=bearer.slice(7);privateValues.add(activeSession.accessToken);}}});
 app.on('response',async response=>{const route=new URL(response.url()).pathname;if(['/api/clan-race','/api/gifts/mailbox'].some(prefix=>route.startsWith(prefix)))appResponses.push({route,status:response.status(),cache:response.headers()['cache-control'],response:await response.json().catch(()=>null)});});
 await login({page:app,account:player.account});await app.goto(web+'/clan');await click(app,tag(app,'clan_weekly_race_entry'));await expect(tag(app,'race_status')).toContainText('Đang');await expect(tag(app,'clan_race')).toContainText('Quà tuần này');
 await expect.poll(()=>appResponses.some(r=>r.response?.config?.goldRate==='0.000001')).toBe(true);
 async function scrollTo(locator){for(let i=0;i<35;i++){if(await locator.count()){await locator.scrollIntoViewIfNeeded();return;}await app.mouse.move(app.viewportSize().width/2,700);await app.mouse.wheel(0,550);await app.waitForTimeout(80);}throw Error('Actual Compose item did not become visible');}
 for(const width of [375,1440]){await app.setViewportSize({width,height:900});await app.reload();await expect(tag(app,'race_status')).toContainText('Đang');await app.screenshot({animations:'disabled',path:join(artifact,'app-prizes-'+width+'.png')});await scrollTo(tag(app,'race_rank_'+clan));await expect(tag(app,'race_rank_'+clan)).toContainText('7.0006');await app.screenshot({animations:'disabled',path:join(artifact,'app-standings-'+width+'.png')});}
 const newMember=contributions.find(r=>r.userId===uid(newTenure)&&r.currentMember);await scrollTo(tag(app,'race_eligibility_'+newMember.tenureId));await expect(tag(app,'race_eligibility_'+newMember.tenureId)).toContainText('Đủ');await app.screenshot({animations:'disabled',path:join(artifact,'app-current-tenure-1440.png')});
 await app.reload();await expect(tag(app,'race_status')).toContainText('Đang');
 await sql('ALTER TABLE clan_race_configs RENAME TO clan_race_configs_acceptance_unavailable;');
 try{const unavailable=await playerRequest(player.session,'/api/clan-race');assert.equal(unavailable.response.status,503);await click(app,tag(app,'race_refresh'));await expect(tag(app,'race_error')).toBeAttached();await app.screenshot({animations:'disabled',path:join(artifact,'app-storage-503.png')});}finally{await sql('ALTER TABLE clan_race_configs_acceptance_unavailable RENAME TO clan_race_configs;');}
 await click(app,tag(app,'race_refresh'));await expect(tag(app,'race_error')).toHaveCount(0);evidence.stages.push('actual-wasm-visible-prizes-rank-eligibility-storage503-retry');
 // At the exact boundary, source time belongs to the new, unconfigured week.
 await setClock(cutoff-1);await command({operation:'kick',clan,owner:uid(player),player:uid(before)});
 const eventsBefore=await sql('SELECT count(*) FROM clan_race_events;');await setClock(cutoff);await donate(player);assert.equal(await sql('SELECT count(*) FROM clan_race_events;'),eventsBefore);
 await setClock(cutoff+86400000);await command({operation:'kick',clan,owner:uid(player),player:uid(after)});
 const pending=(await playerRequest(player.session,'/api/clan-race/history')).value.items.find(r=>r.weekStart===week);assert.equal(pending.state,'CLOSED');
 await command({operation:'settle'});const frozen=(await playerRequest(player.session,'/api/clan-race/history')).value.items.find(r=>r.weekStart===week);assert.equal(frozen.state,'SETTLED');assert.equal(frozen.settlement.result.winners.length,3);assert.equal(frozen.settlement.result.winners[0].eligibleMembers,3);
 const recipients=(await sql(`SELECT user_id FROM clan_race_entitlements WHERE clan_id='${clan}' ORDER BY user_id;`)).split(/\r?\n/);assert.deepEqual(recipients,[uid(player),uid(after),uid(newTenure)].sort());
 evidence.stages.push('owned-backend-restart-start');await stop();await startBackend();evidence.stages.push('owned-backend-restart-ready');await command({operation:'settle'});await command({operation:'deliver'});await command({operation:'deliver'});
 assert.equal(await sql('SELECT count(*) FROM clan_race_entitlements;'),'5');assert.equal(await sql("SELECT count(*) FROM clan_race_outbox WHERE state='DELIVERED';"),'5');
 const historyAgain=(await playerRequest(player.session,'/api/clan-race/history')).value.items.find(r=>r.weekStart===week);assert.deepEqual(historyAgain.settlement.result,frozen.settlement.result);
 const mailbox=(await playerRequest(player.session,'/api/gifts/mailbox')).value;const gift=mailbox.items.find(g=>g.clanRace?.weekStart===week);assert.ok(gift);assert.equal(gift.clanRace.sourceType,'CLAN_WEEKLY_RACE');assert.equal(gift.clanRace.expiresAtEpochMillis-gift.clanRace.availableAtEpochMillis,604800000);
 evidence.invariants.settlement={entitlements:5,delivered:5,alphaRecipients:recipients,cutoff,available:gift.clanRace.availableAtEpochMillis,expires:gift.clanRace.expiresAtEpochMillis,frozen:historyAgain.settlement.result};
 await app.goto(web+'/');await click(app,tag(app,'home_gift_mailbox'));await expect(tag(app,'gift_'+gift.campaignId)).toContainText('LOCAL Race Alpha');await expect(tag(app,'gift_race_deadline_'+gift.campaignId)).toBeAttached();
 for(const width of [375,1440]){await app.setViewportSize({width,height:900});await app.screenshot({animations:'disabled',path:join(artifact,'app-mailbox-'+width+'.png')});}
 let release,observed;const arrived=new Promise(r=>observed=r);const giftPath='/api/gifts/mailbox/'+gift.campaignId+'/claim';
 await app.route('**'+giftPath,async route=>{observed();await new Promise(r=>release=r);await route.continue();});
 const wallet=async()=> (await sql(`SELECT gold||':'||gems||':'||experience_points FROM player_stats WHERE user_id='${uid(player)}';`)).split(':').map(Number);
 const walletBefore=await wallet();const appClaim=app.waitForResponse(r=>r.url().endsWith(giftPath));await click(app,tag(app,'gift_claim_'+gift.campaignId));await arrived;const concurrent=playerRequest(player.session,giftPath,{});release();const [receipt,direct]=await Promise.all([appClaim,concurrent]);assert.equal(receipt.status(),200);assert.equal(direct.response.status,200);assert.equal((await receipt.json()).gift.state,'CLAIMED');await app.unroute('**'+giftPath);
 await expect(tag(app,'gift_'+gift.campaignId)).toContainText('Đã nhận');await app.reload();await expect(tag(app,'gift_'+gift.campaignId)).toContainText('Đã nhận');const walletAfter=await wallet();assert.deepEqual(walletAfter.map((v,i)=>v-walletBefore[i]),[300,3,30]);assert.equal(await sql(`SELECT count(*) FROM wallet_transactions WHERE user_id='${uid(player)}' AND source_type='CLAN_WEEKLY_RACE';`),'1');
 evidence.invariants.appClaim={before:walletBefore,after:walletAfter,delta:[300,3,30],raceLedgers:1};evidence.stages.push('cutoff-late-worker-restart-concurrent-settlement-delivery-real-app-concurrent-claim-reload');
 // Account identity is exercised through the visible logout/login flow, then a genuine reload.
 await app.goto(web+'/account');await click(app,tag(app,'profile_logout'));await click(app,tag(app,'confirm_logout'));await expect(tag(app,'auth_email')).toBeAttached();activeSession=tieA.session;await fill(app,tag(app,'auth_email'),tieA.account.email);await fill(app,tag(app,'auth_password'),tieA.account.password);await app.mouse.click(4,4);await app.waitForTimeout(250);const switched=app.waitForResponse(r=>r.url()===api+'/auth/login'&&r.request().method()==='POST');await click(app,tag(app,'auth_login_submit'));assert.equal((await (await switched).json()).userId,uid(tieA));
 await app.goto(web+'/clan/race');await expect(tag(app,'race_status')).toContainText('Chưa');await app.reload();await expect(tag(app,'race_status')).toContainText('Chưa');
 assert.equal((await playerRequest(tieA.session,'/api/clan-race/contributions')).value.clanId,clanA);assert.equal(await tag(app,'gift_'+gift.campaignId).count(),0);
 await scrollTo(app.getByText('LOCAL Race Alpha',{exact:true}));await expect(app.getByText('LOCAL Race Alpha',{exact:true})).toBeAttached();await app.screenshot({animations:'disabled',path:join(artifact,'app-settled-history-1440.png')});await app.reload();await expect(tag(app,'race_status')).toContainText('Chưa');
 evidence.stages.push('actual-app-account-clan-switch-reload-no-config-week');
 // Delay one genuine old-clan request at its network boundary; never fulfill a response.
 let releaseFence,arriveFence,heldFence=false;const fencedArrival=new Promise(r=>arriveFence=r);
 await app.route('**/api/clan-race/contributions',async route=>{if(heldFence)return route.continue();heldFence=true;arriveFence();await new Promise(r=>releaseFence=r);await route.continue().catch(()=>{});});
 await click(app,tag(app,'race_refresh'));await fencedArrival;
 // Same identity changes clan through real repository leave/request/approve, then reloads actual app.
 await command({operation:'leave',player:uid(tieA)});await command({operation:'join',player:uid(tieA),clan:clanB,owner:uid(tieB)});
 const oldReads=appResponses.length;await app.reload();await expect(tag(app,'race_status')).toContainText('Chưa');
 await expect.poll(()=>appResponses.slice(oldReads).some(r=>r.route==='/api/clan-race/contributions'&&r.response?.clanId===clanB)).toBe(true);
 releaseFence();await app.unroute('**/api/clan-race/contributions');await app.waitForTimeout(250);assert.equal(appResponses.filter(r=>r.route==='/api/clan-race/contributions'&&r.status===200).at(-1).response.clanId,clanB);
 const frozenAfterDissolution=(await playerRequest(tieA.session,'/api/clan-race/history')).value.items.find(r=>r.weekStart===week).settlement.result;
 assert.deepEqual(frozenAfterDissolution,frozen.settlement.result);
 evidence.invariants.clanSwitch={previousClan:clanA,currentClan:clanB,actualAppReloadConfirmed:true,oldRequestHeldAcrossReload:true,frozenHistoryRetained:true};
 evidence.stages.push('real-clan-leave-join-app-reload-frozen-history-after-dissolution');

 // Mix one unclaimed race prize with ordinary and compensation fixture campaigns.
 await command({operation:'mixed-mailbox-fixture',player:uid(tieA)});
 const mixed=(await playerRequest(tieA.session,'/api/gifts/mailbox')).value.items;
 assert.equal(mixed.length,3);assert.equal(mixed.filter(g=>g.clanRace).length,1);assert.equal(mixed.filter(g=>g.compensation).length,1);
 const mixedRace=mixed.find(g=>g.clanRace);const expectedReward=mixedRace.clanRace.finalRank===2?[228,2,20]:[128,1,10];
 const mixedWallet=async()=> (await sql(`SELECT gold||':'||gems||':'||experience_points FROM player_stats WHERE user_id='${uid(tieA)}';`)).split(':').map(Number);
 const mixedBefore=await mixedWallet();await app.goto(web+'/');await click(app,tag(app,'home_gift_mailbox'));await expect(tag(app,'gift_claim_all')).toBeAttached();
 const all=app.waitForResponse(r=>r.url().endsWith('/api/gifts/mailbox/claim-all'));await click(app,tag(app,'gift_claim_all'));const allReceipt=await all;assert.equal(allReceipt.status(),200);assert.equal((await allReceipt.json()).items.filter(i=>i.result?.gift?.state==='CLAIMED').length,3);
 const mixedAfter=await mixedWallet();assert.deepEqual(mixedAfter.map((v,i)=>v-mixedBefore[i]),expectedReward);
 assert.equal(await sql(`SELECT string_agg(source_type||':'||n,',' ORDER BY source_type) FROM (SELECT source_type,count(*) n FROM wallet_transactions WHERE user_id='${uid(tieA)}' AND source_type IN ('ADMIN_GIFT','COMPENSATION','CLAN_WEEKLY_RACE') GROUP BY source_type) s;`),'ADMIN_GIFT:1,CLAN_WEEKLY_RACE:1,COMPENSATION:1');
 const retryAll=await playerRequest(tieA.session,'/api/gifts/mailbox/claim-all',{campaignIds:mixed.map(g=>g.campaignId)});assert.equal(retryAll.response.status,200);assert.deepEqual(await mixedWallet(),mixedAfter);
 evidence.invariants.mixedClaimAll={before:mixedBefore,after:mixedAfter,delta:expectedReward,sourceLedgerCounts:{ADMIN_GIFT:1,CLAN_WEEKLY_RACE:1,COMPENSATION:1},arrangement:'two campaigns inserted with production GiftTransaction in test source; claim-all is actual app HTTP'};
 evidence.stages.push('actual-app-claim-all-mixed-sources-replay');

 assert.deepEqual(appErrors,[]);assert.ok(appResponses.length>10);assert.ok(appResponses.every(r=>r.cache==='no-store'));evidence.invariants.actualAppRequests=appResponses.length;

 evidence.status='PASS';
}catch(error){failure=new Error(sanitize(error.stack||error.message));console.error(failure.message);}finally{
 await finishModerationAcceptance({evidence,failure,closeSockets:async()=>{sockets.forEach(s=>s.close());},closeContext:async()=>{await appContext?.close();await context?.close();if(frontend){frontend.closeAllConnections();await new Promise(yes=>frontend.close(yes));}},closeBrowser:async()=>browser?.close(),removePassword:()=>unlink(passwordFile),stopBackend:stop,dropSchema:()=>database.close(),writeEvidence:value=>writeFile(join(artifact,'acceptance.json'),sanitize(JSON.stringify(value,null,2)))});
}
console.log(JSON.stringify({status:evidence.status,stages:evidence.stages,cleanup:evidence.cleanup,invariants:evidence.invariants}));

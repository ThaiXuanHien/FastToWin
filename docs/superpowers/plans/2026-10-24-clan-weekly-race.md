# Clan Weekly Race Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bang đua top3mỗi tuần bằng điểm quyên góp và claim nhiệm vụ; admin cấu hình tỷ lệ/thưởng, app hiển thị bảng đua/Quà tuần này và hộp thư phát đúng một lần cho người đủ quyền.

**Architecture:** Module clanrace riêng lưu cấu hình tuần bất biến, sự kiện điểm, lịch sử từng lần gia nhập và snapshot chốt. Hook nguồn thật trong cùng JDBCtransaction; worker chốt theo cutoff lịch sử, tạo entitlement/hộp thư, claim dùng ledger chung. UI chỉ trình bày dữ liệu server.

**Tech Stack:** Kotlin/JVM/KMP, Ktor, PostgreSQL/Flyway, Compose, vanilla admin DOM/CSS, JUnit/Node/Playwright.

**Spec:** `docs/superpowers/specs/2026-10-24-clan-weekly-race-design.md` (approved in chat24Oct2026).

## Global Constraints

- Top3 theo tổng cống hiến toàn bang, gồm nhiệm vụ và quyên góp; không lấy riêng20thành viên.
- Admin đặt tỷ lệ Vàng/Gem→điểm theo tuần; áp dụng tuần tiếp theo. Chưa có cấu hình phát hành thì chưa mở đua.
- Điểm từ nhiệm vụ cộng khi người có đóng góp bấm nhận quà, không tự cộng khi nhiệm vụ hoàn thành. Người không góp tiến độ không có điểm đua từ việc nhận quà nhiệm vụ chung.
- Rời/kick rồi vào lại cùng bang: phải có đóng góp mới sau lần gia nhập mới; điểm cũ không tự phục hồi quyền nhận.
- Tuần bắt đầu00:00thứHai, kết thúc00:00thứHai kế tiếp,Asia/Bangkok; khoảng đầu bao gồm/cuối không bao gồm, thời gian server.
- Khi bằng điểm: bang đạt tổng điểm cuối đó sớm hơn đứng trước; nếu vẫn bằng dùng IDbang ổn định làm khóa cuối. Tối đa3hạng, bang0điểm không chiếm hạng thưởng.
- Thưởng cho mỗi thành viên đủ điều kiện,7ngày từ lúc quà thực sự sẵn sàng. Kick/rời sau cutoff không thu hồi quyền đã chốt; dùng lịch sử cutoff chứ không dùng roster lúc worker chạy muộn.
- Điểm chính xác tối đa6chữ số thập phân; lưu số nguyên đơn vị phần triệu, DTO chuỗi số, khôngfloat/JSunsafeNumber. Clamp/overflow âm thầm và grant một phần không được phép.
- Claim/quyên góp/điểm/roster/settlement cần thứ tự khóa chung, event/source/entitlement/ledger chống lặp; không sửaV1–V66 hay nhiệm vụ đang chạy.
- App Arcade, admin Material sáng; mandatoryui-ux-pro-max, TextKey đủ12ngôn ngữ; phone/desktop/landscape/largefont/focus/reducedmotion evidence.
- Giữ .idea/.scratch của chủ dự án, giữ manualpreview18080/18081 và schema của người dùng đang test. Không dừng cluster15432nếu manualpreview đang dùng. Không deploy/provider/production/emulator/push/merge tự động.

## Task 1: Config, precise scoring, tenure history and read APIs

**Files:**
- Create `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/ClanRaceProtocol.kt`.
- Create focused `server/src/main/kotlin/com/hienthai/fastowin/server/clanrace/ClanRaceRules.kt`, `ClanRaceService.kt`, `PostgresClanRaceRepository.kt`, `ClanRaceRoutes.kt`, `ClanRaceScoring.kt`, `ClanRaceTenure.kt` as responsibility requires; JDBCstores may be separated into additional focused files within this package rather than one giant file.
- Add new Flywaymigration after highest appliedhead, leavingV1–V66 unchanged.
- Modify narrow sourcehooks in `PostgresClanRepository.kt`, `missions/PostgresMissionRepository.kt`, `Database.kt`, `Main.kt`, `Application.kt` and deletion/roster callers only where atomic invariants require it.
- Add `server/src/test/kotlin/com/hienthai/fastowin/server/clanrace/ClanRaceRulesTest.kt`, `ClanRaceScoringPostgresTest.kt`, `ClanRaceRoutesPostgresTest.kt`, `ClanRaceTenurePostgresTest.kt`, and protocolserialization tests.

**Interfaces:**
- Produces class/module names `ClanRaceService`, `PostgresClanRaceRepository` and a connection-scoped `JdbcClanRaceStore` for callers already in a transaction; do not open a second connection from sourcehooks. Test clocks inject server time; no production clockendpoint.
- Produces DTOs under separate ClanRaceProtocol; actual final field/signature/route contracts must be recorded in task-1-report.md before later tasks. Read-only player roots `/api/clan-race`, `/api/clan-race/standings`, `/api/clan-race/contributions`, `/api/clan-race/history`; adminroot `/admin/api/clan-race` with list/detail/draft/preview/publish/archivefuture. Bodybounded, no-store, auth/RBAC/CSRF/version/reason/requestID/audit follow missions/admin conventions.
- Config key is weekstartISOdateMonday; stateDRAFT/PUBLISHED/INACTIVE lifecycle. Rates stored as canonical decimal strings, XP/Gold/Gem top1..3permember validated as spec. Publishing no earlier than nextMonday; topcomponentwise descending and everyrank positive. Frozenrevision perweek.
- Durable events carry week/sourceKind/sourceID/user/clan/tenure/servereventtime/scoreUnits; scoreUnits represented exact integer capped at38decimaldigits with checked arithmetic. Identicalsource replay neutral; smaller splits sum identically. Positivefractional donation qualifies and mustdisplay nonzero.
- Tenurehistory retains joins/exits/deletionintervals and clanidentity snapshots needed for later as-of-cutoff. Task2 consumes store/read queries and lock order. No racepointsbackfill from cutover markers or oldmissionclaims.

- [ ] Write a reusable realPostgreSQL child-schema fixture with frozenclock and validadmin/player setup; tests must assert actual database rows/wallet/sourcefacts, no source-text checks.
- [ ] Write/run failing exact-unit tests, then arithmetic implementation. Observable examples use productionpublicrulefunctions, defined here as `parseRaceRateUnits(text:String):BigInteger`, `raceDonationUnits(amount:Long,rateUnits:BigInteger):BigInteger`, `raceWeekStart(nowMillis:Long):LocalDate`:
```kotlin
@Test fun exactRatesAndSplitDonations() {
    val rate = parseRaceRateUnits("0.01")
    assertEquals(BigInteger("10000"), rate)
    assertEquals(raceDonationUnits(100,rate), (1..100).fold(BigInteger.ZERO) { sum,_ -> sum+raceDonationUnits(1,rate) })
    assertFailsWith<IllegalArgumentException> { parseRaceRateUnits("0.0000001") }
}
```
- [ ] Add realHTTPconfig tests: Operator cannot changeeconomy/publish, Supportdenied, wrongOrigin/CSRF401/403, version409, invalidMonday/rates/rewards400, sparse/no configexplicitinactive. Acceptedfuture configimmutable; currentweek cannotpublish.
- [ ] Implement migration/schema/config routes and rules minimally until tests green. Metadata/liveclan/user deletion must notcascade away historical racekeys; no reset of otherfixturedata.
- [ ] Write RED actual donation/missionclaim/roster tests. Eventexamples:100separate100Gold donates vs10000Gold once at0.0001 =>exact1point, respecting existing multiple-of100 donation rules; actual100Gold at0.000001 yields0.0001positivepoint/eligibility; onlyone debit/eventonrequestreplay; DONATION and MISSION sources separate; two same-source simultaneousclaims onlyoneevent; zeroownprogress sharedmissionclaim getsnormalassetsbutno racepoints.
- [ ] Implement atomic sourcehooks. Missionpointsfromsuccessful firstclaim with actualprogressaftercurrenttenurestart; completedoldmission claimednewweek usesclaimweek, firstclaiminactive=>no laterbackfill. Contributionproofmust tie originalevent totenure, notjustcurrentlypositive memberfield. Existinglegacyclaim/economyrules unchanged.
- [ ] Test join/leave/kick/rejoin/concurrentaccept/lastmemberdissolve/exactboundary with retainedhistoricalintervals. Newtenurecan'treuseoldpoints/proof; donation/claim maynotattach towrongclanwhenmembershipchanges concurrently. Fixlockorderatsharedseam, nottesttimeouts.
- [ ] Run focusedREDGREEN, then final fullserver/protocol+unchanged86%Kover once, readXMLcounts; verify nodatabaseguardrelaxation andno leftover ownedschemas. Commit and reportactualAPI/DTO/store/lockorder/migration/testevidence forTask2–5.

## Task 2: Durable settlement, eligibility and race mailbox rewards

**Files:**
- Create `server/src/main/kotlin/com/hienthai/fastowin/server/clanrace/ClanRaceSettlement.kt`, `ClanRaceDelivery.kt`, `ClanRaceRewards.kt` plus separate JDBCqueryfile if needed.
- Extend `protocol/.../ClanRaceProtocol.kt`, `protocol/.../GiftProtocol.kt` with defaulted typed racecontext; narrowly extend `server/.../gifts/GiftModels.kt`, `GiftService.kt`, `PostgresGiftRepository.kt`, related in-memory fixtures as needed to reuse one claim path.
- Modify Task1module/database/main/application lifecycle hooks; new migration only if additional reward/settlement schema needed afterhead.
- Add `ClanRaceSettlementPostgresTest.kt`, `ClanRaceRewardsPostgresTest.kt`, protocolcompatibility and affected existinggifttests.

**Interfaces:**
- Consumes actual Task1 config/scoring/tenurequeries and lock order documentedtask-1-report.md. Produces `ClanRaceSettlement`/worker service and durableeligibleentitlement/mailboxbridge, actualfullDTOcontracts documentedtask-2-report.md.
- Mailbox context contains weekstart/end,clanID/name,finalrank,score,eligibility/cutoff,available/expiry andpermemberXP/Gold/Gem; walletsource `CLAN_WEEKLY_RACE`. SourceIDstable entitlement identity, notactorrequestid. Ordinaryadmincampaignrolescan'tcancel raceprizes.

- [ ] Write realPG failing cutoff/settlement scenarios using frozenclock (notOSclock). Exampleassertion shapes adapted to actual Task1 DTOs:
```kotlin
// A contributed before leaving; returns sameclan with no newcontribution. B contributed and stayed.
// worker runs aftercutoff, then clan kicks B: eligibility is still fromcutoff.
assertEquals(setOf(bUserId), frozenRecipients)
assertEquals(3, distinctRankedClans)
assertEquals(1, entitlementCountForB)
```
- [ ] Implement deterministicpositive-scoretop3 and immutablehistorysnapshots. Compare scoreDESC/achievedfinalscoretimeASC/stableUUIDASC. Resolve roster/clanexistence as-ofcutoff; no runtimecurrentroster shortcut.
- [ ] Test all boundary/membershipcases: kickbefore/aftercutoff inclworkerlate, rejoinwith/withoutnewpoints, switchclan, deletebefore/aftercutoff, morethan20contributors upto50, noeligiblememberbutpositiveclantotal, missingconfig/downtimeacrossweeks, zero-score, exactties.
- [ ] Implement settlementcheckpoint+entitlements+outbox in atomic durable path. Concurrentworkers/restartafterpartialdelivery/claimretry maintain one result/recipient/sourcegrant; failedrecipient retains retriable state or auditable deletedaccount handling.
- [ ] Add failing actualmailbox HTTP tests for racegift render/claim/claim-all/replay/concurrency/expired/overflow/foreignuser. Implement typedracebridge with samewallettransaction;7daysafteravailability, no falseADMIN_GIFTlabel, no lostrights afterkick. Preserveallnormalgift/compensationsemantics andoldDTOdefaults.
- [ ] Run focused then fullserver/protocol/Kover86once after finalcode; recordverifiedXML+racegift source/ledger counts; commit/reportcontracts foradmin/app/integrationtasks.

## Task 3: Admin weekly configuration and settlement visibility

**Files:**
- Create `server/src/main/resources/admin/admin-clan-race.js`, focusedCSS additions, register in `admin.js` and production/fixtureassetallowlists/config.
- Add `scripts/tests/admin-clan-race.test.mjs`, `e2e/tests/admin-clan-race.spec.mjs`, relatedfixturehandlers using actualfinalDTOs; optionally splitfixturehelperfocusedfile.
- Assetsecuritytest updates only fornewasset.

**Interfaces:**
- Consumes exactTask1+2 HTTPcontracts/reports; existingadmin API client/channelfencing/modal/notices/roles/paging. Produces configweeklist/detail/draft/rate/rewardpreview/publish/archivefuture andreadonlysettlementhistory, no newauth orinnerHTML.

- [ ] Write/run failing realDOM lifecycle test that Owner creates futureweekconfig, rate0.01Gold/10Gem, top1/2/3permemberrewards, previews andpublishes; assertactualJSONpayload/date/reward/rate/rolevalues rather than source strings.
- [ ] Implement sharednativeform labels andinlineerrors/focusablesummary. Explain rateper1currency, sixdecimals, exactsmallcontributions, previewexamples, permemberrewards+max50/hangbudget andcutoff/7dayclaim; distinguishactiveweek fromfuturedraft.
- [ ] Cover Operatorread-onlyeconomics/publishhidden, Supporthidden, maliciouscurrentrole/CSRF/versionbackendrejection remainsauthoritative. Unknownretry sameIDandbody, changedinputnewID, conflict reload, pendingcloseblocked andsession/view/logout fencing.
- [ ] Test inactive/no-config/loading/error/settling/settled/read-onlyhistory, escapedtext, fieldhintpreservation, query/paging, futurearchiveonly. Capture/view375/1440/landscape/largefont/focus/reducedmotion whiteMaterialconsole; no currentweekmutationbutton.
- [ ] Run Node/browserrelatedsuites+assetssecuritytests, syntax/diffcheck; commit/reportcommands/screenpaths/limits. No realprovider orproductionpublication.

## Task 4: App weekly race, current prizes and typed mailbox display

**Files:**
- Create `shared/.../data/network/ClanRaceApiClient.kt`, `shared/.../state/ClanRaceController.kt`, `shared/.../ui/screens/ClanRaceScreen.kt`, focusedpresentationcomponents.
- Modify narrow `FastToWinApp.kt`, `ClanScreen.kt`, mailboxpresentation andwalletsource label fornewracegift only; addprotocol/localizationtexts/catalog enough12languages.
- Add network/controller/presentationtests; `app/src/screenshotTest/.../ClanRaceScreenshotTest.kt` withfocusednewreferences, preserveexistingrefs unlesschangedvisibleUIrequiresmigration.

**Interfaces:**
- Consumes Task1/2 DTOs+currentaccountbearer from app, authoritativeweekly/config/standings/contribution/history/mailboxresponses. No clientscore/rewardarithmetic/fixedfallback. No sixthbottomtab.

- [ ] Write/run failing controller/API tests: account+clan+view+token epochs, staleoutoforder responses, reconnect/manualrefresh,401/403/503 retainedvaliddata/error, inactivevsblank, no rewardclaimbyarbitraryuserID; use actualHTTPfakeclient/repositorycontracts, notonlyduplicated implementation assertions.
- [ ] Implement newentry inBang andweeklyscreen usingactualDTOs: week/cutofftime/status, Quà tuần này3ranks, ownpositionoutsidepage, otherclanicon/name/level/score, ownmemberbreakdownandeligibility, historysnapshot. Smallpositivefraction mustnotdisplay0/incorrectineligible.
- [ ] Extend mailbox typedracecontext andlocalizedwalletlabel/source, preserveGiftMailboxControllerclaimidempotency/walletrefresh/normalgiftcompensation behavior. UIcantreatclaimsuccessonlyafterserverreceipt.
- [ ] Test/view375/desktop/landscape/1.6font/reducedmotion statesinactive/loading/error/pending/settled, longnames/bigexactscorestrings,12languages. AssertUIaccessibility/keyboardlabels, no raw unlocalized strings.
- [ ] Run shared/protocoltests/Kover52/localizationscanner/AndroiddevAPK+instrumentationcompile/JS/Wasm/relevantstrictscreens sequentially afterfinalsource. Do notstartemulator. Commit/reportnewrefs/provenance/layoutlimitations/contracts forTask5.

## Task 5: Actual integrated weekly race acceptance and documentation

**Files:**
- Create `e2e/support/clan-race-live.mjs`, test-source `server/src/test/.../clanrace/ClanRaceAcceptanceLauncher.kt`, buildtestclasspath task if required, `docs/clan-weekly-race.md`.
- Add focused startup/cleanup tests and only defectfixes identified by liveacceptance, eachwithRED regression.

**Interfaces:**
- Consumes finalactualadmin/player/mailboxcontracts andexistingownedPGfixture/staticrealWasmserver conventions. Produces sanitizedactualreceipts+walletledger/cutoff/historyevidence andvisibleadmin/appscreens, notmockedMissionState/APIfulfillment.

- [ ] Start loopbackonlybackend with ownedchildschemaandtest-sourceclock/scenarioinputs. Protectports/secrets andmanualpreview18080/18081/schema; use separateports18088/18089 anddo notstopPGusedbyuser. Failurepaths mustcleanup/password/schema/evidence inclmissingJava, provenbyrealfailed-starttest.
- [ ] Driveactualadmincreate/preview/publishconfig; advanceinjectedclock toweek, actualappreadQuàtuầnnày/standings, servercommitteddonation+missionclaim withcontributor/noncontributor andsource replay. AssertactualappHTTPandvisibleownrank/membereligibility, notfetch-only.
- [ ] Advancecutoff, simulatekickbefore/after/rejoinnewcontribution, workerlate/restart/concurrentsettlement. Verifytop3/frozenrecipients/history, actualappmailboxclaim/reload+concurrentHTTPclaim oneentitlement/source/ledger andliteralwalletdelta. Claim-allmixrace+admin+compensation retainsseparation.
- [ ] Exercise actualstorage503/retry, accountclanswitch/fencedreload, no-configweek, sparsepositivefraction/tie, exactboundary; preserveallactualresponseevidence withouttokens/traces. Snapshotusercontentfromotherenvironmentsneverneeded.
- [ ] Finalrelatedfullserver/protocol/shared/gift/admin/browser/compile/localization/coverage/screens once onfinaltree, inspectXMLactualcounts/thresholds. Do notredo unchangedgateswithoutreason; honestcachedprovenance. Viewfinalphone/desktopimages.
- [ ] Documentconfiguration/lifecycle/restart/cutoff/rejoin/rounding/mailbox/rolloutandrollbacklimits. Committaskandreportfullevidence+cleanup/no-productionstatus. Controllerdoesindependenttaskreviewandwholebranchreviewbeforeclaimingdone, thenpreservereportsandhumanintegrationdecision.


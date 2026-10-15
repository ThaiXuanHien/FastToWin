package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.*

class PostgresAdminRepositoryTest {
    @Test fun `player overview aggregates persisted totals once and honors Bangkok boundaries and directory population`() = runTest {
        database { source ->
            val now = java.time.Instant.parse("2026-10-15T02:30:00Z").toEpochMilli()
            val start = java.time.Instant.parse("2026-10-14T17:00:00Z").toEpochMilli()
            val service = AdminService(PostgresAdminRepository(source), AdminConfiguration("http://localhost:8080", false), PasswordHasher(1_000), { now })
            service.bootstrapOwner("owner@example.com", "GoodPassword123!")
            val owner = login(service)
            val users = List(4) { UUID.randomUUID() }
            val clan = UUID.randomUUID()
            source.connection.use { c ->
                c.prepareStatement("INSERT INTO users(id,account_type,status,email_normalized,password_hash,created_at,updated_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").use { s ->
                    users.forEachIndexed { i, id ->
                        s.setObject(1, id); s.setString(2, if (i == 1) "GUEST" else "REGISTERED"); s.setString(3, listOf("ACTIVE", "BLOCKED", "DELETED", "ACTIVE")[i]); s.setString(4, "fixture$i@example.com"); s.setString(5, "unused-fixture-hash"); s.executeUpdate()
                    }
                }
                c.prepareStatement("INSERT INTO profiles(user_id,display_name,player_code,created_at,updated_at) VALUES (?,'Player',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").use { s ->
                    users.take(3).forEachIndexed { i, id -> s.setObject(1, id); s.setString(2, "TESTCODE$i"); s.executeUpdate() }
                }
                c.prepareStatement("INSERT INTO player_stats(user_id,total_matches,wins,losses,draws,experience_points,elo_rating,updated_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)").use { s ->
                    users.take(3).forEachIndexed { i, id ->
                        s.setObject(1, id); s.setInt(2, if (i == 0) Int.MAX_VALUE else 3); s.setInt(3, if (i == 0) Int.MAX_VALUE - 2 else 1)
                        s.setInt(4, 1); s.setInt(5, 1); s.setInt(6, if (i == 0) 210 else 0); s.setInt(7, 1000 + i * 100); s.executeUpdate()
                    }
                }
                c.prepareStatement("INSERT INTO clans(id,name,owner_id,created_at) VALUES (?,'Clan',?,CURRENT_TIMESTAMP)").use { s -> s.setObject(1, clan); s.setObject(2, users[0]); s.executeUpdate() }
                c.prepareStatement("INSERT INTO clan_members(clan_id,user_id,role,joined_at) VALUES (?,?,'MEMBER',CURRENT_TIMESTAMP)").use { s ->
                    for (id in users.take(3)) { s.setObject(1, clan); s.setObject(2, id); s.executeUpdate() }
                }
                c.prepareStatement("INSERT INTO matches(id,room_name,game_mode,started_at,ended_at) VALUES (?,'Test','ORDER',?,?)").use { s ->
                    for (finished in listOf(start - 1, start, now - 1, now, now + 1)) {
                        val match = UUID.randomUUID(); s.setObject(1, match); s.setTimestamp(2, java.sql.Timestamp(finished - 100)); s.setTimestamp(3, java.sql.Timestamp(finished)); s.executeUpdate()
                        c.prepareStatement("INSERT INTO match_players(match_id,user_id,display_name,score,outcome) VALUES (?,?,'Player',10,'DRAW')").use { p ->
                            for (id in users.take(2)) { p.setObject(1, match); p.setObject(2, id); p.executeUpdate() }
                        }
                    }
                }
            }
            val result = service.playerOverview(owner.token)
            assertEquals(now, result.snapshotAt); assertEquals(start, result.todayFrom); assertEquals(now, result.todayUntil)
            assertEquals("3", result.totalPlayers); assertEquals("2", result.registeredPlayers); assertEquals("1", result.guestPlayers)
            assertEquals("1", result.playableAccounts); assertEquals("2", result.matchesToday)
            assertEquals("2147483653", result.recordedParticipations); assertEquals("2147483647", result.recordedWins)
            assertEquals("3", result.recordedLosses); assertEquals("3", result.recordedDraws); assertEquals("3", result.playedProfiles)
            assertEquals("1", result.clanCount); assertEquals("3", result.clanMembers)
            assertEquals("1100.0", result.averageElo); assertEquals(1200, result.maxElo)
            val detail = service.user(owner.token, users[0].toString())
            assertEquals(3, detail.level); assertEquals(3, service.users(owner.token, users[0].toString(), PageRequest()).items.single().level)
            assertEquals(Int.MAX_VALUE, detail.totalMatches); assertEquals(Int.MAX_VALUE - 2, detail.wins)
            assertEquals(1, detail.losses); assertEquals(1, detail.draws)
            assertEquals(5, service.matches(owner.token, users[0].toString(), PageRequest(pageSize = 1)).total)
            assertEquals(1, service.audit(owner.token, PageRequest(pageSize = 100)).items.count { it.action == "PLAYER_OVERVIEW" })
            source.connection.use { c -> c.createStatement().use { it.execute("ALTER TABLE admin_audit ADD CONSTRAINT reject_overview CHECK(action <> 'PLAYER_OVERVIEW') NOT VALID") } }
            assertEquals(503, assertFailsWith<AdminException> { service.playerOverview(owner.token) }.status)
        }
    }
    @Test fun `empty directory has zero totals and unplayed profiles use actual default stats and level`() = runTest {
        database { source ->
            val service = service(source); service.bootstrapOwner("owner@example.com", "GoodPassword123!"); val owner = login(service)
            val empty = service.playerOverview(owner.token)
            assertEquals("0", empty.totalPlayers); assertEquals("0", empty.recordedParticipations); assertEquals("0", empty.matchesToday)
            assertEquals("0", empty.clanMembers); assertEquals("0", empty.playedProfiles); assertNull(empty.averageElo); assertNull(empty.maxElo)
            val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1_000))
            val id = assertIs<AuthResult.Success>(auth.register("unplayed@example.com", "GoodPassword123!", "Unplayed", "test")).session.userId
            val unplayed = service.playerOverview(owner.token)
            assertEquals("1", unplayed.totalPlayers); assertEquals("0", unplayed.recordedParticipations); assertEquals("0", unplayed.playedProfiles)
            assertEquals("1000.0", unplayed.averageElo); assertEquals(1000, unplayed.maxElo)
            val detail = service.user(owner.token, id)
            assertEquals(0, detail.totalMatches); assertEquals(0, detail.wins); assertEquals(0, detail.losses); assertEquals(0, detail.draws)
            assertEquals(1, service.giftRecipient(owner.token, detail.playerCode).level)
        }
    }
    @Test fun `sql public codes and normalized emails resolve exactly and exclude ineligible recipients`() = runTest {
        database { source ->
            val service = service(source); service.bootstrapOwner("owner@example.com", "GoodPassword123!"); val owner = login(service)
            val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1_000))
            val a = assertIs<AuthResult.Success>(auth.register("recipient@example.com", "GoodPassword123!", "Recipient", "test")).session.userId
            val b = assertIs<AuthResult.Success>(auth.register("removed@example.com", "GoodPassword123!", "Removed", "test")).session.userId
            val code = service.user(owner.token, a).playerCode
            val deletedCode = service.user(owner.token, b).playerCode
            assertTrue(code.isNotBlank())
            assertEquals(code, service.users(owner.token, " ${code.lowercase()} ", PageRequest()).items.single().playerCode)
            for (reference in listOf(code.lowercase(), " RECIPIENT@EXAMPLE.COM ")) {
                assertEquals(a, service.giftRecipient(owner.token, reference).id)
            }
            for (reference in listOf(code.dropLast(1), "recipient@", "Recipient", a, "%", "_", "' OR TRUE --")) {
                assertEquals(404, assertFailsWith<AdminException> { service.giftRecipient(owner.token, reference) }.status)
            }
            source.connection.use { connection ->
                connection.prepareStatement("UPDATE users SET status='DELETED' WHERE id=?").use { it.setObject(1, UUID.fromString(b)); it.executeUpdate() }
            }
            assertEquals(404, assertFailsWith<AdminException> { service.giftRecipient(owner.token, deletedCode) }.status)
            source.connection.use { connection ->
                connection.createStatement().use { it.execute("ALTER TABLE admin_audit ADD CONSTRAINT reject_lookup CHECK(action <> 'GIFT_RECIPIENT_LOOKUP') NOT VALID") }
            }
            assertEquals(503, assertFailsWith<AdminException> { service.giftRecipient(owner.token, code) }.status)
            assertFalse(service.audit(owner.token, PageRequest(pageSize = 100)).items.toString().contains("recipient@example.com"))
        }
    }
    private suspend fun database(block: suspend (HikariDataSource) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL absent: PostgreSQL coverage skipped")
        val schema = "admin_test_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"; maximumPoolSize = 4
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { statement -> statement.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema = schema }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    block(source)
                }
            } finally { setup.connection.use { it.createStatement().use { statement -> statement.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }
    private fun service(source: HikariDataSource) = AdminService(PostgresAdminRepository(source), AdminConfiguration("http://localhost:8080", false), PasswordHasher(1_000))
    private suspend fun login(service: AdminService, email: String = "owner@example.com") = service.login(email, "GoodPassword123!", "http://localhost:8080", "127.0.0.1")

    @Test fun `actual sql pagination masking uuid query and histories survive repository restart`() = runTest {
        database { source ->
            val service = service(source); service.bootstrapOwner("owner@example.com", "GoodPassword123!"); val owner = login(service)
            val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1_000))
            val a = assertIs<AuthResult.Success>(auth.register("player-a@example.com", "GoodPassword123!", "Alpha%", "test")).session.userId
            val b = assertIs<AuthResult.Success>(auth.register("player-b@example.com", "GoodPassword123!", "Beta", "test")).session.userId
            source.connection.use { c ->
                c.prepareStatement("INSERT INTO wallet_transactions(id,user_id,source_type,source_id,gold_delta,created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)").use { s ->
                    for ((type, delta) in listOf("ADMIN_GIFT" to 5, "COMPENSATION" to 7, "CHECK_IN" to 10)) {
                        s.setObject(1, UUID.randomUUID()); s.setObject(2, UUID.fromString(a)); s.setString(3, type); s.setString(4, "fixture-$type"); s.setInt(5, delta); s.executeUpdate()
                    }
                }
                val match = UUID.randomUUID()
                c.prepareStatement("INSERT INTO matches(id,room_name,game_mode,started_at,ended_at,match_type) VALUES (?,'Test','ORDER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'RANKED')").use { s -> s.setObject(1, match); s.executeUpdate() }
                c.prepareStatement("INSERT INTO match_players(match_id,user_id,display_name,score,outcome) VALUES (?,?,'Alpha',12,'WIN')").use { s -> s.setObject(1,match); s.setObject(2, UUID.fromString(a)); s.executeUpdate() }
            }
            assertEquals(2, service.users(owner.token, "", PageRequest(pageSize = 1)).total)
            val page0 = service.users(owner.token, "", PageRequest(pageSize = 1)); val page1 = service.users(owner.token, "", PageRequest(1,1))
            assertNotEquals(page0.items.single().id, page1.items.single().id)
            assertEquals(a, service.users(owner.token, "%", PageRequest()).items.single().id)
            assertEquals(b, service.users(owner.token, b, PageRequest()).items.single().id)
            service.createAccount(owner.token, owner.response.csrfToken, "http://localhost:8080", CreateAdminRequest("support@example.com", "GoodPassword123!", AdminRole.SUPPORT, "Hỗ trợ", "support"))
            val support = login(service, "support@example.com")
            assertEquals("p***@e***.com", service.user(support.token, a).email)
            assertEquals(403, assertFailsWith<AdminException> { service.users(support.token, "player-a@example.com", PageRequest()) }.status)
            val restarted = service(source)
            assertEquals(AdminRole.SUPPORT, restarted.session(support.token).admin.role)
            assertEquals(support.response.csrfToken, restarted.session(support.token).csrfToken)
            assertEquals(3, restarted.wallet(support.token, a, false, PageRequest()).total)
            assertEquals(setOf("ADMIN_GIFT", "COMPENSATION"), restarted.wallet(support.token, a, true, PageRequest()).items.map { it.sourceType }.toSet())
            assertEquals(12, restarted.matches(support.token, a, PageRequest()).items.single().score)
            assertNotNull(restarted.user(support.token, a).lastActivityAtEpochMillis)
            val audits = restarted.audit(owner.token, PageRequest()).items
            assertFalse(audits.toString().contains("player-a@example.com")); assertFalse(audits.toString().contains("GoodPassword"))
        }
    }

    @Test fun `audit failure rolls back credential mutation and successful request deduplicates`() = runTest {
        database { source ->
            val service = service(source); service.bootstrapOwner("owner@example.com", "GoodPassword123!"); val owner = login(service)
            val request = CreateAdminRequest("support@example.com", "GoodPassword123!", AdminRole.SUPPORT, "Hỗ trợ", "create-support")
            source.connection.use { it.createStatement().use { s -> s.execute("ALTER TABLE admin_audit ADD CONSTRAINT reject_create CHECK(action <> 'ADMIN_CREATE')") } }
            assertEquals(503, assertFailsWith<AdminException> { service.createAccount(owner.token,owner.response.csrfToken,"http://localhost:8080",request) }.status)
            assertEquals(1, service.accounts(owner.token, PageRequest()).total)
            source.connection.use { it.createStatement().use { s -> s.execute("ALTER TABLE admin_audit DROP CONSTRAINT reject_create") } }
            val support = service.createAccount(owner.token,owner.response.csrfToken,"http://localhost:8080",request)
            assertEquals(support, service.createAccount(owner.token,owner.response.csrfToken,"http://localhost:8080",request))
            assertEquals(409, assertFailsWith<AdminException> { service.createAccount(owner.token,owner.response.csrfToken,"http://localhost:8080",request.copy(reason="Khác")) }.status)
            val access = ChangeAdminAccessRequest(AdminRole.SUPPORT, false, "Thu hồi", "revoke-support")
            service.changeAccess(owner.token,owner.response.csrfToken,"http://localhost:8080", support.id, access)
            service.changeAccess(owner.token,owner.response.csrfToken,"http://localhost:8080", support.id, access)
            val audits = service.audit(owner.token,PageRequest()).items
            assertEquals(1,audits.count { it.action == "ADMIN_CREATE" }); assertEquals(1,audits.count { it.action == "ADMIN_ACCESS" })
        }
    }

    @Test fun `persisted sql creation replay has no fast password verifier and rejects changed passwords`() = runTest {
        database { source ->
            val service=service(source); service.bootstrapOwner("owner@example.com","GoodPassword123!"); val owner=login(service)
            val request=CreateAdminRequest("support@example.com","GoodPassword123!",AdminRole.SUPPORT,"Hỗ trợ","safe-replay")
            val created=service.createAccount(owner.token,owner.response.csrfToken,"http://localhost:8080",request)
            source.connection.use { connection ->
                connection.prepareStatement("SELECT fingerprint,safe_result FROM admin_requests WHERE actor_id=? AND request_id=?").use { statement ->
                    statement.setObject(1,UUID.fromString(owner.response.admin.id)); statement.setString(2,request.requestId)
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals("17973d02c8a96af49ac3d988e55090f5e7f3cab3adea4cc4c8394baca6b18cb3",result.getString("fingerprint"))
                        assertFalse(result.getString("safe_result").contains(request.password))
                        assertFalse(result.getString("safe_result").contains(hashToken(request.password)))
                    }
                }
            }
            val restarted=service(source)
            assertEquals(created,restarted.createAccount(owner.token,owner.response.csrfToken,"http://localhost:8080",request))
            for (changed in listOf(request.copy(password="DifferentPassword!"),request.copy(role=AdminRole.OWNER),request.copy(reason="Khác"),request.copy(email="other@example.com"))) {
                assertEquals(409,assertFailsWith<AdminException> { restarted.createAccount(owner.token,owner.response.csrfToken,"http://localhost:8080",changed) }.status)
            }
            restarted.changeAccess(owner.token,owner.response.csrfToken,"http://localhost:8080",created.id,ChangeAdminAccessRequest(AdminRole.OPERATOR,true,"Vận hành","change-after-create"))
            assertEquals(created,restarted.createAccount(owner.token,owner.response.csrfToken,"http://localhost:8080",request))
            assertEquals(1,restarted.audit(owner.token,PageRequest()).items.count { it.action=="ADMIN_CREATE" })
        }
    }
    @Test fun `dedicated owner does not inherit or overwrite an existing player credential`() = runTest {
        database { source ->
            val auth=AuthenticationService(PostgresAuthRepository(source),passwordHasher=PasswordHasher(1_000))
            val player=assertIs<AuthResult.Success>(auth.register("owner@example.com","PlayerPassword!","Owner player","test")).session.userId
            val service=service(source); val owner=service.bootstrapOwner("owner@example.com","GoodPassword123!")
            assertNotEquals(player,owner.id)
            assertEquals(401,assertFailsWith<AdminException> { service.login("owner@example.com","PlayerPassword!","http://localhost:8080","127.0.0.1") }.status)
            assertEquals(AdminRole.OWNER,login(service).response.admin.role)
        }
    }
    @Test fun `failed sensitive read audit does not expose data`() = runTest {
        database { source ->
            val service=service(source); service.bootstrapOwner("owner@example.com","GoodPassword123!"); val owner=login(service)
            val auth=AuthenticationService(PostgresAuthRepository(source),passwordHasher=PasswordHasher(1_000))
            assertIs<AuthResult.Success>(auth.register("player@example.com","GoodPassword123!","Player","test"))
            source.connection.use { it.createStatement().use { s -> s.execute("ALTER TABLE admin_audit ADD CONSTRAINT reject_search CHECK(action <> 'USER_SEARCH')") } }
            assertEquals(503,assertFailsWith<AdminException> { service.users(owner.token,"",PageRequest()) }.status)
            assertFalse(service.audit(owner.token,PageRequest()).items.any { it.action=="USER_SEARCH" })
        }
    }
    @Test fun `concurrent owner revocations preserve one active owner`() = runTest {
        database { source ->
            val service = service(source); val first = service.bootstrapOwner("owner@example.com", "GoodPassword123!"); val a = login(service)
            val second = service.createAccount(a.token,a.response.csrfToken,"http://localhost:8080",CreateAdminRequest("second@example.com","GoodPassword123!",AdminRole.OWNER,"Dự phòng","second"))
            val b = login(service,"second@example.com")
            val outcomes = coroutineScope {
                listOf(async { runCatching { service.changeAccess(a.token,a.response.csrfToken,"http://localhost:8080",first.id,ChangeAdminAccessRequest(AdminRole.SUPPORT,false,"Thu hồi","a")) } },
                    async { runCatching { service.changeAccess(b.token,b.response.csrfToken,"http://localhost:8080",second.id,ChangeAdminAccessRequest(AdminRole.SUPPORT,false,"Thu hồi","b")) } }).map { it.await() }
            }
            assertEquals(1,outcomes.count { it.isSuccess }); assertEquals(409, assertIs<AdminException>(outcomes.single { it.isFailure }.exceptionOrNull()).status)
            assertEquals(1, PostgresAdminRepository(source).transaction { activeOwners() })
        }
    }
}

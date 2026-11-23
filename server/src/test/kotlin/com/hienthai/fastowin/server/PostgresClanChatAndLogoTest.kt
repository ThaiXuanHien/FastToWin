package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.CLAN_CHAT_MAX_STORED_MESSAGES
import com.hienthai.fastowin.protocol.ClanChatMessageKind
import com.hienthai.fastowin.protocol.ClanChatMessageSnapshot
import com.hienthai.fastowin.protocol.ClanChatSystemEvent
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.UgcTargetType
import com.hienthai.fastowin.server.avatars.AvatarReadResult
import com.hienthai.fastowin.server.avatars.AvatarService
import com.hienthai.fastowin.server.avatars.ClanLogoService
import com.hienthai.fastowin.server.avatars.ClanLogoUploadStatus
import com.hienthai.fastowin.server.avatars.MemoryAvatarObjects
import com.hienthai.fastowin.server.avatars.PostgresAvatarRepository
import com.hienthai.fastowin.server.avatars.PostgresClanLogoRepository
import com.hienthai.fastowin.server.moderation.PostgresModerationRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresClanChatAndLogoTest {
    @Test
    fun `chat pages newest first, trims to the stored limit and keeps lines of departed members`() = runTest { database { f ->
        val leader = f.player(); val member = f.player()
        val clan = f.clan(leader, member)
        val chat = PostgresClanChatRepository(f.source)
        repeat(CLAN_CHAT_MAX_STORED_MESSAGES + 5) { chat.append(f.line(clan, member, "line $it", 1_000L + it)) }
        val newest = chat.page(clan.toString(), null, 3, 0)
        assertEquals(listOf("line 504", "line 503", "line 502"), newest.map { it.text })
        val older = chat.page(clan.toString(), newest.last(), 2, 0)
        assertEquals(listOf("line 501", "line 500"), older.map { it.text })
        assertEquals(CLAN_CHAT_MAX_STORED_MESSAGES, f.count("SELECT COUNT(*) FROM clan_chat_messages WHERE clan_id = ?", clan))
        assertTrue(chat.page(clan.toString(), null, 1, 2_000L).isEmpty())
        assertEquals(CLAN_CHAT_MAX_STORED_MESSAGES, chat.cleanup(10_000L))

        val kept = f.line(clan, member, "still here", 20_000L)
        chat.append(kept)
        f.execute("DELETE FROM clan_members WHERE user_id = ?", member)
        assertEquals("still here", chat.find(clan.toString(), kept.id)?.text)
        val system = ClanChatMessageSnapshot(UUID.randomUUID().toString(), clan.toString(), ClanChatMessageKind.SYSTEM, 20_001L,
            systemEvent = ClanChatSystemEvent.MEMBER_LEFT, systemArgs = mapOf("player" to "Member", "playerId" to member.toString()))
        chat.append(system)
        assertEquals(system, chat.findById(system.id))
        assertTrue(chat.delete(clan.toString(), kept.id, 30_000L))
        assertFalse(chat.delete(clan.toString(), kept.id, 30_001L))
        assertNull(chat.find(clan.toString(), kept.id))
        assertEquals(listOf(system.id), chat.page(clan.toString(), null, 10, 0).map { it.id })

        assertEquals(1, chat.cleanup(20_001L))
        f.execute("DELETE FROM clans WHERE id = ?", clan)
        assertEquals(0, f.count("SELECT COUNT(*) FROM clan_chat_messages WHERE clan_id = ?", clan))
    } }

    @Test
    fun `mutes, read positions and push throttles are per clan member`() = runTest { database { f ->
        val leader = f.player(); val member = f.player()
        val clan = f.clan(leader, member).toString()
        val chat = PostgresClanChatRepository(f.source)
        chat.setMute(clan, member.toString(), 5_000L, leader.toString())
        assertEquals(5_000L, chat.mutedUntil(clan, member.toString(), 4_999L))
        assertNull(chat.mutedUntil(clan, member.toString(), 5_000L))
        assertEquals(mapOf(member.toString() to 5_000L), chat.activeMutes(clan, 1_000L))
        chat.setMute(clan, member.toString(), null, leader.toString())
        assertTrue(chat.activeMutes(clan, 1_000L).isEmpty())

        chat.append(f.line(clan, member, "a", 100L))
        chat.append(f.line(clan, member, "b", 200L))
        chat.append(f.line(clan, leader, "mine", 300L))
        assertEquals(2, chat.unreadCount(clan, leader.toString(), 0))
        assertEquals(1, chat.unreadCount(clan, member.toString(), 0))
        chat.markRead(clan, leader.toString(), 100L)
        chat.markRead(clan, leader.toString(), 50L)
        assertEquals(1, chat.unreadCount(clan, leader.toString(), 0))
        assertEquals(0, chat.unreadCount(clan, leader.toString(), 250L))

        val ids = listOf(leader.toString(), member.toString())
        val now = 1_900_000_000_000L
        assertEquals(ids.toSet(), chat.claimPush(clan, ids, now, 1_800_000L))
        assertTrue(chat.claimPush(clan, ids, now + 1, 1_800_000L).isEmpty())
        assertEquals(ids.toSet(), chat.claimPush(clan, ids, now + 1_800_000L, 1_800_000L))
    } }

    @Test
    fun `uploaded logo is cropped, stored, shown in snapshots and cleaned after removal or dissolution`() = runTest { database { f ->
        val leader = f.player()
        val clan = f.clan(leader).toString()
        val objects = MemoryAvatarObjects()
        var now = System.currentTimeMillis()
        val logos = PostgresClanLogoRepository(f.source) { now }
        val service = ClanLogoService(AvatarService(logos, objects, objectWrites = true,
            normalizer = ClanLogoService.normalizer(), keyPrefix = ClanLogoService.KEY_PREFIX), logos) { now }
        val clans = PostgresClanRepository(f.source)
        assertNull(clans.getClanById(clan)?.logoRevision)
        assertNull(service.currentRevision(clan))

        assertEquals(ClanLogoUploadStatus.INVALID_IMAGE, service.upload(clan, "bm90IGFuIGltYWdl"))
        assertEquals(ClanLogoUploadStatus.UPDATED, service.upload(clan, pngBase64(900, 400)))
        val revision = assertNotNull(clans.getClanById(clan)?.logoRevision)
        assertEquals(revision, clans.getClanList(10, 0, null).single { it.id == clan }.logoRevision)
        val key = objects.objects.keys.single()
        assertTrue(key.startsWith("clan-logos/$clan/"))
        val image = ImageIO.read(assertIs<AvatarReadResult.Found>(service.read(clan)).bytes.inputStream())
        assertEquals(256 to 256, image.width to image.height)

        // A replaced image is queued for deletion only after the object store no longer needs it.
        assertEquals(ClanLogoUploadStatus.UPDATED, service.upload(clan, pngBase64(64, 64)))
        assertEquals(1, f.count("SELECT COUNT(*) FROM avatar_cleanup WHERE object_key = ? AND state = 'pending'", key))
        val currentKey = objects.objects.keys.single { it != key }
        assertTrue(PostgresAvatarRepository(f.source) { now + 2 * 86_400_000L }.claimCleanup(100).none { it.key == currentKey })

        repeat(3) { service.upload(clan, pngBase64(32, 32)) }
        assertEquals(ClanLogoUploadStatus.DAILY_LIMIT, service.upload(clan, pngBase64(32, 32)))
        now += 86_400_001L
        assertEquals(ClanLogoUploadStatus.UPDATED, service.upload(clan, pngBase64(32, 32)))

        assertTrue(service.remove(clan))
        assertNull(clans.getClanById(clan)?.logoRevision)
        assertIs<AvatarReadResult.NotFound>(service.read(clan))
        assertTrue(service.remove(clan))

        service.upload(clan, pngBase64(40, 40))
        val last = f.text("SELECT object_key FROM clan_logos WHERE clan_id = ?", UUID.fromString(clan))
        f.execute("DELETE FROM clans WHERE id = ?", UUID.fromString(clan))
        assertEquals(1, f.count("SELECT COUNT(*) FROM avatar_cleanup WHERE object_key = ? AND state = 'pending'", last))
    } }

    @Test
    fun `leader appoints at most three co-leaders who may change the catalog icon`() = runTest { database { f ->
        val leader = f.player()
        val members = List(4) { f.player() }
        val clan = f.clan(leader, *members.toTypedArray()).toString()
        val clans = PostgresClanRepository(f.source)
        assertEquals(ClanLogoUpdateStatus.NOT_OWNER, clans.updateLogoId(clan, members[0].toString(), "flag"))
        assertEquals(ClanRoleUpdateStatus.NOT_OWNER, clans.setMemberRole(clan, members[1].toString(), members[0].toString(), ClanRole.CO_LEADER))
        members.take(3).forEach {
            assertEquals(ClanRoleUpdateStatus.UPDATED, clans.setMemberRole(clan, leader.toString(), it.toString(), ClanRole.CO_LEADER))
        }
        assertEquals(ClanRoleUpdateStatus.UNCHANGED, clans.setMemberRole(clan, leader.toString(), members[0].toString(), ClanRole.CO_LEADER))
        assertEquals(ClanRoleUpdateStatus.LIMIT_REACHED, clans.setMemberRole(clan, leader.toString(), members[3].toString(), ClanRole.CO_LEADER))
        assertEquals(ClanRoleUpdateStatus.NOT_MEMBER, clans.setMemberRole(clan, leader.toString(), f.player().toString(), ClanRole.CO_LEADER))
        assertEquals(ClanRoleUpdateStatus.FAILED, clans.setMemberRole(clan, leader.toString(), leader.toString(), ClanRole.MEMBER))
        assertEquals(ClanLogoUpdateStatus.UPDATED, clans.updateLogoId(clan, members[0].toString(), "flag"))
        assertEquals("flag", clans.getClanById(clan)?.logoId)
        assertEquals(ClanRoleUpdateStatus.UPDATED, clans.setMemberRole(clan, leader.toString(), members[0].toString(), ClanRole.MEMBER))
        assertEquals(ClanLogoUpdateStatus.NOT_OWNER, clans.updateLogoId(clan, members[0].toString(), "shield"))
        assertEquals(ClanRoleUpdateStatus.UPDATED, clans.setMemberRole(clan, leader.toString(), members[3].toString(), ClanRole.CO_LEADER))
        assertEquals(3, clans.getClanById(clan)!!.members.count { it.role == ClanRole.CO_LEADER })
    } }

    @Test
    fun `chat lines and uploaded logos are reportable moderation targets`() = runTest { database { f ->
        val leader = f.player(); val member = f.player()
        val clan = f.clan(leader, member)
        val chat = PostgresClanChatRepository(f.source)
        val line = f.line(clan, member, "bad words", 1_000L)
        chat.append(line)
        val moderation = PostgresModerationRepository(f.source)
        val target = assertNotNull(moderation.read { target(UgcTargetType.CLAN_MESSAGE, line.id) })
        assertEquals(member.toString(), target.ownerId)
        assertEquals("bad words", target.text)
        assertNull(moderation.read { target(UgcTargetType.CLAN_LOGO, clan.toString()) })

        val logos = PostgresClanLogoRepository(f.source)
        AvatarService(logos, normalizer = ClanLogoService.normalizer(), keyPrefix = ClanLogoService.KEY_PREFIX)
            .upload(clan.toString(), pngBase64(32, 32))
        val logo = assertNotNull(moderation.read { target(UgcTargetType.CLAN_LOGO, clan.toString()) })
        assertEquals(leader.toString(), logo.ownerId)
        assertEquals("", logo.text)
        f.execute("""INSERT INTO ugc_reports(id,version,status,reporter_id,target_type,target_id,target_owner_id,reason,note,evidence_text,created_at)
            VALUES (?,1,'NEW',?,'CLAN_MESSAGE',?,?,'SPAM','','bad words',1)""", UUID.randomUUID(), leader, UUID.fromString(line.id), member)
        chat.delete(clan.toString(), line.id, 2_000L)
        assertNull(moderation.read { target(UgcTargetType.CLAN_MESSAGE, line.id) })
    } }

    private class Fixture(val source: HikariDataSource) {
        suspend fun player(): UUID = UUID.fromString(
            PostgresGuestIdentityRepository(source).resolveGuest("Chat test", null, 1_900_000_000_000L).playerId)

        fun clan(leader: UUID, vararg members: UUID): UUID {
            val clan = UUID.randomUUID()
            execute("INSERT INTO clans (id, name, description, owner_id, logo_id, created_at) VALUES (?, ?, '', ?, 'shield', CURRENT_TIMESTAMP)",
                clan, "Chat ${clan.toString().take(8)}", leader)
            execute("INSERT INTO clan_members (clan_id, user_id, role, joined_at) VALUES (?, ?, 'LEADER', CURRENT_TIMESTAMP)", clan, leader)
            members.forEach { execute("INSERT INTO clan_members (clan_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', CURRENT_TIMESTAMP)", clan, it) }
            return clan
        }

        fun line(clan: Any, sender: UUID, text: String, at: Long) = ClanChatMessageSnapshot(
            UUID.randomUUID().toString(), clan.toString(), ClanChatMessageKind.USER, at, sender.toString(), "Sender", text)

        fun execute(sql: String, vararg arguments: Any) {
            source.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    arguments.forEachIndexed { index, argument -> statement.setObject(index + 1, argument) }
                    statement.executeUpdate()
                }
            }
        }

        fun count(sql: String, vararg arguments: Any): Int = source.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                arguments.forEachIndexed { index, argument -> statement.setObject(index + 1, argument) }
                statement.executeQuery().use { it.next(); it.getInt(1) }
            }
        }

        fun text(sql: String, vararg arguments: Any): String = source.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                arguments.forEachIndexed { index, argument -> statement.setObject(index + 1, argument) }
                statement.executeQuery().use { it.next(); it.getString(1) }
            }
        }
    }

    private suspend fun database(block: suspend (Fixture) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL") ?: return
        val username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
        val password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
        val schema = "clan_chat_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        fun source(jdbc: String) = HikariDataSource(HikariConfig().apply {
            jdbcUrl = jdbc
            this.username = username
            this.password = password
            maximumPoolSize = 3
        })
        source(url).use { admin ->
            admin.connection.use { connection -> connection.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
            try {
                val schemaUrl = url + if ('?' in url) "&currentSchema=$schema" else "?currentSchema=$schema"
                source(schemaUrl).use { dataSource ->
                    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                        .schemas(schema).defaultSchema(schema).load().migrate()
                    block(Fixture(dataSource))
                }
            } finally {
                admin.connection.use { connection -> connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}

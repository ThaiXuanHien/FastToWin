package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ClanLogoGameEngineTest {
    @Test
    fun `locked logo returns localized requirement from authoritative repository`() = runTest {
        val fixture = Fixture(ClanLogoUpdateStatus.LOCKED)
        val error = assertIs<ServerMessage.Error>(fixture.update("dragon").single().message)
        assertEquals("CLAN_LOGO_LOCKED", error.code)
        assertEquals("ServerClanLogoLocked", error.messageKey)
        assertEquals(mapOf("level" to "15"), error.messageArgs)
    }

    @Test
    fun `invalid logo returns localized error`() = runTest {
        val fixture = Fixture(ClanLogoUpdateStatus.INVALID_LOGO)
        listOf("unknown", "Dragon", " dragon", "dragon ", "").forEach { logo ->
            val error = assertIs<ServerMessage.Error>(fixture.update(logo).single().message)
            assertEquals("INVALID_CLAN_LOGO", error.code)
            assertEquals("ServerInvalidClanLogo", error.messageKey)
        }
    }

    @Test
    fun `repository ownership rejection overrides stale owner snapshot`() = runTest {
        val fixture = Fixture(ClanLogoUpdateStatus.NOT_OWNER)
        val error = assertIs<ServerMessage.Error>(fixture.update("flag").single().message)
        assertEquals("NOT_CLAN_OWNER", error.code)
    }

    @Test
    fun `deleted clan is reported from authoritative update`() = runTest {
        val fixture = Fixture(ClanLogoUpdateStatus.CLAN_NOT_FOUND)
        val error = assertIs<ServerMessage.Error>(fixture.update("flag").single().message)
        assertEquals("CLAN_NOT_FOUND", error.code)
    }

    @Test
    fun `allowed and grandfathered same logo refresh authoritative clan data`() = runTest {
        val fixture = Fixture(ClanLogoUpdateStatus.UPDATED)
        listOf("flag", "dragon").forEach { logo ->
            val message = assertIs<ServerMessage.ClanInfoData>(fixture.update(logo).single().message)
            assertEquals("dragon", message.clan.logoId)
            assertEquals(1, message.clan.level)
        }
    }

    private class Fixture(val status: ClanLogoUpdateStatus) {
        val owner = UUID.randomUUID().toString()
        val clan = ClanSnapshot(UUID.randomUUID().toString(), "Original clan", "", owner,
            emptyList(), 0, logoId = "dragon", level = 1)
        val repository = object : ClanRepository by NoOpClanRepository {
            override suspend fun getClanById(clanId: String) = clan.takeIf { it.id == clanId }
            override suspend fun updateLogoId(clanId: String, ownerId: String, logoId: String): ClanLogoUpdateStatus {
                check(clanId == clan.id && ownerId == owner)
                return status
            }
        }
        val engine = GameEngine(clanRepository = repository)
        var connected = false

        suspend fun update(logoId: String): List<Delivery> {
            if (!connected) {
                engine.connectAccount(AuthenticatedAccount(UUID.fromString(owner), "Owner"))
                connected = true
            }
            return engine.handle(owner, ClientMessage.UpdateClanLogo(clan.id, logoId))
        }
    }
}

package com.hienthai.fastowin.protocol

import com.hienthai.fastowin.localization.protocolTextKeyForCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class ClanIconProtocolTest {
    @Test fun `catalog contains the approved ten icons and exact unlock levels`() {
        val expected = linkedMapOf("shield" to 1, "flag" to 1, "sword" to 3, "wolf" to 5,
            "eagle" to 8, "lion" to 10, "dragon" to 15, "phoenix" to 20, "crown" to 25, "kirin" to 30)
        assertEquals(expected, CLAN_LOGOS.associate { it.id to it.requiredLevel })
        assertEquals(expected.keys.toList(), CLAN_AVATAR_IDS)
    }

    @Test fun `each icon is locked before its level and unlocked at the exact boundary`() {
        val levels = mapOf("sword" to 3, "wolf" to 5, "eagle" to 8, "lion" to 10,
            "dragon" to 15, "phoenix" to 20, "crown" to 25, "kirin" to 30)
        levels.forEach { (id, level) ->
            assertEquals(level, clanLogoRequiredLevel(id))
            assertFalse(isClanLogoUnlocked(id, level - 1))
            assertTrue(isClanLogoUnlocked(id, level))
        }
        assertFalse(isClanLogoUnlocked("shield", 0))
        assertFalse(isClanLogoUnlocked("unknown", 50))
        assertFalse(isClanLogoUnlocked("CROWN", 50))
        assertNull(clanLogoRequiredLevel(" crown "))
    }

    @Test fun `personal leaderboard clan identity survives serialization without changing rank`() {
        val entry = ProtocolJson.decodeFromString<LeaderboardEntrySnapshot>("""{"rank":12,"displayName":"Player","playerCode":"PLAYER","wins":1,"totalMatches":2,"highestScore":50,"clanId":"clan","clanName":"Clan","clanLogoId":"phoenix"}""")
        val json = ProtocolJson.parseToJsonElement(ProtocolJson.encodeToString(entry)).jsonObject
        assertEquals("clan", json["clanId"]?.jsonPrimitive?.content)
        assertEquals("Clan", json["clanName"]?.jsonPrimitive?.content)
        assertEquals("phoenix", json["clanLogoId"]?.jsonPrimitive?.content)
        assertEquals(12, entry.rank)
    }

    @Test fun `clan leaderboard icon survives serialization`() {
        val entry = ProtocolJson.decodeFromString<ClanLeaderboardEntrySnapshot>("""{"rank":3,"clanId":"clan","clanName":"Clan","totalElo":1000,"memberCount":1,"logoId":"kirin"}""")
        val json = ProtocolJson.parseToJsonElement(ProtocolJson.encodeToString(entry)).jsonObject
        assertEquals("kirin", json["logoId"]?.jsonPrimitive?.content)
    }

    @Test fun `stable icon errors map to localized client messages`() {
        assertNotNull(protocolTextKeyForCode("CLAN_LOGO_LOCKED"))
        assertNotNull(protocolTextKeyForCode("INVALID_CLAN_LOGO"))
    }

    @Test fun `legacy leaderboard payloads keep missing clan identity optional`() {
        val player = ProtocolJson.decodeFromString<LeaderboardEntrySnapshot>("""{"rank":1,"displayName":"Player","playerCode":"PLAYER","wins":1,"totalMatches":1,"highestScore":50}""")
        assertNull(player.clanId)
        assertNull(player.clanName)
        assertNull(player.clanLogoId)
        val clan = ProtocolJson.decodeFromString<ClanLeaderboardEntrySnapshot>("""{"rank":1,"clanId":"clan","clanName":"Clan","totalElo":1000,"memberCount":1}""")
        assertNull(clan.logoId)
    }
}

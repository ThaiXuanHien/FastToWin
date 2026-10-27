package com.hienthai.fastowin.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class ClanMemberSocialProtocolTest {
    @Test fun `public roster identity and authoritative presence survive wire round trip`() {
        val payload = """{"userId":"member","displayName":"Member","role":"MEMBER","trophies":1200,"playerCode":"ABC123","avatarId":"avatar","frameId":"frame_gold","presence":"PLAYING"}"""
        val member = ProtocolJson.decodeFromString<ClanMemberSnapshot>(payload)
        val encoded = ProtocolJson.parseToJsonElement(ProtocolJson.encodeToString(member)).jsonObject
        assertEquals("ABC123", encoded["playerCode"]?.jsonPrimitive?.content)
        assertEquals("avatar", encoded["avatarId"]?.jsonPrimitive?.content)
        assertEquals("frame_gold", encoded["frameId"]?.jsonPrimitive?.content)
        assertEquals("PLAYING", encoded["presence"]?.jsonPrimitive?.content)
        assertFalse(encoded.containsKey("progression"))
        assertFalse(encoded.containsKey("recentMatches"))
    }

    @Test fun `presence marker extends the existing clan message without a new discriminator`() {
        val payload = """{"type":"clan_info_data","clan":{"id":"clan","name":"Clan","description":"","ownerId":"owner","members":[],"trophies":0},"isPresenceRefresh":true}"""
        val message = ProtocolJson.decodeFromString<ServerMessage>(payload)
        val encoded = ProtocolJson.parseToJsonElement(ProtocolJson.encodeToString(message)).jsonObject
        assertEquals("clan_info_data", encoded["type"]?.jsonPrimitive?.content)
        assertEquals(true, encoded["isPresenceRefresh"]?.jsonPrimitive?.boolean)
    }
}

package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class RewardEventProtocolTest {
    private val legacyJson=Json { ignoreUnknownKeys=true }
    @Serializable private data class Legacy(val goldMultiplier:Int=1,val xpMultiplier:Int=1)
    @Test fun omittedFieldsRemainNeutralAndOldReadersIgnoreAddedMetadata() {
        assertEquals(MatchRewardEventSnapshot(),Json.decodeFromString<MatchRewardEventSnapshot>("{}"))
        assertEquals(MatchRewardBreakdown(),Json.decodeFromString<MatchRewardBreakdown>("{}"))
        val source=RewardEventSourceSnapshot("a",2,"Tết","Tet",1000,2000,2,1)
        val value=MatchRewardEventSnapshot(1500,2,1,listOf(source))
        val encoded=Json.encodeToString(value)
        assertEquals(value,Json.decodeFromString<MatchRewardEventSnapshot>(encoded))
        assertEquals(Legacy(2,1),legacyJson.decodeFromString<Legacy>(encoded))
    }
}

package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.*
import kotlin.test.*

class RewardEventGameStateTest {
    @Test fun `zero leave casual base and neutral tournament server amounts survive expired source`() {
        val source = RewardEventSourceSnapshot("old",2,"Sự kiện đã kết thúc","Ended event",0,1000,2,2)
        for ((matchType,tournament,breakdown) in listOf(
            Triple(MatchType.RANKED,null,MatchRewardBreakdown(sources=listOf(source))),
            Triple(MatchType.CASUAL,null,MatchRewardBreakdown(50,50,100,10,10,20,listOf(source))),
            Triple(MatchType.CASUAL,"tournament",MatchRewardBreakdown(50,0,50,10,0,10))
        )) {
            val game = GameSnapshot("room",roomName="room",hostId="p",players=emptyList(),gameMode=ProtocolGameMode.ORDER,
                phase=RoomPhase.FINISHED,matchType=matchType,tournamentId=tournament,rewardBreakdowns=mapOf("p" to breakdown))
            val state = GameState().applyAuthoritativeSnapshot(game,"p")
            assertEquals(breakdown,state.rewardBreakdowns["p"])
        }
    }
    @Test fun `completed snapshot keeps exact server amounts without client multiplier arithmetic`() {
        val breakdown = MatchRewardBreakdown(100,100,200,30,30,60)
        val game = GameSnapshot(roomId="room",roomName="room",hostId="p",players=emptyList(),numbers=emptyList(),currentTarget=1,gameMode=ProtocolGameMode.ORDER,phase=RoomPhase.FINISHED,eventRewards=MatchRewardEventSnapshot(1000,2,2),rewardBreakdowns=mapOf("p" to breakdown))
        val state = GameState().applyAuthoritativeSnapshot(game,"p")
        assertEquals(200,state.rewardBreakdowns["p"]?.totalGold)
        assertEquals(60,state.rewardBreakdowns["p"]?.totalXp)
        assertEquals(1000,state.eventRewards?.capturedAtEpochMillis)
        val legacy = state.applyAuthoritativeSnapshot(game.copy(eventRewards=null,rewardBreakdowns=emptyMap()),"p")
        assertNull(legacy.eventRewards)
        assertTrue(legacy.rewardBreakdowns.isEmpty())
    }
}

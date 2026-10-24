package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.moderation.*
import kotlin.test.*

class UgcSanitizerTest {
    private val player = "11111111-1111-1111-1111-111111111111"
    private val room = "22222222-2222-2222-2222-222222222222"
    @Test fun `nested room players lose suppressed name and avatar without changing score`() {
        val game = GameSnapshot(roomId=room,roomName="Room",hostId=player,gameMode=ProtocolGameMode.ORDER,
            phase=RoomPhase.WAITING,players=listOf(PlayerSnapshot(player,"Unsafe",42,avatarId="avatar-url")))
        val result = assertIs<ServerMessage.SessionReady>(sanitizeContent(ServerMessage.SessionReady(player,"token",game), listOf(
            ContentSuppression(UgcTargetType.PLAYER_PROFILE,player,contentRevision("Unsafe")),
            ContentSuppression(UgcTargetType.PLAYER_AVATAR,player,"1"))))
        val projected = assertNotNull(result.currentGame).players.single()
        assertEquals("Player", projected.name)
        assertEquals(MODERATED_AVATAR_ID,projected.avatarId)
        assertEquals(42,projected.score)
    }
    @Test fun `new profile revision remains visible while old historical name is removed`() {
        val profile = PlayerProfileSnapshot(player,"Changed","code", recentMatches=listOf(
            MatchHistorySnapshot("match","Old room",ProtocolGameMode.ORDER,"Unsafe",1,2,MatchHistoryOutcome.LOSS,1,
                roomId=room,opponentUserId=player)))
        val result = assertIs<ServerMessage.ProfileData>(sanitizeContent(ServerMessage.ProfileData(profile),listOf(
            ContentSuppression(UgcTargetType.PLAYER_PROFILE,player,contentRevision("Unsafe")),
            ContentSuppression(UgcTargetType.ROOM,room,contentRevision("Old room")))))
        assertEquals("Changed",result.profile.displayName)
        assertEquals("Player",result.profile.recentMatches.single().opponentName)
        assertEquals("Room",result.profile.recentMatches.single().roomName)
    }
}

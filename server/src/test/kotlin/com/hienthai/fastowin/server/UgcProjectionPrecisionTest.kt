package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.moderation.*
import kotlin.test.*

class UgcProjectionPrecisionTest {
    @Test fun `unrelated legacy history names remain visible`() {
        val profile=PlayerProfileSnapshot("user","Safe","code",recentMatches=listOf(
            MatchHistorySnapshot("match","Safe room",ProtocolGameMode.ORDER,"Safe player",1,2,MatchHistoryOutcome.LOSS,1)))
        val message=ServerMessage.ProfileData(profile)
        assertEquals(message,sanitizeContent(message,listOf(
            ContentSuppression(UgcTargetType.PLAYER_PROFILE,"other",contentRevision("Removed player")),
            ContentSuppression(UgcTargetType.ROOM,"other",contentRevision("Removed room")))))
    }
    @Test fun `clan display fingerprint masks profile name and summary without suppressing different new name`() {
        val removal=ContentSuppression(UgcTargetType.CLAN,"clan",contentRevision("Removed clan\nDescription"),nameRevision=contentRevision("Removed clan"))
        val profile=PlayerProfileSnapshot("user","Safe","code",clanId="clan",clanName="Removed clan")
        assertEquals("Clan",assertIs<ServerMessage.ProfileData>(sanitizeContent(ServerMessage.ProfileData(profile),listOf(removal))).profile.clanName)
        val summary=ClanSummarySnapshot("clan","Removed clan",1,50,0)
        assertEquals("Clan",assertIs<ServerMessage.ClanListData>(sanitizeContent(ServerMessage.ClanListData(listOf(summary)),listOf(removal))).clans.single().name)
        val changed=summary.copy(name="Changed clan")
        assertEquals(changed,assertIs<ServerMessage.ClanListData>(sanitizeContent(ServerMessage.ClanListData(listOf(changed)),listOf(removal))).clans.single())
    }
    @Test fun `notification fingerprints preserve unrelated player arguments`() {
        val notification=NotificationSnapshot("n",NotificationKind.FRIEND_REQUEST,"Friend","Safe player invited you",1,
            destination=NotificationDestination.FRIENDS,messageArgs=mapOf("player" to "Safe player"))
        val message=ServerMessage.NotificationsData(listOf(notification))
        assertEquals(message,sanitizeContent(message,listOf(ContentSuppression(UgcTargetType.PLAYER_PROFILE,"removed",contentRevision("Removed player")))))
    }
}

package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.moderation.*
import kotlin.test.*

class UgcProjectionPrecisionTest {
    @Test fun `trusted notification IDs distinguish players rooms and clans with identical names`() {
        val removed="11111111-1111-1111-1111-111111111111"
        val other="22222222-2222-2222-2222-222222222222"
        val suppression=listOf(ContentSuppression(UgcTargetType.PLAYER_PROFILE,removed,contentRevision("Alex")),
            ContentSuppression(UgcTargetType.ROOM,removed,contentRevision("Arena")),
            ContentSuppression(UgcTargetType.CLAN,removed,contentRevision("Group\nDescription"),nameRevision=contentRevision("Group")))
        val notification=NotificationSnapshot("n",NotificationKind.ROOM_INVITATION,"Invitation","Alex invited you to Arena",1,
            destination=NotificationDestination.FRIENDS,messageArgs=mapOf("player" to "Alex","playerId" to other,"room" to "Arena","roomId" to other,"clan" to "Group","clanId" to other))
        val message=ServerMessage.NotificationsData(listOf(notification))
        assertEquals(message,sanitizeContent(message,suppression))
        for((name,reference,neutral) in listOf(Triple("player","playerId","Player"),Triple("room","roomId","Room"),Triple("clan","clanId","Clan"))) {
            val targeted=notification.copy(messageArgs=notification.messageArgs+(reference to removed))
            val actual=assertIs<ServerMessage.NotificationsData>(sanitizeContent(ServerMessage.NotificationsData(listOf(targeted)),suppression)).notifications.single()
            assertEquals(neutral,actual.messageArgs[name])
            notification.messageArgs.filterKeys {it!=name}.forEach {(key,value)->if(key!=reference)assertEquals(value,actual.messageArgs[key])}
        }
    }
    @Test fun `legacy notification without target IDs still uses exact content fingerprint`() {
        val notification=NotificationSnapshot("n",NotificationKind.FRIEND_REQUEST,"Invitation","Alex invited you",1,
            destination=NotificationDestination.FRIENDS,messageArgs=mapOf("player" to "Alex"))
        val actual=assertIs<ServerMessage.NotificationsData>(sanitizeContent(ServerMessage.NotificationsData(listOf(notification)),
            listOf(ContentSuppression(UgcTargetType.PLAYER_PROFILE,"11111111-1111-1111-1111-111111111111",contentRevision("Alex"))))).notifications.single()
        assertEquals("Player",actual.messageArgs["player"])
    }
    @Test fun `present malformed trusted ID never falls back to unrelated name suppression`() {
        val notification=NotificationSnapshot("n",NotificationKind.FRIEND_REQUEST,"Invitation","Alex invited you",1,
            destination=NotificationDestination.FRIENDS,messageArgs=mapOf("player" to "Alex","playerId" to "invalid"))
        val message=ServerMessage.NotificationsData(listOf(notification))
        assertEquals(message,sanitizeContent(message,listOf(ContentSuppression(UgcTargetType.PLAYER_PROFILE,"11111111-1111-1111-1111-111111111111",contentRevision("Alex")))))
    }

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

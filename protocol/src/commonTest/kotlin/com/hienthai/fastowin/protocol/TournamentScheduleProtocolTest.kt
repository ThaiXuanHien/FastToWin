package com.hienthai.fastowin.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

class TournamentScheduleProtocolTest {
    @Test
    fun `invitation defaults preserve old clients and scheduled disclosure survives round trip`() {
        val old = ProtocolJson.decodeFromString<TournamentInvitationSnapshot>("""{"invitationId":"invite","tournamentId":"cup","tournamentName":"Manual","hostPlayerId":"host","hostDisplayName":"Host","gameMode":"ORDER","expiresAtEpochMillis":1000}""")
        assertNull(old.schedule)
        assertNull(old.serverTimeEpochMillis)
        assertEquals(0, old.entryFee)
        val scheduled = old.copy(entryFee = 100,
            schedule = TournamentScheduleSnapshot(2_000_000, "UTC", 1_100_000, 1_100_000), serverTimeEpochMillis = 1_000_000)
        assertEquals(scheduled, ProtocolJson.decodeFromString<TournamentInvitationSnapshot>(ProtocolJson.encodeToString(scheduled)))
    }

    @Test
    fun `old manual payloads and snapshots default scheduled fields without breaking compatibility`() {
        val command = ProtocolJson.decodeFromString<ClientMessage>("""{"type":"create_tournament","name":"Manual","gameMode":"ORDER"}""") as ClientMessage.CreateTournament
        assertNull(command.schedule)
        val snapshot = ProtocolJson.decodeFromString<TournamentSnapshot>("""{"tournamentId":"cup","name":"Manual","hostPlayerId":"host","gameMode":"ORDER","phase":"LOBBY","createdAtEpochMillis":1000,"players":[{"playerId":"host","displayName":"Host"}]}""")
        assertNull(snapshot.schedule)
        assertNull(snapshot.serverTimeEpochMillis)
        assertTrue(snapshot.recordedPlayers.isEmpty())
        assertFalse(snapshot.players.single().isCheckedIn)
        assertTrue(isCompatibleProtocolVersion(38))
        assertTrue(isCompatibleProtocolVersion(43))
        assertTrue(isCompatibleProtocolVersion(44))
        assertFalse(isCompatibleProtocolVersion(45))
    }

    @Test
    fun `scheduled snapshot round trip retains checked in and departed membership`() {
        val schedule = TournamentScheduleSnapshot(2_000_000, "Asia/Bangkok", 1_100_000, 1_100_000)
        val host = TournamentPlayerSnapshot("host", "Host", isHost = true, isCheckedIn = true)
        val leaver = TournamentPlayerSnapshot("leaver", "Leaver")
        val snapshot = TournamentSnapshot("cup", "Scheduled", "host", ProtocolGameMode.ORDER, TournamentPhase.LOBBY,
            players = listOf(host), createdAtEpochMillis = 1_000_000, schedule = schedule,
            serverTimeEpochMillis = 1_100_000, recordedPlayers = listOf(host, leaver))
        val restored = ProtocolJson.decodeFromString<TournamentSnapshot>(ProtocolJson.encodeToString(snapshot))
        assertEquals(snapshot, restored)
        assertEquals(ClientMessage.CheckInTournament("cup"), ProtocolJson.decodeFromString<ClientMessage>(
            ProtocolJson.encodeToString<ClientMessage>(ClientMessage.CheckInTournament("cup"))))
        val request = ClientMessage.CreateTournament("Scheduled", ProtocolGameMode.ORDER,
            schedule = TournamentScheduleRequest(2_000_000, "Asia/Bangkok"))
        assertEquals(request, ProtocolJson.decodeFromString<ClientMessage>(ProtocolJson.encodeToString<ClientMessage>(request)))
    }
}

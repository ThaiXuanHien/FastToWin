package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.PlayerSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PostgresActiveRoomRepositoryTest {
    @Test
    fun `active room snapshot round trips through postgres`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val repository = PostgresActiveRoomRepository(dataSource)
            val roomId = UUID.randomUUID().toString()
            val otherRoomId = UUID.randomUUID().toString()
            val hostId = UUID.randomUUID().toString()
            val guestId = UUID.randomUUID().toString()
            val stored = StoredActiveRoom(
                roomId = roomId,
                roomName = "Restart integration room",
                host = StoredActivePlayer(hostId, "Host", isAccount = false),
                guest = StoredActivePlayer(guestId, "Guest", isAccount = true),
                passwordSalt = byteArrayOf(1, 2, 3),
                passwordHash = byteArrayOf(4, 5, 6),
                gameMode = ProtocolGameMode.ORDER,
                phase = RoomPhase.PLAYING,
                numbers = (1..50).toList().reversed(),
                selectedNumbers = listOf(1, 2),
                currentTarget = 3,
                scores = mapOf(hostId to 10, guestId to 10),
                sequence = 3,
                startedAtEpochMillis = 1_000L,
                processedRequests = mapOf(
                    "wrong-request" to ServerMessage.Error(
                        code = "WRONG_NUMBER",
                        message = "Wrong number",
                        requestId = "wrong-request"
                    ),
                    "accepted-request" to ServerMessage.GameStateUpdated(
                        game = GameSnapshot(
                            roomId = roomId,
                            roomName = "Restart integration room",
                            hostId = hostId,
                            gameMode = ProtocolGameMode.ORDER,
                            phase = RoomPhase.PLAYING,
                            players = listOf(
                                PlayerSnapshot(hostId, "Host", 10),
                                PlayerSnapshot(guestId, "Guest", 10)
                            ),
                            numbers = (1..50).toList().reversed(),
                            selectedNumbers = listOf(1, 2),
                            currentTarget = 3,
                            sequence = 3,
                            startedAtEpochMillis = 1_000L
                        ),
                        acceptedNumber = 2,
                        selectedByPlayerId = guestId
                    )
                ),
                selectionEvents = listOf(
                    MatchSelectionEvent(
                        playerId = hostId,
                        requestId = "accepted-1",
                        number = 1,
                        expectedNumber = 1,
                        result = SelectionResult.ACCEPTED,
                        occurredAtMillis = 1_100L,
                        sequence = 1
                    )
                )
            )

            try {
                repository.save(stored)

                val restored = repository.loadAll().single()
                assertEquals(roomId, restored.roomId)
                assertEquals(stored.roomName, restored.roomName)
                assertEquals(stored.host, restored.host)
                assertEquals(stored.guest, restored.guest)
                assertContentEquals(stored.passwordSalt, restored.passwordSalt)
                assertContentEquals(stored.passwordHash, restored.passwordHash)
                assertEquals(stored.numbers, restored.numbers)
                assertEquals(stored.selectedNumbers, restored.selectedNumbers)
                assertEquals(stored.currentTarget, restored.currentTarget)
                assertEquals(stored.scores, restored.scores)
                assertEquals(stored.sequence, restored.sequence)
                assertEquals(stored.selectionEvents, restored.selectionEvents)
                assertEquals(
                    "WRONG_NUMBER",
                    assertIs<ServerMessage.Error>(restored.processedRequests.getValue("wrong-request")).code
                )
                assertEquals(
                    3,
                    assertIs<ServerMessage.GameStateUpdated>(
                        restored.processedRequests.getValue("accepted-request")
                    ).game.currentTarget
                )

                repository.save(stored.copy(currentTarget = 4))
                assertEquals(4, repository.loadAll().single().currentTarget)

                repository.save(stored.copy(roomId = otherRoomId, roomName = "Other room"))
                assertEquals(setOf(roomId, otherRoomId), repository.loadAll().map { it.roomId }.toSet())

                repository.delete(roomId)
                assertEquals(otherRoomId, repository.loadAll().single().roomId)
            } finally {
                repository.delete(roomId)
                repository.delete(otherRoomId)
            }
        }
    }
}

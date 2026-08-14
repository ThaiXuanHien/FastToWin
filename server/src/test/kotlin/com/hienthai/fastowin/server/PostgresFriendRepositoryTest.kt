package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PostgresFriendRepositoryTest {
    @Test
    fun `friend requests persist accept and cascade on account deletion`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val auth = AuthenticationService(
                PostgresAuthRepository(dataSource),
                PasswordHasher(iterations = 1_000),
                nowMillis = { NOW }
            )
            val firstEmail = "friend-a-${UUID.randomUUID()}@example.com"
            val secondEmail = "friend-b-${UUID.randomUUID()}@example.com"
            val first = assertIs<AuthResult.Success>(auth.register(firstEmail, PASSWORD, "Friend A", "android")).session
            val second = assertIs<AuthResult.Success>(auth.register(secondEmail, PASSWORD, "Friend B", "ios")).session
            try {
                val playerCodes = dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT user_id, player_code FROM profiles WHERE user_id IN (?, ?)"
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(first.userId))
                        statement.setObject(2, UUID.fromString(second.userId))
                        statement.executeQuery().use { result ->
                            buildMap {
                                while (result.next()) {
                                    put(result.getObject("user_id", UUID::class.java).toString(), result.getString("player_code"))
                                }
                            }
                        }
                    }
                }
                val firstCode = playerCodes.getValue(first.userId)
                val secondCode = playerCodes.getValue(second.userId)
                val friends = PostgresFriendRepository(dataSource)
                assertIs<FriendRequestResult.SelfRequest>(
                    friends.sendRequest(first.userId, firstCode, NOW)
                )
                val sent = assertIs<FriendRequestResult.Success>(
                    friends.sendRequest(first.userId, secondCode.lowercase(), NOW)
                )
                assertEquals(second.userId, sent.recipientId)
                val incoming = friends.load(second.userId).incomingRequests.single()
                assertEquals(first.userId, incoming.userId)
                assertIs<FriendRequestResult.AlreadyExists>(
                    friends.sendRequest(first.userId, secondCode, NOW + 1)
                )
                assertIs<FriendCancellationResult.NotFound>(
                    friends.cancelRequest(second.userId, incoming.requestId)
                )
                assertIs<FriendCancellationResult.Success>(
                    friends.cancelRequest(first.userId, incoming.requestId)
                )
                assertTrue(friends.load(first.userId).outgoingRequests.isEmpty())
                assertTrue(friends.load(second.userId).incomingRequests.isEmpty())
                assertIs<FriendRequestResult.Success>(
                    friends.sendRequest(first.userId, secondCode, NOW + 2)
                )
                val resentIncoming = friends.load(second.userId).incomingRequests.single()
                assertIs<FriendResponseResult.Success>(
                    friends.respond(second.userId, resentIncoming.requestId, accept = true, NOW + 3)
                )
                assertTrue(friends.areFriends(first.userId, second.userId))
                assertEquals("Friend B", friends.load(first.userId).friends.single().displayName)

                assertIs<SocialMutationResult.Success>(friends.removeFriend(first.userId, second.userId))
                assertFalse(friends.areFriends(first.userId, second.userId))
                assertIs<FriendRequestResult.Success>(
                    friends.sendRequest(first.userId, secondCode, NOW + 4)
                )
                val secondIncoming = friends.load(second.userId).incomingRequests.single()
                assertIs<FriendResponseResult.Success>(
                    friends.respond(second.userId, secondIncoming.requestId, accept = true, NOW + 5)
                )

                assertIs<SocialMutationResult.Success>(
                    friends.blockPlayer(first.userId, second.userId, NOW + 6)
                )
                assertFalse(friends.areFriends(first.userId, second.userId))
                assertEquals(second.userId, friends.load(first.userId).blockedPlayers.single().userId)
                assertTrue(friends.load(second.userId).blockedPlayers.isEmpty())
                assertIs<FriendRequestResult.Blocked>(
                    friends.sendRequest(second.userId, firstCode, NOW + 7)
                )

                assertIs<SocialMutationResult.Success>(friends.unblockPlayer(first.userId, second.userId))
                assertTrue(friends.load(first.userId).blockedPlayers.isEmpty())
                assertIs<FriendRequestResult.Success>(
                    friends.sendRequest(second.userId, firstCode, NOW + 8)
                )
                val firstIncoming = friends.load(first.userId).incomingRequests.single()
                assertIs<FriendResponseResult.Success>(
                    friends.respond(first.userId, firstIncoming.requestId, accept = true, NOW + 9)
                )
                assertTrue(friends.areFriends(first.userId, second.userId))

                val matchRepository = PostgresMatchResultRepository(dataSource)
                repeat(2) { index ->
                    val endedAt = NOW + 100L + index
                    matchRepository.save(CompletedMatch(
                        matchId = UUID.randomUUID().toString(),
                        roomName = "Recent players test",
                        gameMode = ProtocolGameMode.ORDER,
                        startedAtMillis = endedAt - 50L,
                        endedAtMillis = endedAt,
                        winnerPlayerId = first.userId,
                        players = listOf(
                            CompletedMatchPlayer(first.userId, "Friend A", 50, MatchOutcome.WIN),
                            CompletedMatchPlayer(second.userId, "Friend B", 40, MatchOutcome.LOSS)
                        )
                    ))
                }
                val recentPlayer = friends.load(first.userId).recentPlayers.single()
                assertEquals(second.userId, recentPlayer.userId)
                assertEquals("Friend B", recentPlayer.displayName)
                assertEquals(2, recentPlayer.matchesPlayed)
                assertEquals(NOW + 101L, recentPlayer.lastPlayedAtEpochMillis)

                assertIs<AccountActionResult.Success>(auth.deleteAccount(second.accessToken, PASSWORD))
                assertTrue(friends.load(first.userId).friends.isEmpty())
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE email_normalized IN (?, ?)").use { statement ->
                        statement.setString(1, firstEmail)
                        statement.setString(2, secondEmail)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    private companion object {
        const val PASSWORD = "strong-password-123"
        const val NOW = 1_900_000_000_000L
    }
}

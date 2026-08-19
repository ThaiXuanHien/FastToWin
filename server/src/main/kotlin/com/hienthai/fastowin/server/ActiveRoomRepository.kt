package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID

@Serializable
data class StoredActivePlayer(
    val playerId: String,
    val displayName: String,
    val isAccount: Boolean
)

@Serializable
data class StoredActiveRoom(
    val roomId: String,
    val matchId: String = roomId,
    val roomName: String,
    val host: StoredActivePlayer,
    val guest: StoredActivePlayer? = null,
    val guests: List<StoredActivePlayer> = emptyList(),
    val spectators: List<StoredActivePlayer> = emptyList(),
    val teamIds: Map<String, String> = emptyMap(),
    val passwordSalt: ByteArray? = null,
    val passwordHash: ByteArray? = null,
    val gameMode: ProtocolGameMode,
    val matchType: MatchType = MatchType.CASUAL,
    val phase: RoomPhase,
    val numbers: List<Int> = emptyList(),
    val selectedNumbers: List<Int> = emptyList(),
    val currentTarget: Int = 1,
    val scores: Map<String, Int> = emptyMap(),
    val sequence: Long = 0,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
    val resultQueued: Boolean = false,
    val readyPlayerIds: Set<String> = emptySet(),
    val rematchRequestedPlayerIds: Set<String> = emptySet(),
    val rematchExpiresAtEpochMillis: Long? = null,
    val processedRequests: Map<String, ServerMessage> = emptyMap(),
    val selectionEvents: List<MatchSelectionEvent> = emptyList(),
    val targetOrder: List<Int> = emptyList(),
    val selectedNumbersByPlayer: Map<String, List<Int>> = emptyMap(),
    val targetIndexes: Map<String, Int> = emptyMap(),
    val combos: Map<String, Int> = emptyMap(),
    val lives: Map<String, Int> = emptyMap(),
    val deadlinesAtEpochMillis: Map<String, Long> = emptyMap(),
    val finishedPlayerIds: Set<String> = emptySet(),
    val forcedWinnerId: String? = null
)

interface ActiveRoomRepository {
    suspend fun loadAll(): List<StoredActiveRoom>
    suspend fun save(room: StoredActiveRoom)
    suspend fun delete(roomId: String)
}

object NoOpActiveRoomRepository : ActiveRoomRepository {
    override suspend fun loadAll(): List<StoredActiveRoom> = emptyList()
    override suspend fun save(room: StoredActiveRoom) = Unit
    override suspend fun delete(roomId: String) = Unit
}

class InMemoryActiveRoomRepository : ActiveRoomRepository {
    private val storedRooms = linkedMapOf<String, StoredActiveRoom>()

    override suspend fun loadAll(): List<StoredActiveRoom> = storedRooms.values.toList()

    override suspend fun save(room: StoredActiveRoom) {
        storedRooms[room.roomId] = room
    }

    override suspend fun delete(roomId: String) {
        storedRooms.remove(roomId)
    }
}

class PostgresActiveRoomRepository(
    private val dataSource: HikariDataSource
) : ActiveRoomRepository {
    override suspend fun loadAll(): List<StoredActiveRoom> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT state_json::text FROM active_room_snapshots ORDER BY updated_at, room_id"
        ).use { statement ->
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(ProtocolJson.decodeFromString<StoredActiveRoom>(result.getString(1)))
                    }
                }
            }
        }
    }

    override suspend fun save(room: StoredActiveRoom) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO active_room_snapshots(room_id, state_json, updated_at)
                VALUES (?, CAST(? AS jsonb), CURRENT_TIMESTAMP)
                ON CONFLICT (room_id) DO UPDATE
                SET state_json = EXCLUDED.state_json,
                    updated_at = EXCLUDED.updated_at
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, UUID.fromString(room.roomId))
                statement.setString(2, ProtocolJson.encodeToString(room))
                statement.executeUpdate()
            }
        }
    }

    override suspend fun delete(roomId: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM active_room_snapshots WHERE room_id = ?").use { statement ->
                statement.setObject(1, UUID.fromString(roomId))
                statement.executeUpdate()
            }
        }
    }
}

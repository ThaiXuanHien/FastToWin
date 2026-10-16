package com.hienthai.fastowin.server.admin

import kotlinx.serialization.Serializable
import com.hienthai.fastowin.server.legacyFallback

@Serializable enum class AdminRole { OWNER, OPERATOR, SUPPORT }
@Serializable data class AdminIdentity(val id: String, val email: String, val role: AdminRole, val active: Boolean = true)
@Serializable data class AdminSessionResponse(val admin: AdminIdentity, val csrfToken: String)
@Serializable data class AdminLoginRequest(val email: String, val password: String)
@Serializable data class CreateAdminRequest(val email: String, val password: String, val role: AdminRole, val reason: String, val requestId: String)
@Serializable data class ChangeAdminAccessRequest(val role: AdminRole, val active: Boolean, val reason: String, val requestId: String)
@Serializable data class AdminError(val code: String, val message: String)
@Serializable data class AdminOk(val ok: Boolean = true)
@Serializable data class AdminPage<T>(val items: List<T>, val page: Int, val pageSize: Int, val total: Long)
@Serializable data class UserSummary(val id: String, val displayName: String, val email: String?, val accountType: String, val status: String, val createdAtEpochMillis: Long, val playerCode: String = "", val level: Int? = null)
@Serializable data class UserDetail(
    val id: String, val displayName: String, val email: String?, val accountType: String, val status: String,
    val createdAtEpochMillis: Long, val level: Int, val xp: Int, val gold: Int, val gems: Int, val elo: Int,
    val clanId: String?, val clanName: String?, val lastActivityAtEpochMillis: Long?, val playerCode: String = "",
    val totalMatches: Int? = null, val wins: Int? = null, val losses: Int? = null, val draws: Int? = null
) {
    fun summary() = UserSummary(id, displayName, email, accountType, status, createdAtEpochMillis, playerCode, level)
}
/** Profile population matches the directory, including DELETED accounts. Counts are decimal strings;
 * null means the storage cannot supply that metric. Matches are unique completed persisted matches.
 * Recorded results are participant totals, not unique match totals. Elo includes unplayed profiles
 * with the same default rating (1000) as player details. Clan count includes empty clans. */
data class PlayerOverviewStats(
    val totalPlayers: String?, val registeredPlayers: String?, val guestPlayers: String?, val playableAccounts: String?,
    val matchesToday: String?, val recordedParticipations: String?, val recordedWins: String?, val recordedLosses: String?,
    val recordedDraws: String?, val playedProfiles: String?, val clanCount: String?, val clanMembers: String?,
    val averageElo: String?, val maxElo: Int?
)
@Serializable data class PlayerOverviewResponse(
    val snapshotAt: Long, val todayFrom: Long, val todayUntil: Long,
    val totalPlayers: String?, val registeredPlayers: String?, val guestPlayers: String?, val playableAccounts: String?,
    val matchesToday: String?, val recordedParticipations: String?, val recordedWins: String?, val recordedLosses: String?,
    val recordedDraws: String?, val playedProfiles: String?, val clanCount: String?, val clanMembers: String?,
    val averageElo: String?, val maxElo: Int?
)
@Serializable data class MatchEntry(val id: String, val matchType: String, val outcome: String, val score: Int, val finishedAtEpochMillis: Long)
@Serializable data class WalletEntry(val id: String, val sourceType: String, val sourceId: String, val goldDelta: Int, val gemsDelta: Int, val xpDelta: Int, val createdAtEpochMillis: Long)
@Serializable data class AuditEntry(
    val id: String, val actorId: String?, val action: String, val targetId: String?, val requestId: String?, val reason: String?,
    val beforeSummary: String?, val afterSummary: String?, val createdAtEpochMillis: Long
)

class AdminException(val status: Int, val code: String, override val message: String) : RuntimeException(message)
internal fun invalid(): Nothing = throw AdminException(400, "INVALID_REQUEST", legacyFallback("Yêu cầu không hợp lệ."))
internal fun unauthorized(): Nothing = throw AdminException(401, "UNAUTHORIZED", legacyFallback("Phiên quản trị không hợp lệ hoặc đã hết hạn."))
internal fun forbidden(): Nothing = throw AdminException(403, "FORBIDDEN", legacyFallback("Bạn không có quyền thực hiện thao tác này."))
internal fun missing(): Nothing = throw AdminException(404, "NOT_FOUND", legacyFallback("Không tìm thấy dữ liệu."))
internal fun conflict(): Nothing = throw AdminException(409, "CONFLICT", legacyFallback("Yêu cầu xung đột với dữ liệu hiện tại."))
internal fun unavailable(): Nothing = throw AdminException(503, "STORAGE_UNAVAILABLE", legacyFallback("Kho dữ liệu người chơi chưa sẵn sàng."))

data class PageRequest(val page: Int = 0, val pageSize: Int = 25) {
    init { if (page < 0 || pageSize !in 1..100 || page.toLong() * pageSize > Int.MAX_VALUE) invalid() }
    val offset: Int get() = page * pageSize
    fun <T> result(items: List<T>, total: Long) = AdminPage(items, page, pageSize, total)
}
// Internal records deliberately have no serializer; only safe DTOs cross the HTTP boundary.
data class AdminAccount(val identity: AdminIdentity, val passwordHash: String)
data class AdminSession(val tokenHash: String, val adminId: String, val csrfHash: String, val expiresAtEpochMillis: Long, val revoked: Boolean = false)
data class AdminLogin(val token: String, val response: AdminSessionResponse)
data class AdminDeduplication(val fingerprint: String, val identity: AdminIdentity)

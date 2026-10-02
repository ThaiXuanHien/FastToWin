package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.PlayerProgression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class PostgresAdminRepository(private val dataSource: DataSource) : AdminRepository {
    override suspend fun <T> transaction(block: AdminTransaction.() -> T): T = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    // A single lock order serializes owner changes and authorization with revocation.
                    // This includes sensitive reads so their privilege check and audit form one snapshot.
                    connection.prepareStatement("SELECT pg_advisory_xact_lock(7421092187349)").use { it.execute() }
                    val result = JdbcAdminTransaction(connection).block()
                    connection.commit()
                    result
                } catch (error: Throwable) { connection.rollback(); throw error }
            }
        } catch (_: SQLException) { unavailable() }
    }
}

internal class JdbcAdminTransaction(private val connection: Connection) : AdminTransaction {
    private fun <T> rows(sql: String, values: List<Any?> = emptyList(), mapper: (ResultSet) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            statement.bind(values)
            statement.executeQuery().use { rs -> buildList { while (rs.next()) add(mapper(rs)) } }
        }
    private fun update(sql: String, vararg values: Any?) = connection.prepareStatement(sql).use { it.bind(values.toList()); it.executeUpdate() }
    private fun count(sql: String, values: List<Any?> = emptyList()) = rows(sql, values) { it.getLong(1) }.single()
    private fun accountRow(rs: ResultSet) = AdminAccount(AdminIdentity(rs.getString("id"), rs.getString("email_normalized"), AdminRole.valueOf(rs.getString("role")), rs.getBoolean("active")), rs.getString("password_hash"))
    override fun accountByEmail(email: String) = rows("SELECT id,email_normalized,password_hash,role,active FROM admin_accounts WHERE email_normalized=?", listOf(email), ::accountRow).singleOrNull()
    override fun account(id: String) = rows("SELECT id,email_normalized,password_hash,role,active FROM admin_accounts WHERE id=?", listOf(UUID.fromString(id)), ::accountRow).singleOrNull()
    override fun putAccount(account: AdminAccount) {
        update("""INSERT INTO admin_accounts(id,email_normalized,password_hash,role,active) VALUES (?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET role=EXCLUDED.role,active=EXCLUDED.active,updated_at=CURRENT_TIMESTAMP""",
            UUID.fromString(account.identity.id), account.identity.email, account.passwordHash, account.identity.role.name, account.identity.active)
    }
    override fun activeOwners() = count("SELECT count(*) FROM admin_accounts WHERE role='OWNER' AND active").toInt()
    override fun session(tokenHash: String) = rows("SELECT token_hash,admin_id,csrf_hash,expires_at,revoked FROM admin_sessions WHERE token_hash=?", listOf(tokenHash)) {
        AdminSession(it.getString("token_hash"), it.getString("admin_id"), it.getString("csrf_hash"), it.getTimestamp("expires_at").time, it.getBoolean("revoked"))
    }.singleOrNull()
    override fun putSession(session: AdminSession) {
        update("""INSERT INTO admin_sessions(token_hash,admin_id,csrf_hash,expires_at,revoked) VALUES (?,?,?,?,?)
            ON CONFLICT(token_hash) DO UPDATE SET revoked=EXCLUDED.revoked""", session.tokenHash, UUID.fromString(session.adminId), session.csrfHash, Timestamp(session.expiresAtEpochMillis), session.revoked)
    }
    override fun revokeSessions(adminId: String) { update("UPDATE admin_sessions SET revoked=TRUE WHERE admin_id=? AND NOT revoked", UUID.fromString(adminId)) }
    override fun deduplication(actorId: String, requestId: String) = rows("SELECT fingerprint,safe_result FROM admin_requests WHERE actor_id=? AND request_id=?", listOf(UUID.fromString(actorId),requestId)) {
        AdminDeduplication(it.getString("fingerprint"), Json.decodeFromString<AdminIdentity>(it.getString("safe_result")))
    }.singleOrNull()
    override fun putDeduplication(actorId: String, requestId: String, value: AdminDeduplication) {
        update("INSERT INTO admin_requests(actor_id,request_id,fingerprint,safe_result) VALUES (?,?,?,?)", UUID.fromString(actorId), requestId, value.fingerprint, Json.encodeToString(value.identity))
    }
    override fun appendAudit(entry: AuditEntry) {
        update("INSERT INTO admin_audit(id,actor_id,action,target_id,request_id,reason,before_summary,after_summary,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
            UUID.fromString(entry.id), entry.actorId?.let(UUID::fromString), entry.action, entry.targetId?.let(UUID::fromString), entry.requestId, entry.reason, entry.beforeSummary, entry.afterSummary, Timestamp(entry.createdAtEpochMillis))
    }
    override fun accounts(page: PageRequest): AdminPage<AdminIdentity> {
        val items = rows("SELECT id,email_normalized,role,active FROM admin_accounts ORDER BY id LIMIT ? OFFSET ?", listOf(page.pageSize,page.offset)) {
            AdminIdentity(it.getString("id"),it.getString("email_normalized"),AdminRole.valueOf(it.getString("role")),it.getBoolean("active"))
        }
        return page.result(items, count("SELECT count(*) FROM admin_accounts"))
    }
    override fun audit(page: PageRequest): AdminPage<AuditEntry> {
        val items = rows("SELECT id,actor_id,action,target_id,request_id,reason,before_summary,after_summary,created_at FROM admin_audit ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", listOf(page.pageSize,page.offset)) {
            AuditEntry(it.getString("id"),it.getString("actor_id"),it.getString("action"),it.getString("target_id"),it.getString("request_id"),it.getString("reason"),it.getString("before_summary"),it.getString("after_summary"),it.getTimestamp("created_at").time)
        }
        return page.result(items, count("SELECT count(*) FROM admin_audit"))
    }
    override fun users(query: String, includeEmail: Boolean, page: PageRequest): AdminPage<UserSummary> {
        val id = runCatching { UUID.fromString(query) }.getOrNull()
        val pattern = "%${query.replace("!", "!!").replace("%", "!%").replace("_", "!_")}%"
        val condition: String
        val values: List<Any?>
        when {
            query.isEmpty() -> { condition="TRUE"; values=emptyList() }
            id != null -> { condition="u.id=?"; values=listOf(id) }
            includeEmail -> { condition="(p.display_name ILIKE ? ESCAPE '!' OR u.email_normalized ILIKE ? ESCAPE '!')"; values=listOf(pattern,pattern) }
            else -> { condition="p.display_name ILIKE ? ESCAPE '!'"; values=listOf(pattern) }
        }
        val from = "FROM users u JOIN profiles p ON p.user_id=u.id WHERE $condition"
        val items = rows("SELECT u.id,p.display_name,u.email_normalized,u.account_type,u.status,u.created_at $from ORDER BY u.created_at DESC,u.id LIMIT ? OFFSET ?", values + listOf(page.pageSize,page.offset)) {
            UserSummary(it.getString("id"),it.getString("display_name"),it.getString("email_normalized"),it.getString("account_type"),it.getString("status"),it.getTimestamp("created_at").time)
        }
        return page.result(items,count("SELECT count(*) $from",values))
    }
    override fun user(id: String): UserDetail? = rows("""SELECT u.id,p.display_name,u.email_normalized,u.account_type,u.status,u.created_at,
        COALESCE(ps.experience_points,0) AS xp,COALESCE(ps.gold,0) AS gold,COALESCE(ps.gems,0) AS gems,COALESCE(ps.elo_rating,1000) AS elo,
        c.id AS clan_id,c.name AS clan_name,(SELECT max(s.last_seen_at) FROM sessions s WHERE s.user_id=u.id) AS last_activity
        FROM users u JOIN profiles p ON p.user_id=u.id LEFT JOIN player_stats ps ON ps.user_id=u.id
        LEFT JOIN clan_members cm ON cm.user_id=u.id LEFT JOIN clans c ON c.id=cm.clan_id WHERE u.id=?""", listOf(UUID.fromString(id))) {
        val xp=it.getInt("xp")
        UserDetail(it.getString("id"),it.getString("display_name"),it.getString("email_normalized"),it.getString("account_type"),it.getString("status"),it.getTimestamp("created_at").time,
            PlayerProgression.levelForExperience(xp),xp,it.getInt("gold"),it.getInt("gems"),it.getInt("elo"),it.getString("clan_id"),it.getString("clan_name"),it.getTimestamp("last_activity")?.time)
    }.singleOrNull()
    override fun matches(id: String, page: PageRequest): AdminPage<MatchEntry> {
        val from="FROM match_players mp JOIN matches m ON m.id=mp.match_id WHERE mp.user_id=?"
        val values=listOf(UUID.fromString(id))
        val items=rows("SELECT m.id,m.match_type,mp.outcome,mp.score,m.ended_at $from ORDER BY m.ended_at DESC,m.id LIMIT ? OFFSET ?",values+listOf(page.pageSize,page.offset)) {
            MatchEntry(it.getString("id"),it.getString("match_type"),it.getString("outcome"),it.getInt("score"),it.getTimestamp("ended_at").time)
        }
        return page.result(items,count("SELECT count(*) $from",values))
    }
    override fun wallet(id: String, giftsOnly: Boolean, page: PageRequest): AdminPage<WalletEntry> {
        val from="FROM wallet_transactions WHERE user_id=?" + if (giftsOnly) " AND source_type IN ('ADMIN_GIFT','COMPENSATION')" else ""
        val values=listOf(UUID.fromString(id))
        val items=rows("SELECT id,source_type,source_id,gold_delta,gems_delta,xp_delta,created_at $from ORDER BY created_at DESC,id LIMIT ? OFFSET ?",values+listOf(page.pageSize,page.offset)) {
            WalletEntry(it.getString("id"),it.getString("source_type"),it.getString("source_id"),it.getInt("gold_delta"),it.getInt("gems_delta"),it.getInt("xp_delta"),it.getTimestamp("created_at").time)
        }
        return page.result(items,count("SELECT count(*) $from",values))
    }
}
private fun PreparedStatement.bind(values: List<Any?>) { values.forEachIndexed { index, value -> setObject(index+1,value) } }

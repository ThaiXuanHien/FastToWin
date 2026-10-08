package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gifts.GiftCampaign
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

internal class JdbcCompensationStore(private val connection: Connection) : CompensationStore {
    private fun id(value: String) = UUID.fromString(value)
    private fun <T> rows(sql: String, values: List<Any?> = emptyList(), read: (ResultSet) -> T): List<T> = connection.prepareStatement(sql).use { s ->
        values.forEachIndexed { i, v -> s.setObject(i + 1, v) }
        s.executeQuery().use { r -> buildList { while (r.next()) add(read(r)) } }
    }
    private fun write(sql: String, vararg values: Any?) {
        try { connection.prepareStatement(sql).use { s -> values.forEachIndexed { i, v -> s.setObject(i + 1, v) }; s.executeUpdate() } }
        catch (e: SQLException) { if (e.sqlState == "23505") conflict() else throw e }
    }
    override fun incident(id: String, lock: Boolean) = rows("SELECT payload FROM compensation_incidents WHERE id=?" + if (lock) " FOR UPDATE" else "", listOf(id(id))) { Json.decodeFromString<CompensationIncident>(it.getString(1)) }.singleOrNull()
    override fun incidentByKey(key: String) = rows("SELECT payload FROM compensation_incidents WHERE incident_key=?", listOf(key)) { Json.decodeFromString<CompensationIncident>(it.getString(1)) }.singleOrNull()
    override fun incidents(page: PageRequest) = CompensationPage(
        rows("SELECT payload FROM compensation_incidents ORDER BY id DESC LIMIT ? OFFSET ?", listOf(page.pageSize, page.offset)) { Json.decodeFromString<CompensationIncident>(it.getString(1)) },
        page.page, page.pageSize, rows("SELECT count(*) FROM compensation_incidents") { it.getString(1) }.single())
    override fun putIncident(incident: CompensationIncident) {
        if (incident(incident.id)?.incidentKey?.let { it != incident.incidentKey } == true) conflict()
        write("""INSERT INTO compensation_incidents(id,incident_key,version,responsible_admin_id,payload) VALUES (?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET version=excluded.version,responsible_admin_id=excluded.responsible_admin_id,payload=excluded.payload""",
            id(incident.id), incident.incidentKey, incident.version, id(incident.responsibleAdminId), Json.encodeToString(incident))
    }
    override fun round(campaignId: String) = rows("SELECT payload FROM compensation_rounds WHERE campaign_id=?", listOf(id(campaignId))) { Json.decodeFromString<CompensationRound>(it.getString(1)) }.singleOrNull()
    override fun rounds(incidentId: String, page: PageRequest) = CompensationPage(
        rows("SELECT payload FROM compensation_rounds WHERE incident_id=? ORDER BY round_number DESC LIMIT ? OFFSET ?", listOf(id(incidentId), page.pageSize, page.offset)) { Json.decodeFromString<CompensationRound>(it.getString(1)) },
        page.page, page.pageSize, rows("SELECT count(*) FROM compensation_rounds WHERE incident_id=?", listOf(id(incidentId))) { it.getString(1) }.single())
    override fun putRound(round: CompensationRound) {
        val context = rows("SELECT payload FROM admin_gift_campaigns WHERE id=?", listOf(id(round.campaignId))) { Json.decodeFromString<GiftCampaign>(it.getString(1)).compensation }.singleOrNull() ?: conflict()
        if (context.incidentId != round.incidentId || context.roundNumber != round.roundNumber) conflict()
        val old = round(round.campaignId)
        if (old != null && (old.incidentId != round.incidentId || old.roundNumber != round.roundNumber)) conflict()
        if (old == null && hasDraftRound(round.incidentId) && rows("SELECT state FROM admin_gift_campaigns WHERE id=?", listOf(id(round.campaignId))) { it.getString(1) }.singleOrNull() == "DRAFT") conflict()
        write("""INSERT INTO compensation_rounds(campaign_id,incident_id,round_number,payload) VALUES (?,?,?,?)
            ON CONFLICT(campaign_id) DO UPDATE SET payload=excluded.payload""", id(round.campaignId), id(round.incidentId), round.roundNumber, Json.encodeToString(round))
    }
    override fun maximumRoundNumber(incidentId: String) = rows("SELECT coalesce(max(round_number),0) FROM compensation_rounds WHERE incident_id=?", listOf(id(incidentId))) { it.getInt(1) }.single()
    override fun hasDraftRound(incidentId: String) = rows("SELECT EXISTS(SELECT 1 FROM compensation_rounds r JOIN admin_gift_campaigns c ON c.id=r.campaign_id WHERE r.incident_id=? AND c.state='DRAFT')", listOf(id(incidentId))) { it.getBoolean(1) }.single()
    override fun hasPublishedRound(incidentId: String) = rows("SELECT EXISTS(SELECT 1 FROM compensation_rounds r JOIN admin_gift_campaigns c ON c.id=r.campaign_id WHERE r.incident_id=? AND (c.payload::jsonb ->> 'publishedAtEpochMillis') IS NOT NULL)", listOf(id(incidentId))) { it.getBoolean(1) }.single()
    override fun replay(actorId: String, requestId: String) = rows("SELECT fingerprint,response_type,response_json FROM compensation_replays WHERE actor_id=? AND request_id=?", listOf(id(actorId), requestId)) { CompensationReplay(it.getString(1), it.getString(2), it.getString(3)) }.singleOrNull()
    override fun putReplay(actorId: String, requestId: String, replay: CompensationReplay) = write("INSERT INTO compensation_replays(actor_id,request_id,fingerprint,response_type,response_json) VALUES (?,?,?,?,?)", id(actorId), requestId, replay.fingerprint, replay.responseType, replay.responseJson)
    override fun previousClaims(incidentId: String, beforeRoundNumber: Int, selectedUserIds: List<String>): CompensationPreviousClaims {
        val selected = connection.createArrayOf("uuid", selectedUserIds.distinct().map(::id).toTypedArray())
        return try { rows("""SELECT count(DISTINCT g.user_id),count(*),count(DISTINCT g.user_id) FILTER (WHERE g.user_id=ANY(?))
            FROM admin_gift_recipients g JOIN compensation_rounds r ON r.campaign_id=g.campaign_id
            WHERE r.incident_id=? AND r.round_number<? AND g.claimed_at IS NOT NULL""", listOf(selected, id(incidentId), beforeRoundNumber)) {
            CompensationPreviousClaims(it.getString(1), it.getString(2), it.getString(3))
        }.single() } finally { selected.free() }
    }
}

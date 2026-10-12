package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.hashToken
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Synchronous operations on the caller's existing gift transaction. */
internal object GiftCampaignOperations {
    fun validate(request: GiftDraftRequest) = normalize(request, 4000)
    // Only server-generated incident copy uses the larger fixed-envelope bound.
    const val COMPENSATION_BODY_LIMIT = 5200
    fun validateCompensationContent(request: GiftDraftRequest) = normalize(request, COMPENSATION_BODY_LIMIT)
    fun detail(tx: GiftTransaction, c: GiftCampaign, now: Long) = GiftCampaignDetail(c, tx.counters(c, now))
    data class Audience(val users: List<String>, val invalid: List<String>, val duplicates: Int)
    fun audience(tx: GiftTransaction, c: GiftCampaign): Audience {
        val draft = c.draft
        val registered = tx.registeredPlayers()
        val ids = registered.map { it.id }.toSet()
        val requested = draft.userIds.distinct()
        val selected = when (draft.audience) {
            GiftAudience.ALL_REGISTERED -> ids.toList()
            GiftAudience.USER_IDS -> requested.filter { it in ids }
            GiftAudience.REGISTERED_BETWEEN -> registered.filter { it.registeredAt >= draft.registeredFromEpochMillis!! && it.registeredAt < draft.registeredUntilEpochMillis!! }.map { it.id }
        }.sorted()
        return Audience(selected, if (draft.audience == GiftAudience.USER_IDS) requested.filter { it !in ids }.sorted() else emptyList(), draft.userIds.size - requested.size)
    }
    fun preview(c: GiftCampaign, a: Audience): GiftPreview {
        val token = hashToken(Json.encodeToString(c) + "|" + a.users.joinToString(",") + "|" + a.invalid.joinToString(","))
        return GiftPreview(c.id, c.version, a.users.size.toLong(), a.duplicates, a.invalid,
            Math.multiplyExact(c.draft.gold.toLong(), a.users.size.toLong()), Math.multiplyExact(c.draft.xp.toLong(), a.users.size.toLong()), token,
            Math.multiplyExact(c.draft.gems.toLong(), a.users.size.toLong()))
    }
    private fun normalize(request: GiftDraftRequest, bodyLimit: Int): GiftDraftRequest {
        if (request.titleVi.isBlank() || request.titleEn.isBlank() || request.titleVi.length > 160 || request.titleEn.length > 160 ||
            request.bodyVi.isBlank() || request.bodyEn.isBlank() || request.bodyVi.length > bodyLimit || request.bodyEn.length > bodyLimit ||
            request.gold < 0 || request.xp < 0 || request.gems < 0 || (request.gold == 0 && request.xp == 0 && request.gems == 0) || request.userIds.size > 10000) invalid()
        val expiry = try { request.expiresAtEpochMillis ?: Math.addExact(request.availableAtEpochMillis, 30L * 24 * 60 * 60 * 1000) } catch (_: ArithmeticException) { invalid() }
        if (request.availableAtEpochMillis < 0 || expiry <= request.availableAtEpochMillis || expiry > 253402300799999L) invalid()
        if (request.audience == GiftAudience.USER_IDS) {
            if (request.userIds.isEmpty()) invalid()
            request.userIds.forEach(::uuid)
        } else if (request.userIds.isNotEmpty()) invalid()
        if (request.audience == GiftAudience.REGISTERED_BETWEEN) {
            val from = request.registeredFromEpochMillis ?: invalid(); val until = request.registeredUntilEpochMillis ?: invalid()
            if (from < 0 || until <= from) invalid()
        } else if (request.registeredFromEpochMillis != null || request.registeredUntilEpochMillis != null) invalid()
        return request.copy(expiresAtEpochMillis = expiry, userIds = request.userIds.map { it.lowercase() })
    }
}

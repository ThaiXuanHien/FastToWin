package com.hienthai.fastowin.server.moderation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.AdminException
import com.hienthai.fastowin.server.hashToken
import kotlinx.serialization.Serializable

typealias ModerationException = AdminException
internal fun moderationError(status: Int, code: String): Nothing = throw AdminException(status, code, code)
fun contentRevision(text: String, imageReference: String? = null): String = hashToken("${text.length}:$text:${imageReference.orEmpty()}")

/** A current public target. Image references must identify an object/revision, never contain image bytes. */
data class UgcTarget(val type: UgcTargetType, val id: String, val ownerId: String?, val text: String,
    val revision: String, val imageReference: String? = null)
@Serializable enum class ModerationStatus { NEW, REVIEWING, RESOLVED, DISMISSED }
internal fun ModerationStatus.closed() = this == ModerationStatus.RESOLVED || this == ModerationStatus.DISMISSED
@Serializable data class ModerationReport(
    val id: String, val version: Int, val status: ModerationStatus, val reporterId: String?,
    val targetType: UgcTargetType, val targetId: String?, val targetOwnerId: String?,
    val reason: UgcReportReason, val note: String, val evidenceText: String, val targetRevision: String?,
    val imageReference: String?, val createdAtEpochMillis: Long, val closedAtEpochMillis: Long? = null
)
/** Explicit safe projection: reporter identity and internal image paths never cross the admin API. */
@Serializable data class ModerationReportView(
    val id: String, val version: Int, val status: ModerationStatus,
    val targetType: UgcTargetType, val targetId: String?, val reason: UgcReportReason,
    val note: String, val evidenceText: String, val targetRevision: String?,
    val createdAtEpochMillis: Long, val closedAtEpochMillis: Long?
)
@Serializable data class ModerationRestrictionView(val userId: String, val version: Int = 0, val blocked: Boolean = false)
@Serializable data class ModerationAdminView(val report: ModerationReportView, val targetChanged: Boolean,
    val targetMissing: Boolean, val targetOwnerId: String? = null, val restriction: ModerationRestrictionView? = null,
    val lastActionReason: String? = null)
@Serializable data class ModerationAdminPage(val items: List<ModerationAdminView>, val page: Int, val pageSize: Int, val total: String)
data class ModerationAdminContext(val token: String?, val csrfToken: String? = null, val origin: String? = null)
@Serializable data class ModerationResolveRequest(val expectedVersion: Int, val requestId: String, val reason: String,
    val status: ModerationStatus, val removeContent: Boolean = false)
@Serializable data class ModerationRestrictionRequest(val expectedVersion: Int, val requestId: String, val reason: String, val blocked: Boolean)
@Serializable data class ContentSuppression(val targetType: UgcTargetType, val targetId: String, val revision: String,
    val ownerId: String? = null, val createdAtEpochMillis: Long = 0, val nameRevision: String? = null)
@Serializable data class ModerationReplay(val actorId: String, val requestId: String, val fingerprint: String,
    val responseType: String, val responseJson: String, val reportId: String?, val expiresAtEpochMillis: Long)



/** Private operational reason. Report-linked entries cascade at report expiry; standalone restrictions expire in 90 days. */
data class ModerationActionRecord(val id:String,val reportId:String?,val subjectUserId:String?,val actorId:String?,
    val action:String,val reason:String,val createdAtEpochMillis:Long,val expiresAtEpochMillis:Long?=null)

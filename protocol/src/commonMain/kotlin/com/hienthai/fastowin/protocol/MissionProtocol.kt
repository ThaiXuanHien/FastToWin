package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable enum class MissionScope { PERSONAL, CLAN }
@Serializable enum class MissionCadence { DAILY, WEEKLY }
@Serializable enum class MissionAction { PLAY, WIN, CORRECT, ACCURACY, PERFECT_WIN, WIN_STREAK, CHECK_IN, DONATE_GOLD, DONATE_GEMS }
@Serializable enum class MissionCatalogState { DRAFT, PUBLISHED, ARCHIVED }
@Serializable enum class MissionClaimState { IN_PROGRESS, CLAIMABLE, CLAIMED }
@Serializable data class MissionReward(val xp: Int = 0, val gold: Int = 0, val gems: Int = 0, val contributionPoints: Int = 0)
@Serializable data class MissionConfig(
    val code: String, val scope: MissionScope, val action: MissionAction, val period: MissionCadence,
    val difficulty: MissionDifficulty, val target: Int, val reward: MissionReward,
    val matchType: MatchType? = null, val accuracyPercent: Int? = null,
    val startsOn: String? = null, val endsOn: String? = null, val streakThreshold: Int? = null
)
@Serializable data class MissionDraftRequest(val draft: MissionConfig, val expectedVersion: Long = 0, val reason: String, val requestId: String)
@Serializable data class MissionMutationRequest(val expectedVersion: Long, val reason: String, val requestId: String)
@Serializable data class MissionAdminView(val id: String, val version: Long, val state: MissionCatalogState, val draft: MissionConfig, val publishedRevision: Long? = null, val effectiveFrom: String? = null)
@Serializable data class MissionPreview(val mission: MissionAdminView, val effectiveFrom: String, val reward: MissionReward, val selectedCodes: List<String>, val warnings: List<String>, val serverNowEpochMillis: Long)
@Serializable data class MissionInstanceView(
    val instanceId: String, val code: String, val scope: MissionScope, val action: MissionAction,
    val period: MissionCadence, val difficulty: MissionDifficulty, val periodStart: String, val periodEndExclusive: String,
    val target: Int, val progress: Int, val personalContribution: Int, val reward: MissionReward,
    val state: MissionClaimState, val titleKey: String, val titleArguments: Map<String,String>,
    val matchType: MatchType? = null, val accuracyPercent: Int? = null, val streakThreshold: Int? = null
)
@Serializable data class MissionListResponse(val items: List<MissionInstanceView>, val serverNowEpochMillis: Long)
@Serializable data class MissionClaimRequest(val requestId: String)
@Serializable data class MissionClaimResponse(val instanceId: String, val state: MissionClaimState, val reward: MissionReward)
@Serializable data class MissionApiError(val code: String, val message: String)

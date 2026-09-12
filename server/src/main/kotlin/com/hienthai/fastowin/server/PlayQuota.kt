package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.PlayQuotaSnapshot
import com.hienthai.fastowin.protocol.RewardedAdBonusStatus
import com.hienthai.fastowin.protocol.RewardedAdProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object PlayQuotaRules {
    const val BASE_MATCHES = 10
    const val REWARDED_AD_MATCHES = 2

    private val quotaZone = ZoneId.of("Asia/Bangkok")

    fun quotaDate(nowMillis: Long): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(quotaZone).toLocalDate()

    fun nextResetAtEpochMillis(nowMillis: Long): Long =
        quotaDate(nowMillis)
            .plusDays(1)
            .atStartOfDay(quotaZone)
            .toInstant()
            .toEpochMilli()

    fun remaining(matchesConsumed: Int, bonusMatchesGranted: Int): Int =
        (BASE_MATCHES + bonusMatchesGranted - matchesConsumed).coerceAtLeast(0)
}

enum class PlayQuotaConsumptionStatus {
    CONSUMED,
    ALREADY_CONSUMED,
    EXHAUSTED
}

data class PlayQuotaConsumption(
    val status: PlayQuotaConsumptionStatus,
    val quotas: Map<String, PlayQuotaSnapshot>,
    val exhaustedUserIds: Set<String> = emptySet()
)

data class PlayQuotaCheck(
    val allowed: Boolean,
    val quotas: Map<String, PlayQuotaSnapshot>,
    val exhaustedUserIds: Set<String> = emptySet()
)

data class PlayQuotaGrant(
    val status: RewardedAdBonusStatus,
    val quota: PlayQuotaSnapshot
)

interface PlayQuotaRepository {
    suspend fun getSnapshot(userId: String, nowMillis: Long): PlayQuotaSnapshot

    suspend fun canPlay(userIds: Set<String>, nowMillis: Long): PlayQuotaCheck

    suspend fun consumeMatch(
        userIds: Set<String>,
        matchId: String,
        nowMillis: Long
    ): PlayQuotaConsumption

    suspend fun grantRewardedAd(
        userId: String,
        provider: RewardedAdProvider,
        providerTransactionId: String,
        nowMillis: Long
    ): PlayQuotaGrant
}

class InMemoryPlayQuotaRepository : PlayQuotaRepository {
    private val mutex = Mutex()
    private val dailyQuotas = mutableMapOf<DailyQuotaKey, MutableDailyQuota>()
    private val consumedMatches = mutableSetOf<MatchConsumptionKey>()
    private val rewardedAdTransactions = mutableSetOf<RewardedAdTransactionKey>()

    override suspend fun getSnapshot(userId: String, nowMillis: Long): PlayQuotaSnapshot = mutex.withLock {
        snapshot(userId, nowMillis)
    }

    override suspend fun canPlay(userIds: Set<String>, nowMillis: Long): PlayQuotaCheck = mutex.withLock {
        require(userIds.isNotEmpty()) { "At least one user is required." }
        val quotas = userIds.associateWith { userId -> snapshot(userId, nowMillis) }
        val exhaustedUserIds = quotas.filterValues { it.remainingMatches <= 0 }.keys
        PlayQuotaCheck(
            allowed = exhaustedUserIds.isEmpty(),
            quotas = quotas,
            exhaustedUserIds = exhaustedUserIds
        )
    }

    override suspend fun consumeMatch(
        userIds: Set<String>,
        matchId: String,
        nowMillis: Long
    ): PlayQuotaConsumption = mutex.withLock {
        require(userIds.isNotEmpty()) { "At least one user is required." }
        require(matchId.isNotBlank()) { "Match ID must not be blank." }

        val receiptKeys = userIds.mapTo(mutableSetOf()) { MatchConsumptionKey(it, matchId) }
        val existingReceiptCount = receiptKeys.count(consumedMatches::contains)
        if (existingReceiptCount == receiptKeys.size) {
            return@withLock PlayQuotaConsumption(
                status = PlayQuotaConsumptionStatus.ALREADY_CONSUMED,
                quotas = userIds.associateWith { userId -> snapshot(userId, nowMillis) }
            )
        }
        check(existingReceiptCount == 0) { "Partial quota consumption detected for match $matchId." }

        val currentQuotas = userIds.associateWith { userId -> snapshot(userId, nowMillis) }
        val exhaustedUserIds = currentQuotas.filterValues { it.remainingMatches <= 0 }.keys
        if (exhaustedUserIds.isNotEmpty()) {
            return@withLock PlayQuotaConsumption(
                status = PlayQuotaConsumptionStatus.EXHAUSTED,
                quotas = currentQuotas,
                exhaustedUserIds = exhaustedUserIds
            )
        }

        val quotaDate = PlayQuotaRules.quotaDate(nowMillis)
        userIds.forEach { userId ->
            dailyQuota(userId, quotaDate).matchesConsumed++
        }
        consumedMatches += receiptKeys
        PlayQuotaConsumption(
            status = PlayQuotaConsumptionStatus.CONSUMED,
            quotas = userIds.associateWith { userId -> snapshot(userId, nowMillis) }
        )
    }

    override suspend fun grantRewardedAd(
        userId: String,
        provider: RewardedAdProvider,
        providerTransactionId: String,
        nowMillis: Long
    ): PlayQuotaGrant = mutex.withLock {
        require(userId.isNotBlank()) { "User ID must not be blank." }
        require(providerTransactionId.isNotBlank()) { "Provider transaction ID must not be blank." }

        val transactionKey = RewardedAdTransactionKey(provider, providerTransactionId)
        if (!rewardedAdTransactions.add(transactionKey)) {
            return@withLock PlayQuotaGrant(
                status = RewardedAdBonusStatus.ALREADY_GRANTED,
                quota = snapshot(userId, nowMillis)
            )
        }

        dailyQuota(userId, PlayQuotaRules.quotaDate(nowMillis)).bonusMatchesGranted +=
            PlayQuotaRules.REWARDED_AD_MATCHES
        PlayQuotaGrant(
            status = RewardedAdBonusStatus.GRANTED,
            quota = snapshot(userId, nowMillis)
        )
    }

    private fun snapshot(userId: String, nowMillis: Long): PlayQuotaSnapshot {
        val quotaDate = PlayQuotaRules.quotaDate(nowMillis)
        val quota = dailyQuota(userId, quotaDate)
        return PlayQuotaSnapshot(
            quotaDate = quotaDate.toString(),
            baseMatches = PlayQuotaRules.BASE_MATCHES,
            matchesConsumed = quota.matchesConsumed,
            bonusMatchesGranted = quota.bonusMatchesGranted,
            remainingMatches = PlayQuotaRules.remaining(
                matchesConsumed = quota.matchesConsumed,
                bonusMatchesGranted = quota.bonusMatchesGranted
            ),
            nextResetAtEpochMillis = PlayQuotaRules.nextResetAtEpochMillis(nowMillis)
        )
    }

    private fun dailyQuota(userId: String, quotaDate: LocalDate): MutableDailyQuota =
        dailyQuotas.getOrPut(DailyQuotaKey(userId, quotaDate)) { MutableDailyQuota() }

    private data class DailyQuotaKey(val userId: String, val quotaDate: LocalDate)
    private data class MatchConsumptionKey(val userId: String, val matchId: String)
    private data class RewardedAdTransactionKey(
        val provider: RewardedAdProvider,
        val providerTransactionId: String
    )

    private data class MutableDailyQuota(
        var matchesConsumed: Int = 0,
        var bonusMatchesGranted: Int = 0
    )
}

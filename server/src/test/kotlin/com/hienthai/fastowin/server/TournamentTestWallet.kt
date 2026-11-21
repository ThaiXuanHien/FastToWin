package com.hienthai.fastowin.server

/** For tournament tests whose assertions are independent of individual wallet balances. */
internal object TournamentTestWallet : PlayerProfileRepository by NoOpPlayerProfileRepository {
    override suspend fun applyWalletTransaction(
        playerId: String,
        sourceType: String,
        sourceId: String,
        goldDelta: Int,
        gemsDelta: Int,
        xpDelta: Int
    ) = WalletMutationStatus.APPLIED
}

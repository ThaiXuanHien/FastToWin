package com.hienthai.fastowin.localization

import com.hienthai.fastowin.protocol.AccountActionResponse
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.PlayQuotaSnapshot
import com.hienthai.fastowin.protocol.RewardedAdAvailability
import com.hienthai.fastowin.protocol.RewardedAdBonusStatus
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalizedMessageMapperTest {
    private val englishMapper = LocalizedMessageMapper(LocalizationService(AppLanguage.ENGLISH))

    @Test
    fun rewardedAdResultUsesLocalizedQuotaCopy() {
        val quota = PlayQuotaSnapshot(
            quotaDate = "2026-09-12",
            remainingMatches = 2,
            nextResetAtEpochMillis = 1_789_148_400_000L,
            rewardedAdAvailability = RewardedAdAvailability.DEV_SIMULATED
        )

        assertEquals(
            "2 online matches added",
            englishMapper.message(
                ServerMessage.RewardedAdBonusResult("request-1", RewardedAdBonusStatus.GRANTED, quota)
            )
        )
        assertEquals(
            "Rewarded ads are unavailable right now.",
            englishMapper.message(
                ServerMessage.RewardedAdBonusResult("request-2", RewardedAdBonusStatus.UNAVAILABLE, quota)
            )
        )
    }

    @Test
    fun knownServerErrorUsesCurrentCatalog() {
        val error = ServerMessage.Error("AUTH_REQUIRED", "Hãy đăng nhập.")

        assertEquals("Sign in to continue.", englishMapper.message(error))
    }

    @Test
    fun activeTournamentExplainsHowToContinue() {
        val error = ServerMessage.Error(
            "TOURNAMENT_ACTIVE",
            "Hãy rời hoặc hoàn tất giải đấu hiện tại trước."
        )

        assertEquals(
            "Leave or finish your active tournament before continuing.",
            englishMapper.message(error)
        )
        assertEquals(
            "Hãy rời hoặc hoàn tất giải đấu hiện tại trước khi tiếp tục.",
            LocalizedMessageMapper(LocalizationService(AppLanguage.VIETNAMESE)).message(error)
        )
    }

    @Test
    fun playFlowConflictsUseSpecificRecoveryCopy() {
        val vietnameseMapper = LocalizedMessageMapper(LocalizationService(AppLanguage.VIETNAMESE))

        assertEquals(
            "Hãy rời phòng hoặc hủy ghép trận hiện tại trước khi tiếp tục.",
            vietnameseMapper.message(ServerMessage.Error("PLAYER_BUSY", "Người chơi đang bận."))
        )
        assertEquals(
            "Bạn đang ở trong một phòng khác. Hãy rời phòng đó trước khi tiếp tục.",
            vietnameseMapper.message(ServerMessage.Error("ALREADY_IN_ROOM", "Bạn đã ở trong phòng."))
        )
        assertEquals(
            "Bạn đang tham gia một giải đấu khác. Hãy rời hoặc hoàn tất giải đó trước.",
            vietnameseMapper.message(
                ServerMessage.Error("TOURNAMENT_ALREADY_ACTIVE", "Bạn đã tham gia giải đấu khác.")
            )
        )
    }

    @Test
    fun friendBusyKeepsOpponentSpecificCopy() {
        assertEquals(
            "Người chơi đang bận ở phòng hoặc giải khác.",
            LocalizedMessageMapper(LocalizationService(AppLanguage.VIETNAMESE)).message(
                ServerMessage.Error("FRIEND_BUSY", "Người chơi đang bận.")
            )
        )
    }

    @Test
    fun clanCreationInsufficientFundsNeverLeaksVietnameseFallbackToEnglish() {
        val error = ServerMessage.Error(
            "CLAN_CREATION_INSUFFICIENT_FUNDS",
            "Cần 2.000 vàng và 20 Gem để tạo bang."
        )

        assertEquals(
            "You need 2,000 Gold and 20 Gems to create a clan.",
            englishMapper.message(error)
        )
        assertEquals(
            "Cần 2.000 Vàng và 20 Gem để tạo bang.",
            LocalizedMessageMapper(LocalizationService(AppLanguage.VIETNAMESE)).message(error)
        )
    }

    @Test
    fun invalidResumeTokenUsesLocalizedExpiredSessionCopy() {
        val error = ServerMessage.Error(
            code = "INVALID_RESUME_TOKEN",
            message = "Phiên khôi phục không hợp lệ."
        )

        assertEquals("Your session has expired. Please sign in again.", englishMapper.message(error))
    }

    @Test
    fun explicitMessageKeyUsesNamedArguments() {
        val error = ServerMessage.Error(
            code = "RATE_LIMITED",
            message = "Bạn thao tác quá nhanh.",
            messageKey = "ServerRateLimited",
            messageArgs = mapOf("seconds" to "8")
        )

        assertEquals("Too many requests. Try again in 8 seconds.", englishMapper.message(error))
    }

    @Test
    fun unknownServerErrorKeepsRawFallback() {
        val error = ServerMessage.Error("FUTURE_CODE", "Legacy fallback")

        assertEquals("Legacy fallback", englishMapper.message(error))
    }

    @Test
    fun missingArgumentsKeepAccurateLegacyFallback() {
        val error = ServerMessage.Error(
            code = "RATE_LIMITED",
            message = "Please retry after 30 seconds."
        )

        assertEquals("Please retry after 30 seconds.", englishMapper.message(error))
    }

    @Test
    fun emptyFallbackUsesLocalizedGenericError() {
        assertEquals(
            "Something went wrong. Please try again.",
            englishMapper.message(ServerMessage.Error("FUTURE_CODE", ""))
        )
    }

    @Test
    fun accountActionUsesStructuredMessageAndRetainsFallback() {
        val response = AccountActionResponse(
            message = "Đã cập nhật mật khẩu.",
            messageKey = "PasswordChanged"
        )

        assertEquals("Password updated.", englishMapper.message(response))
        assertEquals(
            "Future fallback",
            englishMapper.message(AccountActionResponse("Future fallback", messageKey = "FutureKey"))
        )
    }

    @Test
    fun actionMessagesUseStableCodesAndArguments() {
        assertEquals(
            "Created a private 8-player tournament.",
            englishMapper.message(
                ServerMessage.TournamentNotice(
                    message = "Đã tạo giải riêng 8 người.",
                    messageArgs = mapOf("players" to "8"),
                    code = "TOURNAMENT_CREATED"
                )
            )
        )
        assertEquals(
            "Clan created.",
            englishMapper.message(
                ServerMessage.ClanActionResult(true, "Tạo clan thành công", "create_clan")
            )
        )
        assertEquals(
            "The service is temporarily unavailable. Please try again.",
            englishMapper.message(ServerMessage.Error("DATABASE_REQUIRED", "Cần cơ sở dữ liệu."))
        )
        assertEquals(
            "Invited",
            englishMapper.message(ServerMessage.Error("INVITE_SENT", "Đã gửi lời mời."))
        )
    }

    @Test
    fun oldJsonWithoutMessageKeyStillDecodes() {
        val legacyErrorJson =
            """{"type":"error","code":"FUTURE_CODE","message":"Legacy fallback"}"""

        val decoded = ProtocolJson.decodeFromString<ServerMessage>(legacyErrorJson) as ServerMessage.Error

        assertNull(decoded.messageKey)
        assertEquals(emptyMap(), decoded.messageArgs)
        assertEquals("Legacy fallback", decoded.message)
    }
}

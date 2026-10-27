package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

internal val matchmakingGuardTexts: Map<AppLanguage, Map<TextKey, String>> = mapOf(
    AppLanguage.VIETNAMESE to mapOf(
        TextKey.ServerTournamentActive to "Hãy rời hoặc hoàn tất giải đấu hiện tại trước khi tiếp tục."
    ),
    AppLanguage.ENGLISH to mapOf(
        TextKey.ServerTournamentActive to "Leave or finish your active tournament before continuing."
    ),
)

/** Recovery copy for the supported languages. */
internal val playFlowConflictTexts: Map<AppLanguage, Map<TextKey, String>> =
    AppLanguage.entries.associateWith { language ->
        when (language) {
            AppLanguage.VIETNAMESE -> mapOf(
                TextKey.PlayActionBlockedTitle to "Không thể tiếp tục",
                TextKey.ServerPlayerActivityConflict to
                    "Hãy rời phòng hoặc hủy ghép trận hiện tại trước khi tiếp tục.",
                TextKey.ServerAlreadyInRoom to
                    "Bạn đang ở trong một phòng khác. Hãy rời phòng đó trước khi tiếp tục.",
                TextKey.ServerTournamentAlreadyActive to
                    "Bạn đang tham gia một giải đấu khác. Hãy rời hoặc hoàn tất giải đó trước."
            )
            else -> mapOf(
                TextKey.PlayActionBlockedTitle to "Unable to continue",
                TextKey.ServerPlayerActivityConflict to
                    "Leave your current room or cancel matchmaking before continuing.",
                TextKey.ServerAlreadyInRoom to
                    "You are already in another room. Leave that room before continuing.",
                TextKey.ServerTournamentAlreadyActive to
                    "You are already in another tournament. Leave or finish it before continuing."
            )
        }
    }

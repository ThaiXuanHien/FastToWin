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
    AppLanguage.SIMPLIFIED_CHINESE to mapOf(
        TextKey.ServerTournamentActive to "请先退出或完成当前锦标赛，再继续操作。"
    ),
    AppLanguage.JAPANESE to mapOf(
        TextKey.ServerTournamentActive to "続行する前に、参加中の大会を退出するか完了してください。"
    ),
    AppLanguage.KOREAN to mapOf(
        TextKey.ServerTournamentActive to "계속하기 전에 진행 중인 토너먼트를 나가거나 완료하세요."
    ),
    AppLanguage.SPANISH to mapOf(
        TextKey.ServerTournamentActive to "Abandona o termina el torneo activo antes de continuar."
    ),
    AppLanguage.BRAZILIAN_PORTUGUESE to mapOf(
        TextKey.ServerTournamentActive to "Saia ou conclua o torneio ativo antes de continuar."
    ),
    AppLanguage.FRENCH to mapOf(
        TextKey.ServerTournamentActive to "Quittez ou terminez le tournoi en cours avant de continuer."
    ),
    AppLanguage.GERMAN to mapOf(
        TextKey.ServerTournamentActive to "Verlasse oder beende dein aktives Turnier, bevor du fortfährst."
    ),
    AppLanguage.INDONESIAN to mapOf(
        TextKey.ServerTournamentActive to "Keluar atau selesaikan turnamen aktif sebelum melanjutkan."
    ),
    AppLanguage.THAI to mapOf(
        TextKey.ServerTournamentActive to "ออกจากหรือเล่นทัวร์นาเมนต์ปัจจุบันให้จบก่อนดำเนินการต่อ"
    ),
    AppLanguage.RUSSIAN to mapOf(
        TextKey.ServerTournamentActive to "Покиньте или завершите активный турнир, прежде чем продолжить."
    )
)

/** VI/EN are selectable; legacy catalogs keep clear English recovery copy. */
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

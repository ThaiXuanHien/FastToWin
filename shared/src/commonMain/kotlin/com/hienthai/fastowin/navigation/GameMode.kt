package com.hienthai.fastowin.navigation

import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService

enum class GameMode(
    val titleKey: TextKey,
    val descriptionKey: TextKey,
    val unlockLevel: Int
) {
    ORDER(TextKey.ModeClassicTitle, TextKey.ModeClassicDescription, 1),
    RANDOM_TARGET(TextKey.ModeRandomTitle, TextKey.ModeRandomDescription, 3),
    TIME_BONUS(TextKey.ModeTimeBonusTitle, TextKey.ModeTimeBonusDescription, 5),
    SPEED_UP(TextKey.ModeSpeedUpTitle, TextKey.ModeSpeedUpDescription, 7),
    SURVIVAL(TextKey.ModeSurvivalTitle, TextKey.ModeSurvivalDescription, 10),
    COMBO(TextKey.ModeComboTitle, TextKey.ModeComboDescription, 12),
    TIME_ATTACK(TextKey.ModeTimeAttackTitle, TextKey.ModeTimeAttackDescription, 1),
    TEAM_2V2(TextKey.ModeTeamTitle, TextKey.ModeTeamDescription, 5);

    val isLegacy: Boolean get() = this == TIME_ATTACK

    val title: String get() = LocalizationService(AppLanguage.VIETNAMESE).text(titleKey)

    val description: String get() = LocalizationService(AppLanguage.VIETNAMESE).text(descriptionKey)
}

package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EconomyProgressionLocalizationTest {
    @Test
    fun `all economy progression copy is explicit in every language`() {
        val keysByName = TextKey.entries.associateBy(TextKey::name)
        val expectedKeys = ECONOMY_PROGRESSION_KEY_NAMES.mapTo(mutableSetOf()) { keyName ->
            assertNotNull(keysByName[keyName], "Missing TextKey.$keyName")
        }

        AppLanguage.entries.forEach { language ->
            val explicitTexts = economyProgressionTexts.getValue(language)
            assertEquals(expectedKeys, explicitTexts.keys, language.code)
            expectedKeys.forEach { key ->
                    val catalog = allLocalizationCatalogs.getValue(language)
                    val translated = catalog.texts.getValue(key)
                    assertTrue(translated.isNotBlank(), "${language.code}/$key")
                    assertEquals(explicitTexts.getValue(key), translated, "${language.code}/$key")
            }
        }
    }

    @Test
    fun `representative progression copy resolves in all twelve languages`() {
        val expectedFrameNames = mapOf(
            AppLanguage.VIETNAMESE to "Tia Chớp",
            AppLanguage.ENGLISH to "Lightning",
            AppLanguage.SIMPLIFIED_CHINESE to "闪电",
            AppLanguage.JAPANESE to "稲妻",
            AppLanguage.KOREAN to "번개",
            AppLanguage.SPANISH to "Relámpago",
            AppLanguage.BRAZILIAN_PORTUGUESE to "Relâmpago",
            AppLanguage.FRENCH to "Éclair",
            AppLanguage.GERMAN to "Blitz",
            AppLanguage.INDONESIAN to "Kilat",
            AppLanguage.THAI to "สายฟ้า",
            AppLanguage.RUSSIAN to "Молния"
        )
        val key = TextKey.entries.singleOrNull { it.name == "FrameLightningName" }
        assertNotNull(key, "Missing TextKey.FrameLightningName")
        expectedFrameNames.forEach { (language, expected) ->
            assertTrue(allLocalizationCatalogs.getValue(language).texts.getValue(key) == expected)
        }
    }

    private companion object {
        val ECONOMY_PROGRESSION_KEY_NAMES = setOf(
            "GoldTab", "GoldVault", "GoldVaultDescription", "ExchangeGemsForGold",
            "GoldExchangeConfirmation", "NotEnoughGems", "GoldExchangeGranted",
            "GoldExchangeAlreadyGranted", "GoldExchangeFailed", "GoldOfferBagName",
            "GoldOfferChestName", "GoldOfferVaultName",

            "MissionPlayOne", "MissionCasualTwo", "MissionPlayThree", "MissionWinOne",
            "MissionRankedTwo", "MissionDailyCorrectHundred", "MissionAccuracyNinety",
            "MissionDailyPerfectWin", "MissionDailyCheckIn", "MissionDonateGoldFiveHundred",
            "MissionWeeklyPlayFifteen", "MissionWeeklyWinFive", "MissionWeeklyRankedWinThree",
            "MissionWeeklyCorrectFiveHundred", "MissionWeeklyStreakThree",
            "MissionWeeklyPerfectThree", "MissionWeeklyDonateGoldTwoThousand",
            "MissionWeeklyDonateGemsFive",

            "AchievementFirstWinTitle", "AchievementFirstWinDescription",
            "AchievementWins10Title", "AchievementWins10Description",
            "AchievementWins50Title", "AchievementWins50Description",
            "AchievementRankedWins100Title", "AchievementRankedWins100Description",
            "AchievementWinStreak10Title", "AchievementWinStreak10Description",
            "AchievementPerfectMatch1Title", "AchievementPerfectMatch1Description",
            "AchievementPerfectMatches10Title", "AchievementPerfectMatches10Description",
            "AchievementAccuracy90TenTitle", "AchievementAccuracy90TenDescription",
            "AchievementResponse2500TenTitle", "AchievementResponse2500TenDescription",
            "AchievementResponse1500TenTitle", "AchievementResponse1500TenDescription",
            "AchievementCheckinStreak7Title", "AchievementCheckinStreak7Description",
            "AchievementCheckinStreak30Title", "AchievementCheckinStreak30Description",
            "AchievementCheckins50Title", "AchievementCheckins50Description",
            "AchievementCheckins100Title", "AchievementCheckins100Description",
            "AchievementPlayerLevel30Title", "AchievementPlayerLevel30Description",
            "AchievementClanJoinedTitle", "AchievementClanJoinedDescription",
            "AchievementClanGold10000Title", "AchievementClanGold10000Description",
            "AchievementClanGems50Title", "AchievementClanGems50Description",
            "AchievementClanQuests10Title", "AchievementClanQuests10Description",
            "AchievementClanLevel10Title", "AchievementClanLevel10Description",

            "FrameLightningName", "FrameLightningUnlockDescription",
            "FrameWildfireName", "FrameWildfireUnlockDescription",
            "FrameWarriorName", "FrameWarriorUnlockDescription",
            "FrameVeteranName", "FrameVeteranUnlockDescription",
            "FrameDiamondName", "FrameDiamondUnlockDescription",
            "FrameChallengerName", "FrameChallengerUnlockDescription",
            "FrameGloryName", "FrameGloryUnlockDescription",
            "FrameUnyieldingName", "FrameUnyieldingUnlockDescription",
            "FrameLegendName", "FrameLegendUnlockDescription",
            "FrameEmperorName", "FrameEmperorUnlockDescription",
            "FrameSpeedShadowName", "FrameSpeedShadowUnlockDescription",
            "FrameChampionName", "FrameChampionUnlockDescription",
            "FrameImmortalName", "FrameImmortalUnlockDescription",
            "FrameDragonMightName", "FrameDragonMightUnlockDescription",
            "FrameSupremeName", "FrameSupremeUnlockDescription",
            "FramePeerlessName", "FramePeerlessUnlockDescription",

            "TitleFirstBattleName", "TitleFirstBattleUnlockDescription",
            "TitleGodspeedName", "TitleGodspeedUnlockDescription",
            "TitleUndefeatedName", "TitleUndefeatedUnlockDescription",
            "TitleVeteranName", "TitleVeteranUnlockDescription",
            "TitleDivineEyeName", "TitleDivineEyeUnlockDescription",
            "TitlePillarName", "TitlePillarUnlockDescription",
            "TitleOneStrikeName", "TitleOneStrikeUnlockDescription",
            "TitleGoldenReflexName", "TitleGoldenReflexUnlockDescription",
            "TitleMasterName", "TitleMasterUnlockDescription",
            "TitleConquerorName", "TitleConquerorUnlockDescription",
            "TitleWarGodName", "TitleWarGodUnlockDescription",
            "TitleSpeedKingName", "TitleSpeedKingUnlockDescription"
        )
    }
}

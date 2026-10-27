package com.hienthai.fastowin.localization

import com.hienthai.fastowin.protocol.*
import kotlin.test.*

class MissionPresentationTest {
    @Test fun `admin targets and explicit accuracy and clan streak render in every language`() {
        for(language in AppLanguage.entries) {
            val l=LocalizationService(language)
            val accuracy=MissionInstanceView("id","CUSTOM",MissionScope.PERSONAL,MissionAction.ACCURACY,MissionCadence.DAILY,MissionDifficulty.NORMAL,"2026-10-24","2026-10-25",27,3,3,MissionReward(),MissionClaimState.IN_PROGRESS,"MissionActionACCURACY",mapOf("target" to "27","accuracy" to "87"),MatchType.RANKED,87)
            assertTrue(l.missionTitle(accuracy).contains("27")); assertTrue(l.missionTitle(accuracy).contains("87")); assertFalse(l.missionTitle(accuracy).contains("{"))
            val streak=accuracy.copy(scope=MissionScope.CLAN,action=MissionAction.WIN_STREAK,titleKey="MissionClanStreak",titleArguments=mapOf("target" to "27","streak" to "9"),accuracyPercent=null,streakThreshold=9)
            assertTrue(l.missionTitle(streak).contains("27")); assertTrue(l.missionTitle(streak).contains("9"))
        }
    }
    @Test fun `legacy dynamic title without arguments uses truthful snapshot fallback`() {
        val legacy=MissionSnapshot(code="CUSTOM",title="Reach 87% accuracy in 27 matches",progress=3,target=27,completed=false,rewardXp=0,rewardGold=0,titleKey="MissionActionACCURACY")
        val l=LocalizationService(AppLanguage.ENGLISH)
        assertEquals("Reach 87% accuracy in 27 matches",l.missionTitle(legacy))
    }
    @Test fun `legacy clan sentinel is ongoing without exposing a fabricated 1970 deadline`() {
        val legacy=MissionInstanceView("legacy-instance","LEGACY_CLAN_QUEST",MissionScope.CLAN,MissionAction.WIN,MissionCadence.WEEKLY,
            MissionDifficulty.NORMAL,"1970-01-01","1970-01-05",350,261,86,MissionReward(gold=5000),MissionClaimState.IN_PROGRESS,
            "MissionActionWIN",mapOf("target" to "350"),MatchType.RANKED)
        assertEquals("Ongoing clan mission",LocalizationService(AppLanguage.ENGLISH).missionPeriod(legacy))
        val regular=legacy.copy(code="CUSTOM",periodStart="2026-10-19",periodEndExclusive="2026-10-26")
        assertEquals("2026-10-19 → before 2026-10-26",LocalizationService(AppLanguage.ENGLISH).missionPeriod(regular))
    }
    @Test fun `imported clan wins keep ranked eligibility even when historical DTO omits match type`() {
        val legacy=MissionInstanceView("legacy-instance","LEGACY_CLAN_QUEST",MissionScope.CLAN,MissionAction.WIN,MissionCadence.WEEKLY,
            MissionDifficulty.NORMAL,"1970-01-01","1970-01-05",350,261,86,MissionReward(gold=5000),MissionClaimState.IN_PROGRESS,
            "MissionActionWIN",mapOf("target" to "350"))
        assertEquals(MatchType.RANKED,legacy.displayMatchType)
        assertNull(legacy.copy(code="CUSTOM").displayMatchType)
    }
}

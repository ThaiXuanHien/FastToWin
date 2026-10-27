package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.preferences.*
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.*
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.screens.*
import com.hienthai.fastowin.ui.theme.FastToWinTheme

private fun missionRows(clan: Boolean = false) = listOf(
    MissionInstanceView("current", "ADMIN_ACCURACY_27", if(clan) MissionScope.CLAN else MissionScope.PERSONAL,
        if(clan) MissionAction.WIN_STREAK else MissionAction.ACCURACY, MissionCadence.DAILY, MissionDifficulty.HARD,
        "2026-10-24", "2026-10-25", 27, 27, 9, MissionReward(12345, 987654321, 12345, if(clan) 500 else 0), MissionClaimState.CLAIMABLE,
        if(clan) "MissionClanStreak" else "MissionActionACCURACY", mapOf("target" to "27", "accuracy" to "87", "streak" to "9"),
        MatchType.RANKED, if(clan) null else 87, if(clan) 9 else null),
    MissionInstanceView("past", "ADMIN_OLD_PLAY", if(clan) MissionScope.CLAN else MissionScope.PERSONAL, MissionAction.PLAY,
        MissionCadence.WEEKLY, MissionDifficulty.NORMAL, "2026-10-12", "2026-10-19", 19, 19, 3, MissionReward(250,500,5),
        MissionClaimState.CLAIMABLE,"MissionActionPLAY",mapOf("target" to "19"),MatchType.CASUAL)
)
@Composable private fun MissionFrame(english: Boolean = false, content: @Composable () -> Unit) {
    ProvideLocalization(if(english) AppLanguage.ENGLISH else AppLanguage.VIETNAMESE) {
        FastToWinTheme(preferences=AppPreferences(themeMode=AppThemeMode.DARK, visualEffectsEnabled=false)) {
            Surface(Modifier.fillMaxSize()) { content() }
        }
    }
}
@Composable private fun PersonalMissions(pending: Boolean = false) {
    val profile=ArcadeScreenshotFixtures.profile()
    ProfileSectionScreen(state=ArcadeScreenshotFixtures.homeState(),profile=profile,section=ProfileSection.MISSIONS,isExternalProfile=false,
        canEdit=true,onBack={},onRefresh={},onOpenMatchDetail={},onCloseMatchDetail={},onEquipCosmetics={_,_->},onClaimMissionReward={},
        onSave={_,_->},onOpenNotifications={},missionState=MissionState(items=missionRows(),pendingIds=if(pending) setOf("current") else emptySet()))
}
@PreviewTest @Preview(name="personal_vi_dark_375",widthDp=375,heightDp=1000) @Composable
fun missionsPersonalPhone() = MissionFrame { PersonalMissions() }
@PreviewTest @Preview(name="personal_en_dark_1024",widthDp=1024,heightDp=1000) @Composable
fun missionsPersonalDesktop() = MissionFrame(true) { PersonalMissions() }
@PreviewTest @Preview(name="pending_vi_dark_375_large",widthDp=375,heightDp=1300,fontScale=1.6f) @Composable
fun missionsPersonalLargeText() = MissionFrame { PersonalMissions(true) }
@PreviewTest @Preview(name="personal_en_landscape_844",widthDp=844,heightDp=390,fontScale=1.3f) @Composable
fun missionsPersonalLandscape() = MissionFrame(true) { PersonalMissions() }
@PreviewTest @Preview(name="clan_vi_dark_375",widthDp=375,heightDp=1400) @Composable
fun missionsClanPhone() = MissionFrame {
    val state=ArcadeScreenshotFixtures.clanState()
    ClanDetailView(serverUrl="",currentUserId=state.profile?.userId,clan=checkNotNull(state.currentClan),notice=null,onLeave={},
        onKickMember={_,_->},onRespondJoinRequest={_,_,_->},onUpdateLogo={},onClaimQuest={},missionState=MissionState(items=missionRows(true)))
}
@PreviewTest @Preview(name="states_en_dark_1024",widthDp=1024,heightDp=1300,fontScale=1.3f) @Composable
fun missionsFeedbackDesktop() = MissionFrame(true) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ServerMissionList(MissionState(isLoading=true),{},{})
        ServerMissionList(MissionState(loadError="NETWORK_ERROR"),{},{})
        ServerMissionList(MissionState(),{},{})
        ServerMissionList(MissionState(items=listOf(missionRows().first().copy(state=MissionClaimState.CLAIMED))),{},{})
    }
}
@PreviewTest @Preview(name="past_retry_en_dark_375",widthDp=375,heightDp=1000,fontScale=1.6f) @Composable
fun missionsPastClaimRetry() = MissionFrame(true) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ServerMissionList(MissionState(items=listOf(missionRows().last()),itemErrors=mapOf("past" to "NETWORK_ERROR")),{},{})
    }
}

@PreviewTest @Preview(name="claim_footer_en_landscape_844",widthDp=844,heightDp=390,fontScale=1.3f) @Composable
fun missionsLandscapeClaimFooter() = MissionFrame(true) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(100000)).padding(16.dp)) {
        ServerMissionList(MissionState(items=missionRows()),{},{})
    }
}
@PreviewTest @Preview(name="clan_vi_dark_375_large",widthDp=375,heightDp=1100,fontScale=1.6f) @Composable
fun missionsClanLargeText() = MissionFrame {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ServerMissionList(MissionState(items=missionRows(true).take(1)),{},{})
    }
}

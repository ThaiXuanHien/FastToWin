package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.material3.Surface
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.*
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.screens.*
import com.hienthai.fastowin.ui.theme.FastToWinTheme

private val rewardPreviewEvent = RewardEventPublicView("october",1,RewardEventState.ACTIVE,
    "Tháng Mười bứt tốc", "October sprint", "Nhân đôi GOLD và XP trong trận Thường và Xếp hạng.",
    "Double GOLD and XP in Casual and Ranked matches.","Asia/Ho_Chi_Minh",
    ArcadeScreenshotFixtures.NOW_MILLIS-3600000,ArcadeScreenshotFixtures.NOW_MILLIS+86400000,
    2,2,setOf(MatchType.CASUAL,MatchType.RANKED))
private val rewardPreviewState = RewardEventUiState(RewardEventLoad.READY,listOf(rewardPreviewEvent),serverNowEpochMillis=ArcadeScreenshotFixtures.NOW_MILLIS,total=1)
private val rewardPreviewSource = RewardEventSourceSnapshot("october",1,rewardPreviewEvent.nameVi,rewardPreviewEvent.nameEn,
    rewardPreviewEvent.startsAtEpochMillis,rewardPreviewEvent.endsAtEpochMillis,2,2)
private val rewardPreviewBreakdown = MatchRewardBreakdown(100,100,200,30,30,60,listOf(rewardPreviewSource))

@PreviewTest
@Preview(name="compact",widthDp=320,heightDp=568,showSystemUi=false)
@Preview(name="large_phone",widthDp=430,heightDp=932,showSystemUi=false)
@Preview(name="large_text",widthDp=430,heightDp=932,fontScale=1.6f,showSystemUi=false)
@Preview(name="desktop",widthDp=1024,heightDp=768,showSystemUi=false)
@Composable
fun rewardEventHome() = ArcadeScreenshotFrame {
    RewardEventHomeContent()
}
@Composable
private fun RewardEventHomeContent() {
    LobbyScreen(state=ArcadeScreenshotFixtures.homeState(),onModeSelected={},onStartMatchmaking={_,_->},
        onCancelMatchmaking={},onOpenRoomBrowser={},onCreateRoom={_,_,_,_->},onJoinRoom={_,_->},onLeaveRoom={},
        onSetReady={},onKickOpponent={},onRefreshRooms={},onOpenProfile={},onOpenLeaderboard={},onOpenFriends={},
        onOpenFriendProfile={},onBackToMode={},onLogout={},isGuest=false,onUpgradeGuest={},onOpenNotifications={},
        onOpenClan={},onOpenPractice={},onOpenTournament={},onOpenShop={},onShareRoom={_,_->Result.success(Unit)},
        onResolveRoomLink={},onClaimDailyCheckIn={},rewardEventState=rewardPreviewState,onOpenRewardEvents={},onRefreshRewardEvents={})
}

@PreviewTest
@Preview(name="compact",widthDp=320,heightDp=568,showSystemUi=false)
@Preview(name="large_phone",widthDp=430,heightDp=932,showSystemUi=false)
@Preview(name="large_text",widthDp=430,heightDp=932,fontScale=1.6f,showSystemUi=false)
@Preview(name="desktop",widthDp=1024,heightDp=768,showSystemUi=false)
@Composable
fun rewardEventDetails() = ArcadeScreenshotFrame {
    RewardEventDetailsScreen(rewardPreviewState.copy(selected=rewardPreviewEvent),{},{},{},{})
}

@PreviewTest
@Preview(name="compact",widthDp=320,heightDp=568,showSystemUi=false)
@Preview(name="large_phone",widthDp=430,heightDp=932,showSystemUi=false)
@Preview(name="large_text",widthDp=430,heightDp=932,fontScale=1.6f,showSystemUi=false)
@Preview(name="desktop",widthDp=1024,heightDp=768,showSystemUi=false)
@Composable
fun rewardEventResult() = ArcadeScreenshotFrame {
    RewardEventResultContent()
}
@Composable
private fun RewardEventResultContent() {
    val original = ArcadeScreenshotFixtures.rankedWinState()
    ResultScreen(state=original.copy(currentMatchId="result",rewardBreakdowns=mapOf(requireNotNull(original.player.id) to rewardPreviewBreakdown)),
        onRestart={},onBack={},onRematch={},onCancelRematch={},onDeclineRematch={},onConnectOpponent={},onBlockOpponent={},onOpenRewardEvent={},
        preferences=AppPreferences(soundEnabled=false,vibrationEnabled=false,visualEffectsEnabled=false))
}

@PreviewTest
@Preview(name="english_details",widthDp=430,heightDp=932,showSystemUi=false)
@Preview(name="english_desktop",widthDp=1024,heightDp=768,showSystemUi=false)
@Composable
fun rewardEventEnglish() = ArcadeScreenshotFrame {
    ProvideLocalization(AppLanguage.ENGLISH) {
        RewardEventDetailsScreen(rewardPreviewState.copy(selected=rewardPreviewEvent),{},{},{},{})
    }
}

@PreviewTest
@Preview(name="result_large_text",widthDp=320,heightDp=700,fontScale=1.6f,showSystemUi=false)
@Composable
fun rewardEventAmountsLargeText() = ArcadeScreenshotFrame { RewardBreakdownCard(rewardPreviewBreakdown) }

@PreviewTest
@Preview(name="english_phone",widthDp=430,heightDp=932,showSystemUi=false)
@Preview(name="english_desktop",widthDp=1024,heightDp=768,showSystemUi=false)
@Composable
fun rewardEventEnglishHome() = ArcadeScreenshotFrame { ProvideLocalization(AppLanguage.ENGLISH) { RewardEventHomeContent() } }

@PreviewTest
@Preview(name="english_phone",widthDp=430,heightDp=932,showSystemUi=false)
@Preview(name="english_large_text",widthDp=430,heightDp=932,fontScale=1.6f,showSystemUi=false)
@Preview(name="english_desktop",widthDp=1024,heightDp=768,showSystemUi=false)
@Composable
fun rewardEventEnglishResult() = ArcadeScreenshotFrame { ProvideLocalization(AppLanguage.ENGLISH) { RewardEventResultContent() } }

@PreviewTest
@Preview(name="light_home",widthDp=430,heightDp=932,showSystemUi=false)
@Composable
fun rewardEventLightHome() = RewardEventLightFrame { RewardEventHomeContent() }

@PreviewTest
@Preview(name="light_details",widthDp=430,heightDp=932,showSystemUi=false)
@Composable
fun rewardEventLightDetails() = RewardEventLightFrame { RewardEventDetailsScreen(rewardPreviewState.copy(selected=rewardPreviewEvent),{},{},{},{}) }

@PreviewTest
@Preview(name="light_result",widthDp=430,heightDp=932,showSystemUi=false)
@Composable
fun rewardEventLightResult() = RewardEventLightFrame { RewardEventResultContent() }

@PreviewTest
@Preview(name="landscape_home",widthDp=844,heightDp=390,showSystemUi=false)
@Composable
fun rewardEventLandscapeHome() = ArcadeScreenshotFrame { RewardEventHomeContent() }

@Composable
private fun RewardEventLightFrame(content: @Composable () -> Unit) = ArcadeScreenshotFrame {
    FastToWinTheme(preferences=AppPreferences(themeMode=AppThemeMode.LIGHT,visualEffectsEnabled=false)) { Surface { content() } }
}

package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.ClanChatMessageKind
import com.hienthai.fastowin.protocol.ClanChatMessageSnapshot
import com.hienthai.fastowin.protocol.ClanChatSystemEvent
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.state.ClanChatState
import com.hienthai.fastowin.state.PendingClanChatMessage
import com.hienthai.fastowin.ui.screens.ClanChatActions
import com.hienthai.fastowin.ui.screens.ClanDetailView
import com.hienthai.fastowin.ui.screens.ClanLogoDialog
import com.hienthai.fastowin.ui.screens.ClanMemberProfileDialog
import com.hienthai.fastowin.ui.screens.ClanMemberRoleAction
import com.hienthai.fastowin.ui.screens.ClanChatMessageActionsDialog
import com.hienthai.fastowin.ui.components.ProvideUgcReportActions
import com.hienthai.fastowin.ui.components.UgcReportDialog
import com.hienthai.fastowin.state.UgcReportDraft
import com.hienthai.fastowin.protocol.UgcTargetType
import com.hienthai.fastowin.protocol.UgcReportReason

private const val CHAT_NOW = 1_795_000_000_000L
private const val chatZone = "UTC"

private val chatClan = ClanSnapshot(
    id = "clan-1", name = "Thần Tốc Việt", description = "Nhanh, chính xác và luôn đồng đội.", ownerId = "player-1",
    members = listOf(
        ClanMemberSnapshot("player-1", "HienTX", ClanRole.LEADER, 1_742, avatarId = "avatar_player_blue"),
        ClanMemberSnapshot("player-2", "Minh Anh", ClanRole.CO_LEADER, 1_698, avatarId = "avatar_player_coral"),
        ClanMemberSnapshot("player-3", "Kenji", ClanRole.MEMBER, 1_521),
        ClanMemberSnapshot("player-4", "Sofia", ClanRole.MEMBER, 1_488)
    ),
    trophies = 6_449, logoId = "dragon", level = 18, experiencePoints = 228_400,
    currentLevelExperience = 8_400, nextLevelExperience = 15_000, donatedGold = 329_000, donatedGems = 3_230
)

private fun chatLine(id: String, minutesAgo: Long, sender: String?, name: String, text: String) =
    ClanChatMessageSnapshot(id, chatClan.id, ClanChatMessageKind.USER, CHAT_NOW - minutesAgo * 60_000, sender, name, text)

private fun systemLine(id: String, minutesAgo: Long, event: ClanChatSystemEvent, args: Map<String, String>) =
    ClanChatMessageSnapshot(id, chatClan.id, ClanChatMessageKind.SYSTEM, CHAT_NOW - minutesAgo * 60_000,
        systemEvent = event, systemArgs = args)

private val chatState = ClanChatState(
    clanId = chatClan.id,
    hasMore = true,
    messages = listOf(
        systemLine("s1", 1_500, ClanChatSystemEvent.MEMBER_JOINED, mapOf("player" to "Sofia", "playerId" to "player-4")),
        chatLine("m1", 52, "player-2", "Minh Anh", "Tối nay 21h đánh giải bang nhé mọi người!"),
        chatLine("m2", 47, "player-3", "Kenji", "Ok, mình vào đúng giờ 👍"),
        systemLine("s2", 40, ClanChatSystemEvent.LEVEL_UP, mapOf("level" to "18")),
        chatLine("m3", 12, "player-1", "HienTX", "Cảm ơn cả bang đã quyên góp. Mục tiêu tuần này là top 3 đua bang."),
        chatLine("m4", 3, "player-4", "Sofia", "Cố lên!!! ****")
    ),
    mutedMembers = mapOf("player-3" to CHAT_NOW + 40 * 60_000),
    isVisible = true
)

@Composable
private fun ChatDetail(chat: ClanChatState, currentUserId: String = "player-1") {
    ClanDetailView(
        serverUrl = "", currentUserId = currentUserId, clan = chatClan, notice = null,
        onLeave = {}, onKickMember = { _, _ -> }, onRespondJoinRequest = { _, _, _ -> }, onUpdateLogo = {},
        onClaimQuest = {}, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        clanChat = chat, chatActions = ClanChatActions(), chatNowMillis = CHAT_NOW, chatTimeZoneId = chatZone,
        initialChatTab = true
    )
}

@PreviewTest
@Preview(name = "chat_vi_phone", widthDp = 375, heightDp = 812)
@Preview(name = "chat_vi_large_text", widthDp = 375, heightDp = 812, fontScale = 1.6f)
@Preview(name = "chat_vi_desktop", widthDp = 1280, heightDp = 800)
@Composable
fun clanChatConversation() = ArcadeScreenshotFrame { ChatDetail(chatState) }

@PreviewTest
@Preview(name = "chat_en_muted_phone", widthDp = 375, heightDp = 812)
@Composable
fun clanChatMutedWithFailedLine() = ArcadeScreenshotFrame {
    ProvideLocalization(AppLanguage.ENGLISH) {
        ChatDetail(chatState.copy(
            mutedUntilEpochMillis = CHAT_NOW + 55 * 60_000,
            pending = listOf(PendingClanChatMessage("r1", "See you at 9pm", CHAT_NOW,
                failure = "You are sending too fast. Try again in 2 seconds."))
        ), currentUserId = "player-3")
    }
}

@PreviewTest
@Preview(name = "chat_vi_empty_phone", widthDp = 375, heightDp = 812)
@Composable
fun clanChatEmpty() = ArcadeScreenshotFrame {
    ChatDetail(ClanChatState(clanId = chatClan.id, isVisible = true))
}

@PreviewTest
@Preview(name = "chat_overview_tab_unread_phone", widthDp = 375, heightDp = 812)
@Composable
fun clanChatOverviewTabWithUnread() = ArcadeScreenshotFrame {
    ClanDetailView(
        serverUrl = "", currentUserId = "player-1", clan = chatClan, notice = null,
        onLeave = {}, onKickMember = { _, _ -> }, onRespondJoinRequest = { _, _, _ -> }, onUpdateLogo = {},
        onClaimQuest = {}, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        clanChat = chatState.copy(isVisible = false, unreadCount = 3), chatNowMillis = CHAT_NOW, chatTimeZoneId = chatZone
    )
}

@PreviewTest
@Preview(name = "logo_upload_vi_phone", widthDp = 375, heightDp = 812)
@Preview(name = "logo_upload_vi_desktop", widthDp = 1024, heightDp = 768)
@Composable
fun clanLogoUploadDialog() = ArcadeScreenshotFrame {
    ClanLogoDialog(selectedLogoId = "dragon", clanLevel = 18, onSelect = {}, onDismiss = {},
        hasUploadedLogo = true, onUpload = {}, onRemoveUpload = {})
}

@PreviewTest
@Preview(name = "member_appoint_vi_phone", widthDp = 375, heightDp = 812)
@Composable
fun clanMemberAppointCoLeader() = ArcadeScreenshotFrame {
    ClanMemberProfileDialog(chatClan.members[2], "player-1", FriendsSnapshot(), false, null, null, {}, { _, _ -> }, {},
        roleAction = ClanMemberRoleAction(appoint = true, enabled = true, onClick = {}))
}

@PreviewTest
@Preview(name = "message_actions_vi_phone", widthDp = 375, heightDp = 812)
@Preview(name = "message_actions_vi_desktop", widthDp = 1024, heightDp = 768)
@Composable
fun clanChatMessageActions() = ArcadeScreenshotFrame {
    ProvideUgcReportActions("player-1", onReport = {}) {
        ChatDetail(chatState)
        ClanChatMessageActionsDialog(chatState.messages[2], chatClan.members[2], ClanRole.LEADER, "player-1",
            mutedUntil = null, actions = ClanChatActions(), onDismiss = {})
    }
}

@PreviewTest
@Preview(name = "message_report_vi_phone", widthDp = 375, heightDp = 812)
@Preview(name = "message_report_vi_desktop", widthDp = 1024, heightDp = 768)
@Composable
fun clanChatReportDialog() = ArcadeScreenshotFrame {
    ChatDetail(chatState)
    UgcReportDialog(UgcReportDraft(UgcTargetType.CLAN_MESSAGE, "m2", "Ok, mình vào đúng giờ 👍", UgcReportReason.HARASSMENT),
        onEdit = { _, _, _ -> }, onSubmit = {}, onDismiss = {})
}

@PreviewTest
@Preview(name = "message_block_confirm_vi_phone", widthDp = 375, heightDp = 812)
@Composable
fun clanChatBlockConfirmation() = ArcadeScreenshotFrame {
    ChatDetail(chatState)
    ClanChatMessageActionsDialog(chatState.messages[2], chatClan.members[2], ClanRole.MEMBER, "player-4",
        mutedUntil = null, actions = ClanChatActions(), onDismiss = {}, initialBlockConfirmation = true)
}

@PreviewTest
@Preview(name = "chat_after_block_vi_phone", widthDp = 375, heightDp = 812)
@Preview(name = "chat_after_block_vi_desktop", widthDp = 1280, heightDp = 800)
@Composable
fun clanChatAfterBlock() = ArcadeScreenshotFrame {
    ChatDetail(chatState.withoutSender("player-3"))
}

package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.ui.screens.ClanMemberProfileDialog

private val previewMember = ClanMemberSnapshot(
    userId = "member-1", displayName = "Nguyễn Minh Anh · Tia Chớp", role = ClanRole.CO_LEADER,
    trophies = 23540, questContribution = 128, donatedGold = 1500000, donatedGems = 2500,
    playerCode = "MINHANH01", avatarId = "avatar_player_blue", frameId = "frame_gold",
    presence = FriendPresence.PLAYING
)

@PreviewTest
@Preview(name = "member_vi_phone", widthDp = 375, heightDp = 812)
@Preview(name = "member_vi_small_large_text", widthDp = 320, heightDp = 932, fontScale = 1.6f)
@Preview(name = "member_vi_desktop", widthDp = 1024, heightDp = 768)
@Composable
fun clanMemberProfileVietnamese() = ArcadeScreenshotFrame {
    ClanMemberProfileDialog(previewMember, "self", FriendsSnapshot(), false, null, null, {}, { _, _ -> }, {})
}

@PreviewTest
@Preview(name = "member_en_phone", widthDp = 375, heightDp = 812)
@Preview(name = "member_en_desktop", widthDp = 1024, heightDp = 768)
@Preview(name = "member_en_landscape_large_text", widthDp = 844, heightDp = 390, fontScale = 1.6f)
@Composable
fun clanMemberProfileEnglish() = ArcadeScreenshotFrame {
    ProvideLocalization(AppLanguage.ENGLISH) {
        ClanMemberProfileDialog(previewMember, "self", FriendsSnapshot(incomingRequests = listOf(
            FriendRequestSnapshot("request-1", previewMember.userId, previewMember.displayName, previewMember.playerCode)
        )), false, null, null, {}, { _, _ -> }, {})
    }
}

@PreviewTest
@Preview(name = "member_request_error_en", widthDp = 375, heightDp = 812)
@Composable
fun clanMemberProfileRequestError() = ArcadeScreenshotFrame {
    ProvideLocalization(AppLanguage.ENGLISH) {
        ClanMemberProfileDialog(previewMember, "self", FriendsSnapshot(), false, null,
            "Unable to send the request. Please try again.", {}, { _, _ -> }, {})
    }
}

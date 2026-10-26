package com.hienthai.fastowin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.ui.screens.ClanDetailView
import com.hienthai.fastowin.ui.screens.ClanMemberProfileDialog
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ClanMemberProfileUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val member = ClanMemberSnapshot("member", "Minh Anh", ClanRole.MEMBER, 123,
        questContribution = 7, donatedGold = 500, donatedGems = 10, playerCode = "MEMBER01",
        frameId = "frame_gold", presence = FriendPresence.PLAYING)

    @Test
    fun memberTapOpensLatestPublicProfileAndRemovalDismissesIt() {
        val clan = mutableStateOf(clan())
        composeRule.setContent { FastToWinTheme { detail(clan.value) } }
        composeRule.onNodeWithTag("clan_detail_list").performScrollToNode(hasTestTag("clan_member:member"))
        composeRule.onNodeWithTag("clan_member:member").performClick()
        composeRule.onNodeWithTag("clan_member_profile_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("MEMBER01", substring = true).assertIsDisplayed()
        composeRule.onNode(hasTestTag("presence_playing") and hasAnyAncestor(hasTestTag("clan_member_profile_dialog"))).assertIsDisplayed()
        composeRule.runOnIdle { clan.value = clan.value.copy(members = listOf(member.copy(displayName = "Updated member", presence = FriendPresence.ONLINE))) }
        composeRule.onNode(hasText("Updated member") and hasAnyAncestor(hasTestTag("clan_member_profile_dialog"))).assertIsDisplayed()
        composeRule.onNode(hasTestTag("presence_online") and hasAnyAncestor(hasTestTag("clan_member_profile_dialog"))).assertIsDisplayed()
        composeRule.runOnIdle { clan.value = clan.value.copy(members = emptyList()) }
        composeRule.onNodeWithTag("clan_member_profile_dialog").assertDoesNotExist()
    }

    @Test
    fun kickControlOpensOnlyRemovalConfirmation() {
        composeRule.setContent { FastToWinTheme { detail(clan()) } }
        composeRule.onNodeWithTag("clan_detail_list").performScrollToNode(hasTestTag("clan_kick:member"))
        composeRule.onNodeWithTag("clan_kick:member").performClick()
        composeRule.onNodeWithTag("kick_clan_member_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("clan_member_profile_dialog").assertDoesNotExist()
    }

    @Test
    fun switchingClanDismissesMemberProfileEvenWhenMemberIdMatches() {
        val clan = mutableStateOf(clan())
        composeRule.setContent { FastToWinTheme { detail(clan.value) } }
        composeRule.onNodeWithTag("clan_detail_list").performScrollToNode(hasTestTag("clan_member:member"))
        composeRule.onNodeWithTag("clan_member:member").performClick()
        composeRule.onNodeWithTag("clan_member_profile_dialog").assertIsDisplayed()
        composeRule.runOnIdle { clan.value = clan.value.copy(id = "another-clan") }
        composeRule.onNodeWithTag("clan_member_profile_dialog").assertDoesNotExist()
    }

    @Test
    fun selfProfileHasNoFriendAction() {
        composeRule.setContent { FastToWinTheme {
            ClanMemberProfileDialog(member, member.userId, FriendsSnapshot(), false, null, null, {}, { _, _ -> }, {})
        } }
        composeRule.onNodeWithTag("clan_member_friend_action").assertDoesNotExist()
    }

    @Test
    fun socialErrorsRemainVisibleAndAllowRetry() {
        var retriedCode: String? = null
        composeRule.setContent { FastToWinTheme {
            ClanMemberProfileDialog(member, "owner", FriendsSnapshot(), false, null, "Please retry", { retriedCode = it }, { _, _ -> }, {})
        } }
        composeRule.onNodeWithText("Please retry").assertIsDisplayed()
        composeRule.onNodeWithTag("clan_member_friend_action").performClick()
        composeRule.runOnIdle { assertEquals(member.playerCode, retriedCode) }
    }

    @Test
    fun unavailableSocialSnapshotDisablesFriendActionAndOffersRefresh() {
        var refreshed = false
        composeRule.setContent { FastToWinTheme {
            ClanMemberProfileDialog(member, "owner", FriendsSnapshot(), false, null, "Please retry", {}, { _, _ -> }, {},
                isSocialReady = false, onRefreshSocial = { refreshed = true })
        } }
        composeRule.onNodeWithTag("clan_member_friend_action").assertIsNotEnabled()
        composeRule.onNodeWithTag("clan_member_social_retry").performClick()
        composeRule.runOnIdle { assertEquals(true, refreshed) }
    }

    @Test
    fun sendRequestUsesMemberCodeAndLoadingPreventsDuplicates() {
        var sentCode: String? = null
        val loading = mutableStateOf(false)
        composeRule.setContent { FastToWinTheme {
            ClanMemberProfileDialog(member, "owner", FriendsSnapshot(), loading.value, null, null,
                { sentCode = it; loading.value = true }, { _, _ -> }, {})
        } }
        composeRule.onNodeWithTag("clan_member_friend_action").performClick()
        composeRule.runOnIdle { assertEquals("MEMBER01", sentCode) }
        composeRule.onNodeWithTag("clan_member_friend_action").assertIsNotEnabled()
    }

    @Test
    fun incomingRequestAcceptsUsingExistingRequestId() {
        var response: Pair<String, Boolean>? = null
        composeRule.setContent { FastToWinTheme {
            ClanMemberProfileDialog(member, "owner", FriendsSnapshot(incomingRequests = listOf(
                FriendRequestSnapshot("incoming-1", member.userId, member.displayName, member.playerCode)
            )), false, null, null, {}, { id, accepted -> response = id to accepted }, {})
        } }
        composeRule.onNodeWithTag("clan_member_friend_action").performClick()
        composeRule.runOnIdle { assertEquals("incoming-1" to true, response) }
    }

    @androidx.compose.runtime.Composable
    private fun detail(clan: ClanSnapshot) = ClanDetailView("", "owner", clan, null, {}, { _, _ -> },
        { _, _, _ -> }, {}, {})

    private fun clan() = ClanSnapshot("clan", "Speed", "Together", "owner", listOf(member), 123)
}

package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hienthai.fastowin.protocol.ClanJoinRequestSnapshot
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClanSummarySnapshot
import com.hienthai.fastowin.ui.screens.ClanScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ClanScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun applicantCanRequestAnotherClanWhileExistingRequestStaysPending() {
        var requestedClanId: String? = null

        composeRule.setContent {
            FastToWinTheme {
                ClanScreen(
                    serverUrl = "ws://127.0.0.1:8080/game",
                    currentUserId = "applicant",
                    myClanId = null,
                    clanList = listOf(
                        ClanSummarySnapshot("clan-a", "Bang Tốc Độ", 3, 50, 120),
                        ClanSummarySnapshot("clan-b", "Bang Phản Xạ", 5, 50, 240)
                    ),
                    pendingJoinClanIds = setOf("clan-a"),
                    currentClan = null,
                    notice = null,
                    onCreateClan = { _, _ -> },
                    onJoinClan = { requestedClanId = it },
                    onLeaveClan = {},
                    onSearch = {},
                    onKickMember = { _, _ -> },
                    onRespondJoinRequest = { _, _, _ -> },
                    onUpdateLogo = { _, _ -> },
                    onClaimQuest = {},
                    onViewClan = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Đang chờ").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText("Xin vào").performClick()
        composeRule.runOnIdle { assertEquals("clan-b", requestedClanId) }
    }

    @Test
    fun clanOwnerCanApprovePendingApplicant() {
        var approval: Triple<String, String, Boolean>? = null
        val clan = ClanSnapshot(
            id = "clan-a",
            name = "Bang Tốc Độ",
            description = "Nhanh và chuẩn",
            ownerId = "owner",
            members = listOf(
                ClanMemberSnapshot("owner", "Bang chủ", ClanRole.LEADER, trophies = 100)
            ),
            trophies = 100,
            joinRequests = listOf(
                ClanJoinRequestSnapshot(
                    userId = "applicant",
                    displayName = "Người xin vào",
                    playerCode = "PLAYER01",
                    requestedAtEpochMillis = 1L
                )
            )
        )

        composeRule.setContent {
            FastToWinTheme {
                ClanScreen(
                    serverUrl = "ws://127.0.0.1:8080/game",
                    currentUserId = "owner",
                    myClanId = clan.id,
                    clanList = emptyList(),
                    pendingJoinClanIds = emptySet(),
                    currentClan = clan,
                    notice = null,
                    onCreateClan = { _, _ -> },
                    onJoinClan = {},
                    onLeaveClan = {},
                    onSearch = {},
                    onKickMember = { _, _ -> },
                    onRespondJoinRequest = { clanId, userId, accept ->
                        approval = Triple(clanId, userId, accept)
                    },
                    onUpdateLogo = { _, _ -> },
                    onClaimQuest = {},
                    onViewClan = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Yêu cầu tham gia (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Người xin vào").assertIsDisplayed()
        composeRule.onNodeWithText("Duyệt").performClick()
        composeRule.runOnIdle {
            assertEquals(Triple("clan-a", "applicant", true), approval)
        }
    }
}

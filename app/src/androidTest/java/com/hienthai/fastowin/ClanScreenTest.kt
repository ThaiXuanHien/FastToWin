package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun playerCanCreateClanFromArcadeDialog() {
        var createdClan: Pair<String, String>? = null

        composeRule.setContent {
            FastToWinTheme {
                ClanScreen(
                    serverUrl = "ws://127.0.0.1:8080/game",
                    currentUserId = "player",
                    myClanId = null,
                    clanList = emptyList(),
                    pendingJoinClanIds = emptySet(),
                    currentClan = null,
                    notice = null,
                    onCreateClan = { name, description -> createdClan = name to description },
                    onJoinClan = {},
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

        composeRule.onNodeWithTag("open_create_clan").performClick()
        composeRule.onNodeWithTag("create_clan_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("create_clan_name").performTextInput("Tia Chớp")
        composeRule.onNodeWithTag("create_clan_description").performTextInput("Nhanh và chính xác")
        composeRule.onNodeWithText("TẠO").performClick()

        composeRule.runOnIdle {
            assertEquals("Tia Chớp" to "Nhanh và chính xác", createdClan)
        }
    }

    @Test
    fun clanOwnerCanSelectLogoByTappingHero() {
        var selectedLogo: String? = null
        val clan = ClanSnapshot(
            id = "clan-a",
            name = "Bang Tốc Độ",
            description = "Nhanh và chuẩn",
            ownerId = "owner",
            members = listOf(ClanMemberSnapshot("owner", "Bang chủ", ClanRole.LEADER, trophies = 100)),
            trophies = 100,
            logoId = "shield"
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
                    onRespondJoinRequest = { _, _, _ -> },
                    onUpdateLogo = { _, logoId -> selectedLogo = logoId },
                    onClaimQuest = {},
                    onViewClan = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Chọn logo bang").performClick()
        composeRule.onNodeWithTag("clan_logo_dialog").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Logo Song kiếm").performClick()

        composeRule.runOnIdle { assertEquals("sword", selectedLogo) }
    }

    @Test
    fun clanOwnerMustConfirmBeforeKickingMember() {
        var kickedMember: Pair<String, String>? = null
        val clan = ClanSnapshot(
            id = "clan-a",
            name = "Bang Tốc Độ",
            description = "Nhanh và chuẩn",
            ownerId = "owner",
            members = listOf(
                ClanMemberSnapshot("owner", "Bang chủ", ClanRole.LEADER, trophies = 100),
                ClanMemberSnapshot("member", "Thành viên thử", ClanRole.MEMBER, trophies = 25)
            ),
            trophies = 125
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
                    onKickMember = { clanId, memberId -> kickedMember = clanId to memberId },
                    onRespondJoinRequest = { _, _, _ -> },
                    onUpdateLogo = { _, _ -> },
                    onClaimQuest = {},
                    onViewClan = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Mời Thành viên thử rời bang")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("kick_clan_member_dialog").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, kickedMember) }

        composeRule.onNodeWithTag("confirm_kick_clan_member").performClick()
        composeRule.runOnIdle { assertEquals("clan-a" to "member", kickedMember) }
    }
}

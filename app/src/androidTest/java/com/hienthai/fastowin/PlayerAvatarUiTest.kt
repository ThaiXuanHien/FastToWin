package com.hienthai.fastowin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test

class PlayerAvatarUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allAvatarFramesRenderWithAccessiblePlayerLabels() {
        val frames = listOf(
            "frame_default",
            "frame_bronze",
            "frame_gold",
            "frame_perfect",
            "frame_persistent"
        )

        composeRule.setContent {
            FastToWinTheme {
                Column {
                    frames.forEachIndexed { index, frameId ->
                        PlayerAvatar(
                            displayName = "Người chơi $index",
                            avatarId = "crown",
                            frameId = frameId,
                            size = 48.dp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        frames.forEachIndexed { index, frameId ->
            composeRule.onNodeWithTag("avatar_frame:$frameId").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Ảnh đại diện của Người chơi $index")
                .assertIsDisplayed()
        }
    }
}

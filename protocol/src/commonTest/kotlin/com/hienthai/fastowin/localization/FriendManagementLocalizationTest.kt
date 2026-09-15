package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class FriendManagementLocalizationTest {
    @Test
    fun friendManagementCopyDoesNotFallBackToEnglish() {
        val keys = listOf(
            TextKey.RemoveFriendDescription, TextKey.RemoveFriendAction,
            TextKey.GoBack, TextKey.BlockFriendDescription,
            TextKey.NewCount, TextKey.PlayerCodeLabel, TextKey.PlayerCodeShort,
            TextKey.BlockPlayerNamed, TextKey.ActiveFriends,
            TextKey.PendingReplies, TextKey.SentFriendInvitation,
            TextKey.BlockedPlayers, TextKey.Unblock, TextKey.PlayerActions,
            TextKey.InviteSent, TextKey.SendingInvitation,
            TextKey.InviteToRoom, TextKey.SocialHub,
        )
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts

        allLocalizationCatalogs.filterKeys {
            it != AppLanguage.ENGLISH && it != AppLanguage.VIETNAMESE
        }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}

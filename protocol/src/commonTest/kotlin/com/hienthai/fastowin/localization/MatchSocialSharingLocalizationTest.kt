package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class MatchSocialSharingLocalizationTest {
    @Test
    fun matchSocialAndResultSharingCopyDoesNotFallBackToEnglish() {
        val keys = listOf(
            TextKey.BlockPlayerTitle, TextKey.BlockPlayerDescription,
            TextKey.Block, TextKey.Processing,
            TextKey.Accept, TextKey.Decline,
            TextKey.Draw, TextKey.Win, TextKey.Loss,
            TextKey.MatchReward, TextKey.RankedNoRematch,
            TextKey.Rematch, TextKey.ResponseSeconds,
            TextKey.InviteRematch, TextKey.LoginForSocial,
            TextKey.AddFriend, TextKey.AcceptFriend,
            TextKey.FriendRequestSent, TextKey.AlreadyFriends,
            TextKey.Blocked, TextKey.LoadingInfo,
            TextKey.ShareResultCaption, TextKey.ShareTimeLabel,
            TextKey.ShareAccuracyLabel, TextKey.ShareSlogan,
            TextKey.ShareResultSheetTitle,
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

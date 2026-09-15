package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class AccountSecurityLocalizationTest {
    @Test
    fun securityAndSessionWarningsDoNotFallBackToEnglish() {
        val keys = setOf(
            TextKey.LogoutDescription, TextKey.AccountSecurityTitle,
            TextKey.AccountSecurityDescription, TextKey.ChangePassword,
            TextKey.CurrentPassword, TextKey.NewPasswordLabel,
            TextKey.ConfirmNewPasswordLabel, TextKey.NewPasswordMustDiffer,
            TextKey.DangerZone, TextKey.DeleteAccountWarning,
            TextKey.PasswordToConfirm, TextKey.RequestDeleteAccount,
            TextKey.LoginDevicesDescription, TextKey.NoActiveSessions,
            TextKey.CurrentDevice, TextKey.LogoutAllDevices,
            TextKey.LogoutAllDevicesTitle, TextKey.LogoutAllDevicesDescription,
            TextKey.RevokeCurrentDeviceDescription, TextKey.RevokeOtherDeviceDescription,
        )
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts
        allLocalizationCatalogs.filterKeys { it != AppLanguage.ENGLISH }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}

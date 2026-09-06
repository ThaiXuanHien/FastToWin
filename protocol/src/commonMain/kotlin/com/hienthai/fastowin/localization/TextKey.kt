package com.hienthai.fastowin.localization

/** Stable keys shared by clients and the backend. Never use player input as a key. */
enum class TextKey {
    Back, Cancel, Confirm, Retry, Close, Save, Delete, Loading, UnknownError, WelcomePlayer,
    SettingsTitle, LanguageTitle, ChooseLanguageTitle, SystemLanguage, ResolvedSystemLanguage,
    AppearanceTitle, AppearanceSubtitle,

    Gold, Gems, Notifications, ViewMoreCount,
    UpdateGameTitle, UpdateAvailableSubtitle, UpdateHeroTitle, UpdateDescription,
    UpdateAutoReload, UpdateProgressPreserved, UpdateNow, Later,
    MaintenanceBadge, MaintenanceTitle, MaintenanceDefaultMessage, MaintenanceAutoResume,
    MaintenanceNoAction,
    OfflineBadge, OfflineTitle, OfflineDescription, OfflineCheckNetwork, OfflineNotMaintenance,
    RetryConnection, OfflinePractice, NoNetwork,
    TutorialTitle, Skip, TutorialStep,
    TutorialFindNumbersTitle, TutorialFindNumbersDescription, TutorialFindNumbersHint,
    TutorialSharedTargetTitle, TutorialSharedTargetDescription, TutorialSharedTargetHint,
    TutorialChooseModeTitle, TutorialChooseModeDescription, TutorialChooseModeHint,
    StartPlaying, Continue,

    VerifyEmailTitle, VerifyEmailDescription, DevVerificationCode, VerificationCode,
    Verifying, Verify, ResendCode, Logout,
    AuthWelcomeDescription, AuthLoginTitle, Login, LoggingIn, CreateAccount, PlayAsGuest, Password,
    ForgotPassword, ResetPasswordTitle, ResetPasswordDescription, Sending, SendResetCode,
    DevResetCode, ResetCode, NewPassword, ConfirmNewPassword, Updating, ResetPasswordAction,
    UseDifferentEmail, DisplayName, GenderDefaultAvatar, Male, Female, Player,
    PasswordWithMinimum, ConfirmPassword, Creating, SaveGuestAccountTitle,
    SaveGuestAccountDescription, Saving, SaveAndCreateAccount, Email, HidePassword,
    ShowPassword, PasswordTooShort, PasswordTooLong, PasswordMismatch,

    Rooms, Leaderboard, Clan, Account,
    FinishCurrentMatchBeforeChallenge, ModeUnlockLevel, CannotOpenChallenge, Understood,
    ChoosePracticeMode, ShareRoom
}

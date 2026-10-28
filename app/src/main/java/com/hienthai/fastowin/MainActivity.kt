package com.hienthai.fastowin

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import com.hienthai.fastowin.platform.LocalAndroidRewardedAdConfiguration
import com.hienthai.fastowin.platform.RewardedAdConfiguration
import com.hienthai.fastowin.platform.RewardedAdMode
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hienthai.fastowin.data.network.AndroidResumeTokenStore
import com.hienthai.fastowin.data.network.AndroidAuthSessionStore
import com.hienthai.fastowin.data.preferences.AndroidAppPreferencesStore
import com.hienthai.fastowin.platform.AppDeepLinkRouter
import com.hienthai.fastowin.state.localAuthDiagnosticSink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val tokenStore = AndroidResumeTokenStore(applicationContext)
        val authSessionStore = AndroidAuthSessionStore(applicationContext)
        val preferencesStore = AndroidAppPreferencesStore(applicationContext)
        val authDiagnostics = localAuthDiagnosticSink(BuildConfig.AUTH_DIAGNOSTICS_ENABLED) {
            android.util.Log.i("FTWAuthQA", it)
        }
        intent?.dataString?.let(AppDeepLinkRouter::openUri)
        val adMode = BuildConfig.ADMOB_MODE.takeIf { it.isNotEmpty() }
            ?.let { value -> RewardedAdMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } }
            ?: if (BuildConfig.DEBUG && BuildConfig.APP_ENV == "dev") RewardedAdMode.SIMULATED else RewardedAdMode.DISABLED
        val adConfiguration = RewardedAdConfiguration(adMode, BuildConfig.ADMOB_APP_ID, BuildConfig.ADMOB_AD_UNIT_ID,
            BuildConfig.ADMOB_TEST_DEVICE_IDS.split(',').filter(String::isNotBlank))
        setContent {
            CompositionLocalProvider(LocalAndroidRewardedAdConfiguration provides adConfiguration) {
            FastToWinApp(
                serverUrl = BuildConfig.GAME_SERVER_URL,
                resumeTokenStore = tokenStore,
                authSessionStore = authSessionStore,
                preferencesStore = preferencesStore,
                devicePlatform = "android",
                authDiagnosticSink = authDiagnostics,
                fcmToken = applicationContext.getSharedPreferences("fcm", android.content.Context.MODE_PRIVATE).getString("fcm_token", null)
            )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(AppDeepLinkRouter::openUri)
    }
}

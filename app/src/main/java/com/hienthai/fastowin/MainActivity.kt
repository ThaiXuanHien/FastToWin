package com.hienthai.fastowin

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hienthai.fastowin.data.network.AndroidResumeTokenStore
import com.hienthai.fastowin.data.network.AndroidAuthSessionStore
import com.hienthai.fastowin.data.preferences.AndroidAppPreferencesStore
import com.hienthai.fastowin.platform.AppDeepLinkRouter

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
        intent?.dataString?.let(AppDeepLinkRouter::openUri)
        setContent {
            FastToWinApp(
                serverUrl = BuildConfig.GAME_SERVER_URL,
                resumeTokenStore = tokenStore,
                authSessionStore = authSessionStore,
                preferencesStore = preferencesStore,
                devicePlatform = "android",
                fcmToken = applicationContext.getSharedPreferences("fcm", android.content.Context.MODE_PRIVATE).getString("fcm_token", null)
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(AppDeepLinkRouter::openUri)
    }
}

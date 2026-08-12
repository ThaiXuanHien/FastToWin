package com.hienthai.fastowin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hienthai.fastowin.data.network.AndroidResumeTokenStore
import com.hienthai.fastowin.data.network.AndroidAuthSessionStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tokenStore = AndroidResumeTokenStore(applicationContext)
        val authSessionStore = AndroidAuthSessionStore(applicationContext)
        setContent {
            FastToWinApp(
                serverUrl = BuildConfig.GAME_SERVER_URL,
                resumeTokenStore = tokenStore,
                authSessionStore = authSessionStore,
                devicePlatform = "android"
            )
        }
    }
}

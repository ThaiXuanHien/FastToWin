package com.hienthai.fastowin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.hienthai.fastowin.navigation.FastToWinNavHost
import com.hienthai.fastowin.ui.theme.FastToWinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FastToWinTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FastToWinNavHost(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
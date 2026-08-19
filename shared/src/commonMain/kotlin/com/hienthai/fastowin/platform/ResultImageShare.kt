package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable

data class ResultShareContent(
    val result: String,
    val playerName: String,
    val playerScore: Int,
    val opponentName: String,
    val opponentScore: Int,
    val gameMode: String,
    val matchType: String,
    val duration: String,
    val accuracy: String,
    val elo: String? = null
) {
    val caption: String
        get() = "Kết quả Fast To Win: $playerName $playerScore – $opponentScore $opponentName • $gameMode"
}

fun interface ResultImageSharer {
    fun share(content: ResultShareContent): Result<Unit>
}

@Composable
expect fun rememberResultImageSharer(): ResultImageSharer

fun interface TextSharer {
    fun share(text: String, title: String): Result<Unit>
}

@Composable
expect fun rememberTextSharer(): TextSharer

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
    val elo: String? = null,
    val caption: String,
    val timeLabel: String,
    val accuracyLabel: String,
    val slogan: String,
    val shareSheetTitle: String
)

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

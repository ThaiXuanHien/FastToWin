package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.ui.components.clanEmblemImageVector

@PreviewTest
@Preview(name = "clan_emblem_artwork_phone", widthDp = 375, heightDp = 812)
@Preview(name = "clan_emblem_artwork_desktop", widthDp = 1024, heightDp = 640)
@Composable
fun clanEmblemArtwork() = ArcadeScreenshotFrame {
    val emblemIds = listOf(
        "shield", "flag", "sword", "wolf", "eagle", "lion", "dragon", "phoenix", "crown", "kirin"
    )
    BoxWithConstraints(Modifier.padding(16.dp)) {
        val columns = if (maxWidth >= 600.dp) 5 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Clan emblem artwork",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text("24 / 36 / 56 dp", style = MaterialTheme.typography.bodySmall)
            emblemIds.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { id ->
                        Surface(
                            modifier = Modifier.weight(1f).height(124.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(24, 36, 56).forEach { size ->
                                        Icon(
                                            imageVector = clanEmblemImageVector(id),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(size.dp)
                                        )
                                    }
                                }
                                Text(id, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

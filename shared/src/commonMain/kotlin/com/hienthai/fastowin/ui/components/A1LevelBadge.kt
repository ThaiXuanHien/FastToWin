package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.ui.theme.A1Palette

/** Huy hiệu cấp tròn xanh ở góc avatar (".lvl" của thiết kế). */
@Composable
fun A1LevelBadge(level: Int, modifier: Modifier = Modifier) {
    val description = localized(TextKey.Level, "level" to level)
    Text(
        level.toString(),
        style = MaterialTheme.typography.labelMedium,
        fontFamily = MaterialTheme.typography.titleSmall.fontFamily,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = modifier
            .semantics { contentDescription = description }
            .heightIn(min = 26.dp)
            .widthIn(min = 26.dp)
            .background(Color(0xFF3D8BFF), RoundedCornerShape(13.dp))
            .border(2.dp, A1Palette.Line, RoundedCornerShape(13.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp)
    )
}

package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.PlayQuotaSnapshot
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.LocalNewOnlineIntentAllowed
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.theme.A1Palette

/** Ba lựa chọn đầu màn Chọn chế độ (thiết kế A1): luyện tập offline, đấu thường, xếp hạng. */
internal enum class PlayKind { PRACTICE, CASUAL, RANKED }

/** Chế độ hiện chọn được ở màn Chọn chế độ (bỏ chế độ cũ và 2v2 như hộp thoại trước đây). */
internal val selectableGameModes: List<GameMode>
    get() = GameMode.entries.filter { !it.isLegacy && it != GameMode.TEAM_2V2 }

/**
 * Màn "Chọn chế độ" (tab Chơi) theo thiết kế A1: chọn kiểu trận, chọn chế độ trên lưới thẻ,
 * xem lượt chơi còn lại và bấm CHƠI. Luyện tập mở màn luyện tập offline (không tốn lượt).
 */
@Composable
fun ModeSelectContent(
    playerLevel: Int,
    quota: PlayQuotaSnapshot?,
    onPlay: (GameMode, MatchType) -> Unit,
    onPractice: () -> Unit,
    onQuotaExhausted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var kind by rememberSaveable { mutableStateOf(PlayKind.RANKED) }
    var selected by rememberSaveable { mutableStateOf(GameMode.ORDER) }
    val onlineAllowed = LocalNewOnlineIntentAllowed.current
    val modes = selectableGameModes
    val chosen = selected.takeIf { playerLevel >= it.unlockLevel } ?: GameMode.ORDER

    Column(modifier = modifier.fillMaxSize().testTag("mode_select_screen")) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ArcadeSegmentedControl(
                labels = listOf(localized(TextKey.ModeTabPractice), localized(TextKey.Casual), localized(TextKey.Ranked)),
                selectedIndex = kind.ordinal,
                onSelected = { kind = PlayKind.entries[it] },
                itemTestTag = { "play_kind:${PlayKind.entries[it].name}" }
            )
            com.hienthai.fastowin.ui.components.A1OutlinedText(
                text = localized(
                    when (kind) {
                        PlayKind.PRACTICE -> TextKey.ModePracticeHint
                        PlayKind.CASUAL -> TextKey.ModeCasualHint
                        PlayKind.RANKED -> TextKey.ModeRankedHint
                    }
                ),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                outlineWidth = 2.dp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            // Lưới 2 cột; thẻ lẻ cuối cùng trải hết hàng như thiết kế.
            modes.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { mode ->
                        ModeCard(
                            mode = mode,
                            selected = mode == chosen,
                            unlocked = playerLevel >= mode.unlockLevel,
                            wide = row.size == 1,
                            onClick = { selected = mode },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (kind != PlayKind.PRACTICE) PlayCostBar(quota)
            val title = localized(chosen.titleKey)
            ArcadeActionButton(
                label = if (kind == PlayKind.PRACTICE) localized(TextKey.ModePracticeButton)
                else localized(TextKey.ModePlayButton, "mode" to title.uppercase()),
                style = ArcadeActionStyle.GOLD,
                hero = true,
                enabled = kind == PlayKind.PRACTICE || onlineAllowed,
                onClick = {
                    when {
                        kind == PlayKind.PRACTICE -> onPractice()
                        quota?.remainingMatches == 0 -> onQuotaExhausted()
                        else -> onPlay(chosen, if (kind == PlayKind.RANKED) MatchType.RANKED else MatchType.CASUAL)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag("mode_select_play")
            )
        }
    }
}

/** Thẻ một chế độ: icon màu, tên, mô tả; viền vàng khi chọn; phủ khóa khi chưa đủ cấp. */
@Composable
private fun ModeCard(
    mode: GameMode,
    selected: Boolean,
    unlocked: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    val (iconBg, iconFg) = mode.a1IconColors()
    val title = localized(mode.titleKey)
    val lockText = localized(TextKey.UnlockAtLevel, "level" to mode.unlockLevel)
    Box(
        modifier = modifier
            .testTag("game_mode:${mode.name}")
            .then(if (selected) Modifier.border(4.dp, A1Palette.Yellow2, RoundedCornerShape(26.dp)).padding(6.dp) else Modifier.padding(6.dp))
            .a1Card()
            .clip(shape)
            .clickable(enabled = unlocked, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (!unlocked) {
                    disabled()
                    stateDescription = lockText
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (wide) 100.dp else 132.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBg, RoundedCornerShape(15.dp))
                    .border(2.dp, A1Palette.Line, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(mode.a1Icon(), contentDescription = null, tint = iconFg, modifier = Modifier.size(28.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = A1Palette.Ink)
            Text(
                localized(mode.descriptionKey),
                style = MaterialTheme.typography.bodySmall,
                color = A1Palette.Muted
            )
        }
        if (selected && unlocked) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = A1Palette.Green2,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(28.dp)
            )
        }
        if (!unlocked) {
            Column(
                modifier = Modifier.matchParentSize().background(A1Palette.SurfaceAlt.copy(alpha = 0.94f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = A1Palette.Muted, modifier = Modifier.size(30.dp))
                Text(lockText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = A1Palette.Muted)
            }
        }
    }
}

/** Thanh "Tốn 1 lượt chơi · Còn N · hồi lúc 00:00" phía trên nút CHƠI. */
@Composable
private fun PlayCostBar(quota: PlayQuotaSnapshot?) {
    val left = quota?.remainingMatches
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
            .border(3.dp, A1Palette.Line, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("mode_select_cost"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        A1TicketIcon()
        Text(
            localized(TextKey.ModeCostOnePlay),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = A1Palette.Ink,
            modifier = Modifier.weight(1f)
        )
        if (left != null) {
            Text(
                localized(TextKey.ModePlaysLeft, "count" to left),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = A1Palette.Muted
            )
        }
    }
}

/** Vé lượt chơi màu hồng có hai khía tròn hai bên và đường xé nét đứt (".ticket" của thiết kế). */
@Composable
internal fun A1TicketIcon(width: Dp = 30.dp, height: Dp = 20.dp) {
    Canvas(Modifier.size(width, height)) {
        val notch = 4.5.dp.toPx()
        val r = 4.dp.toPx()
        val body = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
        }
        val cut = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(0f, size.height / 2), notch))
            addOval(androidx.compose.ui.geometry.Rect(Offset(size.width, size.height / 2), notch))
        }
        val shape = androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, body, cut)
        drawPath(shape, Brush.verticalGradient(listOf(Color(0xFFFF9FC4), Color(0xFFFF4F8A))))
        drawPath(shape, Color(0xFF8A1F55), style = Stroke(2.dp.toPx()))
        val x = size.width * 0.64f
        drawLine(
            Color.White, Offset(x, 4.dp.toPx()), Offset(x, size.height - 4.dp.toPx()), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
        )
    }
}

/** Icon chế độ theo thiết kế A1 (Cổ điển là ô lưới, Combo là tia chớp…). */
private fun GameMode.a1Icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    GameMode.ORDER -> androidx.compose.material.icons.Icons.Rounded.GridView
    GameMode.COMBO -> androidx.compose.material.icons.Icons.Rounded.Bolt
    GameMode.RANDOM_TARGET -> androidx.compose.material.icons.Icons.Rounded.Shuffle
    GameMode.SPEED_UP -> androidx.compose.material.icons.Icons.Rounded.Speed
    GameMode.SURVIVAL -> androidx.compose.material.icons.Icons.Rounded.Favorite
    else -> androidx.compose.material.icons.Icons.Rounded.Timer
}

/** Màu icon chế độ theo thiết kế: Cổ điển xanh, Combo vàng, Ngẫu nhiên tím, Tăng tốc đỏ, Sinh tồn xanh lá. */
private fun GameMode.a1IconColors(): Pair<Color, Color> = when (this) {
    GameMode.ORDER -> A1Palette.Blue2 to Color.White
    GameMode.COMBO -> A1Palette.Yellow2 to Color(0xFF6A3300)
    GameMode.RANDOM_TARGET -> A1Palette.Purple2 to Color.White
    GameMode.SPEED_UP -> A1Palette.Red2 to Color.White
    GameMode.SURVIVAL -> A1Palette.Green2 to Color.White
    else -> A1Palette.Ink to Color.White
}

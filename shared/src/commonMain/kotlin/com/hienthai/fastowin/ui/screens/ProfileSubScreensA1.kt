package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.AchievementSnapshot
import com.hienthai.fastowin.protocol.CosmeticSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.GameModeStatisticsSnapshot
import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.A1Tag
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeChoiceChip
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
import com.hienthai.fastowin.ui.format.fullNumber
import com.hienthai.fastowin.ui.theme.A1Palette

/*
 * Các màn con của tab Tôi theo thiết kế A1 (bảng "Tài khoản – màn con 1"):
 * Thống kê, Lịch sử thi đấu, Bộ sưu tập.
 */

/** Đường gạch đứt giữa các dòng trong một thẻ (".lr" của thiết kế). */
internal fun Modifier.a1DashedDivider(show: Boolean): Modifier = if (!show) this else drawBehind {
    val y = size.height - 1.dp.toPx()
    drawLine(
        A1Palette.SurfaceAlt2,
        Offset(3.dp.toPx(), y),
        Offset(size.width - 3.dp.toPx(), y),
        strokeWidth = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    )
}

/** Thẻ nhỏ (".card.soft"): bo 18dp, viền navy 3dp, đế 3dp. */
internal fun Modifier.a1SoftCard(background: Color = Color.White): Modifier =
    a1Card(radius = 18.dp, depth = 3.dp, background = background)

/** Ô icon vuông bo góc (".ico"): nền một màu, viền navy 2dp. */
@Composable
internal fun A1IconTile(
    icon: ImageVector,
    background: Color,
    tint: Color = Color.White,
    size: Dp = 40.dp,
    radius: Dp = 12.dp,
    iconSize: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(size)
            .background(background, RoundedCornerShape(radius))
            .border(2.dp, A1Palette.Line, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

private val A1YellowIconInk = Color(0xFF6A3300)

/** Màu + icon của từng chế độ, giống màn Chọn chế độ. */
private fun ProtocolGameMode.a1Tile(): Triple<ImageVector, Color, Color> = when (this) {
    ProtocolGameMode.ORDER -> Triple(Icons.Rounded.GridView, A1Palette.Blue2, Color.White)
    ProtocolGameMode.COMBO -> Triple(Icons.Rounded.Bolt, A1Palette.Yellow2, A1YellowIconInk)
    ProtocolGameMode.RANDOM_TARGET -> Triple(Icons.Rounded.Shuffle, A1Palette.Purple2, Color.White)
    ProtocolGameMode.SPEED_UP -> Triple(Icons.Rounded.Speed, A1Palette.Red2, Color.White)
    ProtocolGameMode.SURVIVAL -> Triple(Icons.Rounded.Favorite, A1Palette.Green2, Color.White)
    ProtocolGameMode.TEAM_2V2 -> Triple(Icons.Rounded.Groups, A1Palette.Blue2, Color.White)
    ProtocolGameMode.TIME_BONUS, ProtocolGameMode.TIME_ATTACK -> Triple(Icons.Rounded.Timer, A1Palette.Ink, Color.White)
}

/** Ngưỡng mode: thứ tự hiển thị theo cấp mở khóa như màn Chọn chế độ. */
private val ProtocolGameMode.unlockOrder: Int
    get() = when (this) {
        ProtocolGameMode.ORDER -> 1
        ProtocolGameMode.TIME_ATTACK -> 1
        ProtocolGameMode.RANDOM_TARGET -> 3
        ProtocolGameMode.TIME_BONUS -> 5
        ProtocolGameMode.TEAM_2V2 -> 5
        ProtocolGameMode.SPEED_UP -> 7
        ProtocolGameMode.SURVIVAL -> 10
        ProtocolGameMode.COMBO -> 12
    }

@Composable
private fun SmallMuted(text: String, modifier: Modifier = Modifier, maxLines: Int = 4) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
        fontWeight = FontWeight.Normal,
        color = A1Palette.Muted,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RowTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 19.sp),
        fontWeight = FontWeight.ExtraBold,
        color = A1Palette.Ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CardHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp),
        fontWeight = FontWeight.ExtraBold,
        color = A1Palette.Ink
    )
}

@Composable
private fun EmptyCard(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().a1Card().padding(horizontal = 18.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = A1Palette.Muted
    )
}

// ---------------------------------------------------------------- Thống kê

/** Màn "Thống kê": 4 ô số liệu, thẻ "Theo chế độ" và thẻ "Thành tích". */
@Composable
internal fun A1StatisticsContent(profile: PlayerProfileSnapshot) {
    val stats = profile.statistics
    val winRate = if (stats.totalMatches == 0) 0 else stats.wins * 100 / stats.totalMatches
    var openAchievement by remember { mutableStateOf<AchievementSnapshot?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth().testTag("profile_statistics_content"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        val tiles = listOf(
            fullNumber(stats.totalMatches.toLong()) to localized(TextKey.StatMatchesPlayed),
            "$winRate%" to localized(TextKey.StatWinRate),
            stats.bestWinStreak.toString() to localized(TextKey.StatBestWinStreak),
            formatReactionSeconds(stats.averageReactionMillis) to localized(TextKey.StatAverageReaction)
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(2).forEach { pair ->
                // Hai ô cùng hàng luôn cao bằng nhau, kể cả khi nhãn xuống dòng ở chữ lớn.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (value, label) -> StatTile(value, label, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }

        // Chế độ chơi nhiều nhất đứng đầu (như thiết kế: Cổ điển 612, Combo 280…).
        val modes = profile.modeStatistics.sortedWith(compareByDescending<GameModeStatisticsSnapshot> { it.totalMatches }.thenBy { it.gameMode.unlockOrder })
        if (modes.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().a1Card().padding(vertical = 4.dp).testTag("mode_statistics")) {
                CardHeading(
                    localized(TextKey.StatsByMode),
                    Modifier.fillMaxWidth().a1DashedDivider(true).padding(horizontal = 16.dp, vertical = 12.dp)
                )
                modes.forEachIndexed { index, mode -> ModeStatRow(mode, showDivider = index < modes.lastIndex) }
            }
        }

        AchievementsCard(profile.achievements, onOpen = { openAchievement = it })
    }
    openAchievement?.let { AchievementDialog(it, onDismiss = { openAchievement = null }) }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 100.dp)
            .a1SoftCard()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 30.sp),
            fontWeight = FontWeight.ExtraBold,
            color = A1Palette.Ink,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            fontWeight = FontWeight.Bold,
            color = A1Palette.Muted,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun ModeStatRow(statistics: GameModeStatisticsSnapshot, showDivider: Boolean) {
    val winRate = if (statistics.totalMatches == 0) 0 else statistics.wins * 100 / statistics.totalMatches
    val (icon, color, tint) = statistics.gameMode.a1Tile()
    val bestLabel = localized(TextKey.HighScore)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mode_statistics:${statistics.gameMode.name}")
            .a1DashedDivider(showDivider)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        A1IconTile(icon, color, tint)
        Column(Modifier.weight(1f)) {
            RowTitle(statistics.gameMode.localizedDisplayName())
            SmallMuted(localized(TextKey.StatsModeSummary, "matches" to fullNumber(statistics.totalMatches.toLong()), "rate" to winRate))
        }
        Text(
            statistics.highestScore.toString(),
            modifier = Modifier.semantics { contentDescription = "$bestLabel ${statistics.highestScore}" },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            fontWeight = FontWeight.ExtraBold,
            color = A1Palette.Ink
        )
    }
}

/** Icon + màu theo nhóm thành tích (thắng, phản xạ, hoàn hảo, điểm danh, bang, cấp…). */
private fun achievementTile(code: String): Triple<ImageVector, Color, Color> = when {
    code == "FIRST_WIN" -> Triple(Icons.Rounded.EmojiEvents, A1Palette.Yellow2, A1YellowIconInk)
    code.startsWith("RESPONSE_") || code.startsWith("SPEED") -> Triple(Icons.Rounded.Bolt, A1Palette.Blue2, Color.White)
    code.startsWith("PERFECT") || code.startsWith("ACCURACY") -> Triple(Icons.Rounded.Verified, A1Palette.Green2, Color.White)
    code.startsWith("WIN") || code.startsWith("RANKED_WINS") -> Triple(Icons.Rounded.MilitaryTech, A1Palette.Purple2, Color.White)
    code.startsWith("CHECKIN") || code.startsWith("DAILY_STREAK") -> Triple(Icons.Rounded.CalendarMonth, A1Palette.Red2, Color.White)
    code.startsWith("CLAN") -> Triple(Icons.Rounded.Groups, A1Palette.Blue2, Color.White)
    code.startsWith("PLAYER_LEVEL") -> Triple(Icons.Rounded.WorkspacePremium, A1Palette.Green2, Color.White)
    else -> Triple(Icons.Rounded.WorkspacePremium, A1Palette.Yellow2, A1YellowIconInk)
}

@Composable
private fun AchievementsCard(achievements: List<AchievementSnapshot>, onOpen: (AchievementSnapshot) -> Unit) {
    val unlocked = achievements.count { it.unlocked }
    Column(
        Modifier.fillMaxWidth().a1Card().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardHeading(localized(TextKey.AchievementsTitle), Modifier.weight(1f))
            if (achievements.isNotEmpty()) A1Tag("$unlocked/${achievements.size}", A1TagTone.YELLOW)
        }
        if (achievements.isEmpty()) {
            SmallMuted(localized(TextKey.NoAchievements))
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Đã mở khóa đứng trước, chưa mở khóa hiện ổ khóa đen.
                achievements.sortedByDescending { it.unlocked }.forEach { achievement ->
                    val title = localizedAchievementTitle(achievement)
                    val state = if (achievement.unlocked) localized(TextKey.Unlocked) else localized(TextKey.Locked)
                    val (icon, color, tint) = if (achievement.unlocked) achievementTile(achievement.code)
                        else Triple(Icons.Rounded.Lock, A1Palette.Ink, Color.White)
                    A1IconTile(
                        icon = icon,
                        background = color,
                        tint = tint,
                        size = 52.dp,
                        radius = 16.dp,
                        iconSize = 28.dp,
                        modifier = Modifier
                            .testTag("profile_achievement:${achievement.code}")
                            .clickable(role = Role.Button, onClickLabel = title) { onOpen(achievement) }
                            .semantics {
                                contentDescription = title
                                stateDescription = state
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementDialog(achievement: AchievementSnapshot, onDismiss: () -> Unit) {
    ArcadeDialog(
        title = localizedAchievementTitle(achievement),
        subtitle = localizedAchievementDescription(achievement),
        onDismissRequest = onDismiss
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (achievement.unlocked) {
                A1Tag(localized(TextKey.Unlocked), A1TagTone.GREEN)
            } else {
                A1Tag("${achievement.progress.coerceAtMost(achievement.target)}/${achievement.target}", A1TagTone.BLUE)
            }
            ArcadeActionButton(
                label = localized(TextKey.Close).uppercase(),
                onClick = onDismiss,
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** "0.9s": phản xạ trung bình theo giây, một chữ số thập phân. */
internal fun formatReactionSeconds(millis: Long): String {
    if (millis <= 0L) return "--"
    val tenths = (millis + 50) / 100
    return "${tenths / 10}.${tenths % 10}s"
}

// ---------------------------------------------------------------- Lịch sử thi đấu

@Composable
internal fun A1MatchHistoryContent(
    profile: PlayerProfileSnapshot,
    isExternalProfile: Boolean,
    historyFilter: MatchHistoryOutcome?,
    onFilterChange: (MatchHistoryOutcome?) -> Unit,
    onOpenMatchDetail: (String) -> Unit
) {
    var visibleMatchCount by remember(historyFilter) { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag("recent_match_tabs"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple(null, TextKey.RecentFilterAll, "history_filter_all"),
                Triple(MatchHistoryOutcome.WIN, TextKey.RecentFilterWins, "history_filter_win"),
                Triple(MatchHistoryOutcome.LOSS, TextKey.RecentFilterLosses, "history_filter_loss")
            ).forEach { (outcome, label, tag) ->
                ArcadeChoiceChip(
                    label = localized(label),
                    selected = historyFilter == outcome,
                    onClick = { onFilterChange(outcome) },
                    modifier = Modifier.testTag(tag)
                )
            }
        }
        val filteredMatches = profile.recentMatches.filter { historyFilter == null || it.outcome == historyFilter }
        val visibleMatches = filteredMatches.take(visibleMatchCount)
        when {
            profile.recentMatches.isEmpty() -> EmptyCard(localized(TextKey.NoCompletedMatches))
            filteredMatches.isEmpty() -> EmptyCard(localized(TextKey.NoMatchesForFilter))
            else -> {
                Column(Modifier.fillMaxWidth().a1Card().padding(vertical = 4.dp)) {
                    visibleMatches.forEachIndexed { index, match ->
                        MatchHistoryRow(
                            match = match,
                            showDivider = index < visibleMatches.lastIndex,
                            onClick = if (isExternalProfile) null else ({ onOpenMatchDetail(match.matchId) })
                        )
                    }
                }
                ArcadeLoadMoreButton(
                    visibleItemCount = visibleMatches.size,
                    totalItemCount = filteredMatches.size,
                    onLoadMore = { visibleMatchCount = nextArcadePageItemCount(visibleMatchCount, filteredMatches.size) },
                    testTag = "recent_matches_load_more"
                )
            }
        }
    }
}

@Composable
private fun MatchHistoryRow(match: MatchHistorySnapshot, showDivider: Boolean, onClick: (() -> Unit)?) {
    val (result, tone) = when (match.outcome) {
        MatchHistoryOutcome.WIN -> localized(TextKey.Win) to A1TagTone.GREEN
        MatchHistoryOutcome.LOSS -> localized(TextKey.Loss) to A1TagTone.RED
        MatchHistoryOutcome.DRAW -> localized(TextKey.Draw) to A1TagTone.BLUE
    }
    val ranked = match.matchType == MatchType.RANKED
    val (eloText, eloColor) = when {
        // Trận thường không đổi Elo: gạch ngang, màu theo kết quả.
        !ranked -> "—" to when (match.outcome) {
            MatchHistoryOutcome.WIN -> A1Palette.GreenText
            MatchHistoryOutcome.LOSS -> A1Palette.RedText
            MatchHistoryOutcome.DRAW -> A1Palette.Muted
        }
        match.eloChange > 0 -> "+${match.eloChange}" to A1Palette.GreenText
        match.eloChange < 0 -> "−${-match.eloChange}" to A1Palette.RedText
        else -> "0" to A1Palette.Muted
    }
    val eloDescription = if (ranked) "Elo $eloText" else localized(TextKey.NoEloChange)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("match_history:${match.matchId}")
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .a1DashedDivider(showDivider)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        A1Tag(result, tone, Modifier.widthIn(min = 56.dp))
        Column(Modifier.weight(1f)) {
            RowTitle(localized(TextKey.MatchVsOpponent, "name" to match.opponentName))
            SmallMuted(
                listOf(
                    match.gameMode.localizedDisplayName(),
                    localized(if (ranked) TextKey.Ranked else TextKey.CasualShort),
                    relativeDayTime(match.endedAtEpochMillis)
                ).joinToString(" · ")
            )
        }
        Text(
            eloText,
            modifier = Modifier.semantics { contentDescription = eloDescription },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            fontWeight = FontWeight.ExtraBold,
            color = eloColor
        )
    }
}

/** "5 phút trước", "1 giờ trước", "hôm qua", "2 ngày trước". */
@Composable
internal fun relativeDayTime(timestampMillis: Long, nowMillis: Long = epochMillis()): String {
    val elapsed = (nowMillis - timestampMillis).coerceAtLeast(0L)
    val days = elapsed / 86_400_000L
    return when {
        elapsed < 60_000L -> localized(TextKey.JustNow)
        elapsed < 3_600_000L -> localized(TextKey.MinutesAgo, "count" to elapsed / 60_000L)
        days < 1L -> localized(TextKey.HoursAgo, "count" to elapsed / 3_600_000L)
        days == 1L -> localized(TextKey.YesterdayShort)
        else -> localized(TextKey.DaysAgo, "count" to days)
    }
}

// ---------------------------------------------------------------- Bộ sưu tập

/** Màn "Bộ sưu tập": thanh chọn Khung / Danh hiệu, khung xếp lưới 3 cột. */
@Composable
internal fun A1CollectionContent(
    state: GameState,
    profile: PlayerProfileSnapshot,
    canEdit: Boolean,
    isLoading: Boolean,
    onEquipCosmetics: (String, String) -> Unit
) {
    val cosmetics = profile.progression.cosmetics
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ArcadeSegmentedControl(
            labels = listOf(localized(TextKey.Frames), localized(TextKey.Titles)),
            selectedIndex = tab,
            onSelected = { tab = it },
            modifier = Modifier.testTag("collection_tabs"),
            itemTestTag = { if (it == 0) "collection_tab_frames" else "collection_tab_titles" }
        )
        if (cosmetics.isEmpty()) {
            EmptyCard(localized(TextKey.EmptyCollection))
            return@Column
        }
        val equippedFrame = cosmetics.firstOrNull { it.type == CosmeticType.FRAME && it.equipped }?.id ?: "frame_default"
        val equippedTitle = cosmetics.firstOrNull { it.type == CosmeticType.TITLE && it.equipped }?.id ?: "title_rookie"
        val equippingId = state.equippingCosmeticId
        fun canEquip(cosmetic: CosmeticSnapshot) =
            canEdit && cosmetic.unlocked && !cosmetic.equipped && !isLoading && equippingId == null
        if (tab == 0) {
            val frames = cosmetics.filter { it.type == CosmeticType.FRAME }
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("collection_frames")) {
                // Điện thoại: 3 cột như thiết kế; màn rộng thêm cột để thẻ không phình to.
                val columns = maxOf(3, (maxWidth / 150.dp).toInt())
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    frames.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { frame ->
                                FrameCard(
                                    cosmetic = frame,
                                    profile = profile,
                                    isEquipping = equippingId == frame.id,
                                    canEquip = canEquip(frame),
                                    onClick = { onEquipCosmetics(frame.id, equippedTitle) },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        } else {
            val titles = cosmetics.filter { it.type == CosmeticType.TITLE }
            Column(Modifier.fillMaxWidth().a1Card().padding(vertical = 4.dp).testTag("collection_titles")) {
                titles.forEachIndexed { index, title ->
                    TitleRow(
                        cosmetic = title,
                        isEquipping = equippingId == title.id,
                        canEquip = canEquip(title),
                        showDivider = index < titles.lastIndex,
                        onClick = { onEquipCosmetics(equippedFrame, title.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun cosmeticStatus(cosmetic: CosmeticSnapshot, isEquipping: Boolean, canEquip: Boolean): String = when {
    isEquipping -> localized(TextKey.Equipping)
    cosmetic.equipped -> localized(TextKey.CollectionInUse)
    cosmetic.unlocked && canEquip -> localized(TextKey.CollectionTapToUse)
    cosmetic.unlocked -> localized(TextKey.Unlocked)
    else -> cosmetic.localizedUnlockRequirement() ?: localized(TextKey.Locked)
}

@Composable
private fun FrameCard(
    cosmetic: CosmeticSnapshot,
    profile: PlayerProfileSnapshot,
    isEquipping: Boolean,
    canEquip: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = cosmeticStatus(cosmetic, isEquipping, canEquip)
    val selectedOutline = A1Palette.Yellow2
    Box(
        modifier
            .testTag("collection_frame:${cosmetic.id}")
            .then(
                if (cosmetic.equipped) Modifier.drawBehind {
                    // Viền vàng 4dp cách thẻ 2dp (outline của thiết kế).
                    val inset = 6.dp.toPx()
                    drawRoundRect(
                        selectedOutline,
                        topLeft = Offset(-inset + 2.dp.toPx(), -inset + 2.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width + (inset - 2.dp.toPx()) * 2, size.height + (inset - 2.dp.toPx()) * 2),
                        cornerRadius = CornerRadius(22.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                    )
                } else Modifier
            )
            .alpha(if (cosmetic.unlocked) 1f else 0.55f)
            .a1SoftCard()
            .clickable(enabled = canEquip, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = status }
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                PlayerAvatar(
                    displayName = profile.displayName,
                    avatarId = profile.avatarId,
                    userId = profile.userId,
                    frameId = cosmetic.id,
                    size = 84.dp
                )
                if (isEquipping) {
                    Box(
                        Modifier.size(84.dp).background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(42.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(34.dp).testTag("collection_equipping:${cosmetic.id}"),
                            strokeWidth = 3.dp,
                            color = A1Palette.Blue2
                        )
                    }
                }
            }
            Text(
                cosmetic.localizedName(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                fontWeight = FontWeight.ExtraBold,
                color = A1Palette.Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                status,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                fontWeight = FontWeight.SemiBold,
                color = A1Palette.Muted,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TitleRow(
    cosmetic: CosmeticSnapshot,
    isEquipping: Boolean,
    canEquip: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    val status = cosmeticStatus(cosmetic, isEquipping, canEquip)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("collection_title:${cosmetic.id}")
            .clickable(enabled = canEquip, role = Role.Button, onClick = onClick)
            .a1DashedDivider(showDivider)
            .semantics(mergeDescendants = true) { stateDescription = status }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.alpha(if (cosmetic.unlocked) 1f else 0.55f)) {
            if (isEquipping) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp).testTag("collection_equipping:${cosmetic.id}"),
                        strokeWidth = 2.dp,
                        color = A1Palette.Blue2
                    )
                }
            } else {
                A1IconTile(
                    if (cosmetic.unlocked) Icons.Rounded.WorkspacePremium else Icons.Rounded.Lock,
                    if (cosmetic.unlocked) A1Palette.Purple2 else A1Palette.Ink
                )
            }
        }
        Column(Modifier.weight(1f).alpha(if (cosmetic.unlocked) 1f else 0.7f)) {
            RowTitle(cosmetic.localizedName())
            if (!cosmetic.equipped) SmallMuted(status)
        }
        if (cosmetic.equipped) A1Tag(localized(TextKey.CollectionInUse), A1TagTone.YELLOW)
    }
}

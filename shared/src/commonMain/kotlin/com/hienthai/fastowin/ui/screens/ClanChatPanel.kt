package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.CLAN_CHAT_MAX_LENGTH
import com.hienthai.fastowin.protocol.ClanChatMessageKind
import com.hienthai.fastowin.protocol.ClanChatMessageSnapshot
import com.hienthai.fastowin.protocol.ClanChatSystemEvent
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.UgcTargetType
import com.hienthai.fastowin.protocol.canModerateClan
import com.hienthai.fastowin.state.ClanChatState
import com.hienthai.fastowin.state.PendingClanChatMessage
import com.hienthai.fastowin.ui.components.AppOutlinedTextField
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.rememberUgcReport
import com.hienthai.fastowin.ui.theme.ArcadePalette
import androidx.compose.ui.draw.drawBehind
import com.hienthai.fastowin.ui.theme.A1Palette
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Callbacks for the clan chat panel; defaults keep previews and tests inert. */
data class ClanChatActions(
    val onVisible: (Boolean) -> Unit = {},
    val onSend: (String) -> Boolean = { false },
    val onRetry: (String) -> Unit = {},
    val onDiscard: (String) -> Unit = {},
    val onLoadOlder: () -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onMute: (memberId: String, minutes: Int) -> Unit = { _, _ -> },
    val onBlock: (senderId: String) -> Unit = {}
)

@Composable
fun ClanChatPanel(
    chat: ClanChatState,
    members: List<ClanMemberSnapshot>,
    currentUserId: String?,
    actions: ClanChatActions,
    modifier: Modifier = Modifier,
    nowMillis: Long = epochMillis(),
    timeZone: TimeZone = TimeZone.currentSystemDefault()
) {
    DisposableEffect(Unit) {
        actions.onVisible(true)
        onDispose { actions.onVisible(false) }
    }
    val byId = remember(members) { members.associateBy { it.userId } }
    val viewerRole = byId[currentUserId]?.role
    var selected by remember { mutableStateOf<ClanChatMessageSnapshot?>(null) }
    val listState = rememberLazyListState()
    // Newest line sits at index 0 of the reversed list; follow it unless the reader scrolled away.
    val newestKey = chat.pending.lastOrNull()?.requestId ?: chat.newestMessageId
    LaunchedEffect(newestKey) {
        if (newestKey != null && listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    Surface(
        modifier = modifier.testTag("clan_chat_panel"),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFE6F3FF),
        border = BorderStroke(3.dp, A1Palette.Line)
    ) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    chat.isLoading && chat.messages.isEmpty() -> Column(
                        Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = A1Palette.Blue2, modifier = Modifier.size(32.dp))
                        Text(localized(TextKey.ClanChatLoading), Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyMedium, color = A1Palette.Muted)
                    }
                    chat.messages.isEmpty() && chat.pending.isEmpty() -> ChatEmpty()
                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize().testTag("clan_chat_messages"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(chat.pending.asReversed(), key = { "pending:${it.requestId}" }) { line ->
                            PendingBubble(line, actions)
                        }
                        items(chat.messages.asReversed(), key = { it.id }) { message ->
                            if (message.kind == ClanChatMessageKind.SYSTEM) {
                                SystemLine(message, nowMillis, timeZone)
                            } else {
                                val own = message.senderId == currentUserId
                                val canOpen = own || viewerRole?.canModerateClan() == true || currentUserId != null
                                MessageBubble(
                                    message = message,
                                    sender = byId[message.senderId],
                                    own = own,
                                    mutedUntil = message.senderId?.let { chat.mutedMembers[it] }?.takeIf { it > nowMillis },
                                    nowMillis = nowMillis,
                                    timeZone = timeZone,
                                    onOpen = if (canOpen) ({ selected = message }) else null
                                )
                            }
                        }
                        if (chat.hasMore) item(key = "load_older") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = actions.onLoadOlder, enabled = !chat.isLoadingOlder,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("clan_chat_load_older")) {
                                    Text(localized(if (chat.isLoadingOlder) TextKey.ClanChatLoading else TextKey.ClanChatLoadOlder),
                                        color = A1Palette.Blue2, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            chat.mutedUntilEpochMillis?.takeIf { it > nowMillis }?.let { until -> MutedBanner(until, nowMillis, timeZone) }
            ChatComposer(enabled = chat.clanId != null && !chat.isMuted(nowMillis), onSend = actions.onSend)
        }
    }

    selected?.let { message ->
        ClanChatMessageActionsDialog(
            message = message,
            sender = byId[message.senderId],
            viewerRole = viewerRole,
            currentUserId = currentUserId,
            mutedUntil = message.senderId?.let { chat.mutedMembers[it] }?.takeIf { it > nowMillis },
            actions = actions,
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun ChatHeader() {
    Row(
        Modifier.fillMaxWidth().background(Color.White)
            .drawBehind { drawRect(A1Palette.Line, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.Forum, contentDescription = null, tint = A1Palette.Blue2, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(localized(TextKey.ClanChatTitle), Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium, color = A1Palette.Ink)
            Text(localized(TextKey.ClanChatRetention), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
        }
    }
}

@Composable
private fun ChatEmpty() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).background(Color.White, CircleShape)
                .border(3.dp, A1Palette.Line, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Forum, contentDescription = null, tint = A1Palette.Blue2, modifier = Modifier.size(30.dp)) }
        Text(localized(TextKey.ClanChatEmpty), Modifier.padding(top = 14.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ClanChatMessageSnapshot,
    sender: ClanMemberSnapshot?,
    own: Boolean,
    mutedUntil: Long?,
    nowMillis: Long,
    timeZone: TimeZone,
    onOpen: (() -> Unit)?
) {
    val name = if (own) localized(TextKey.ClanChatYou) else (sender?.displayName ?: message.senderName)
    val time = chatTime(message.createdAtEpochMillis, nowMillis, timeZone)
    val actionLabel = localized(TextKey.ClanChatMessageActions)
    val roleLabel = sender?.role?.takeIf { it != ClanRole.MEMBER }?.localizedName()
    val edge = if (own) A1Palette.YellowBorder else A1Palette.Line
    Row(
        Modifier.fillMaxWidth().testTag("clan_chat_message:${message.id}"),
        horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!own) {
            PlayerAvatar(name, sender?.avatarId, message.senderId, sender?.frameId ?: "frame_default", size = 36.dp)
            Box(Modifier.size(8.dp))
        }
        Column(
            Modifier.widthIn(max = 300.dp)
                .padding(bottom = 4.dp)
                .drawBehind {
                    val r = 18.dp.toPx()
                    drawRoundRect(edge, topLeft = androidx.compose.ui.geometry.Offset(0f, 4.dp.toPx()), size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
                }
                .background(if (own) A1Palette.Highlight else Color.White, RoundedCornerShape(18.dp))
                .border(3.dp, edge, RoundedCornerShape(18.dp))
                // Tap or long-press opens the options; the ⋮ button below is the visible affordance.
                .then(if (onOpen == null) Modifier else Modifier.combinedClickable(
                    onClickLabel = actionLabel, onLongClickLabel = actionLabel, onLongClick = onOpen, onClick = onOpen))
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (roleLabel != null) "$name · $roleLabel" else name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (own) A1Palette.YellowBorder else chatNameColor(message.senderId),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (mutedUntil != null) Icon(Icons.Rounded.VolumeOff, contentDescription = localized(TextKey.ClanChatMutedUntil,
                    "time" to chatTime(mutedUntil, nowMillis, timeZone)), tint = A1Palette.RedText, modifier = Modifier.size(14.dp))
                if (onOpen != null) IconButton(onClick = onOpen,
                    modifier = Modifier.size(32.dp).testTag("clan_chat_more:${message.id}")) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = actionLabel, tint = A1Palette.Muted, modifier = Modifier.size(18.dp))
                }
            }
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = A1Palette.Ink, modifier = Modifier.padding(end = 8.dp))
            Text(time, style = MaterialTheme.typography.labelMedium, color = A1Palette.Muted)
        }
    }
}

/** Màu tên người gửi cố định theo người (đỏ, xanh lá, tím, xanh dương) như thiết kế chat bang. */
private fun chatNameColor(senderId: String?): Color {
    val palette = listOf(A1Palette.RedText, A1Palette.GreenText, A1Palette.PurpleDeep, A1Palette.BlueDeep)
    return palette[((senderId?.hashCode() ?: 0) and 0x7fffffff) % palette.size]
}

@Composable
private fun RoleTag(role: ClanRole) {
    val color = if (role == ClanRole.LEADER) ArcadePalette.Gold500 else ArcadePalette.Violet400
    Text(
        role.localizedName(),
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1
    )
}

@Composable
private fun PendingBubble(line: PendingClanChatMessage, actions: ClanChatActions) {
    Row(Modifier.fillMaxWidth().testTag("clan_chat_pending:${line.requestId}"), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(
                Modifier.widthIn(max = 300.dp)
                    .alpha(if (line.failure == null) 0.72f else 1f)
                    .background(A1Palette.Highlight, RoundedCornerShape(18.dp))
                    .border(3.dp, if (line.failure == null) A1Palette.YellowBorder else A1Palette.RedText, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(line.text, style = MaterialTheme.typography.bodyLarge, color = A1Palette.Ink)
                if (line.failure == null) Text(localized(TextKey.Processing), style = MaterialTheme.typography.labelMedium,
                    color = A1Palette.Muted, modifier = Modifier.align(Alignment.End))
            }
            line.failure?.let { failure ->
                Text(failure, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = A1Palette.RedText)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ actions.onDiscard(line.requestId) }, Modifier.heightIn(min = 48.dp)) {
                        Text(localized(TextKey.Cancel), color = A1Palette.Muted, fontWeight = FontWeight.Bold)
                    }
                    TextButton({ actions.onRetry(line.requestId) }, Modifier.heightIn(min = 48.dp).testTag("clan_chat_retry")) {
                        Text(localized(TextKey.Retry), color = A1Palette.Blue2, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemLine(message: ClanChatMessageSnapshot, nowMillis: Long, timeZone: TimeZone) {
    val text = systemText(message)
    Row(Modifier.fillMaxWidth().testTag("clan_chat_system:${message.id}"), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.Campaign, contentDescription = null, tint = A1Palette.Muted, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = A1Palette.Muted,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
            Text(chatTime(message.createdAtEpochMillis, nowMillis, timeZone), style = MaterialTheme.typography.labelMedium,
                color = A1Palette.Muted)
        }
    }
}

@Composable
private fun systemText(message: ClanChatMessageSnapshot): String {
    val player = "player" to (message.systemArgs["player"] ?: "Player")
    return when (message.systemEvent) {
        ClanChatSystemEvent.MEMBER_JOINED -> localized(TextKey.ClanChatSystemJoined, player)
        ClanChatSystemEvent.MEMBER_LEFT -> localized(TextKey.ClanChatSystemLeft, player)
        ClanChatSystemEvent.MEMBER_KICKED -> localized(TextKey.ClanChatSystemKicked, player)
        ClanChatSystemEvent.LOGO_CHANGED -> localized(TextKey.ClanChatSystemLogoChanged, player)
        ClanChatSystemEvent.LEVEL_UP -> localized(TextKey.ClanChatSystemLevelUp, "level" to (message.systemArgs["level"] ?: ""))
        ClanChatSystemEvent.CO_LEADER_APPOINTED -> localized(TextKey.ClanChatSystemCoLeaderAppointed, player)
        ClanChatSystemEvent.CO_LEADER_DISMISSED -> localized(TextKey.ClanChatSystemCoLeaderDismissed, player)
        null -> message.text
    }
}

@Composable
private fun MutedBanner(until: Long, nowMillis: Long, timeZone: TimeZone) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp)
            .background(Color(0xFFFFE0E0), RoundedCornerShape(12.dp))
            .border(2.dp, Color(0xFFF08A8A), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("clan_chat_muted_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.VolumeOff, contentDescription = null, tint = A1Palette.RedText, modifier = Modifier.size(18.dp))
        Text(localized(TextKey.ClanChatMutedBanner, "time" to chatTime(until, nowMillis, timeZone)),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = A1Palette.RedDeep)
    }
}

@Composable
private fun ChatComposer(enabled: Boolean, onSend: (String) -> Boolean) {
    var draft by rememberSaveable { mutableStateOf("") }
    val tooLong = draft.length > CLAN_CHAT_MAX_LENGTH
    val canSend = enabled && draft.isNotBlank() && !tooLong
    val send = { if (canSend && onSend(draft)) draft = "" }
    Row(
        Modifier.fillMaxWidth().background(Color.White)
            .drawBehind { drawRect(A1Palette.Line, size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppOutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(CLAN_CHAT_MAX_LENGTH + 20) },
            // Gợi ý nằm trong ô (không có nhãn phía trên) để ô nhập còn dùng được weight trong hàng.
            placeholder = localized(TextKey.ClanChatInputHint),
            enabled = enabled,
            maxLines = 4,
            isError = tooLong,
            supportingText = if (draft.length >= CLAN_CHAT_MAX_LENGTH - 50) {
                { Text(localized(TextKey.ClanChatCharacterCount, "count" to draft.length, "max" to CLAN_CHAT_MAX_LENGTH)) }
            } else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f).testTag("clan_chat_input")
        )
        val sendLabel = localized(TextKey.ClanChatSend)
        IconButton(
            onClick = send,
            enabled = canSend,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = A1Palette.Green2, contentColor = Color.White,
                disabledContainerColor = A1Palette.SurfaceAlt2, disabledContentColor = A1Palette.Muted
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .size(52.dp)
                .border(3.dp, if (canSend) A1Palette.GreenDeep else A1Palette.Line, RoundedCornerShape(16.dp))
                .semantics { contentDescription = sendLabel }
                .testTag("clan_chat_send")
        ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null) }
    }
}

@Composable
fun ClanChatMessageActionsDialog(
    message: ClanChatMessageSnapshot,
    sender: ClanMemberSnapshot?,
    viewerRole: ClanRole?,
    currentUserId: String?,
    mutedUntil: Long?,
    actions: ClanChatActions,
    onDismiss: () -> Unit,
    initialBlockConfirmation: Boolean = false
) {
    val own = message.senderId == currentUserId
    val moderator = viewerRole?.canModerateClan() == true
    val targetRole = sender?.role
    // Mirrors the server: nobody mutes the leader, co-leaders only mute regular members.
    val canMute = !own && moderator && sender != null && targetRole != ClanRole.LEADER &&
        (viewerRole == ClanRole.LEADER || targetRole == ClanRole.MEMBER)
    val report = if (own) null else rememberUgcReport(UgcTargetType.CLAN_MESSAGE, message.id, message.text.take(60))
    val senderName = sender?.displayName ?: message.senderName
    var confirmBlock by remember(message.id) { mutableStateOf(initialBlockConfirmation) }
    if (confirmBlock) {
        ArcadeDialog(
            title = localized(TextKey.BlockPlayerTitle, "player" to senderName),
            subtitle = localized(TextKey.ClanChatBlockDescription, "player" to senderName),
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag("clan_chat_block_confirmation")
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(localized(TextKey.Block), {
                    message.senderId?.let(actions.onBlock); onDismiss()
                }, style = ArcadeActionStyle.DANGER, modifier = Modifier.fillMaxWidth().testTag("clan_chat_confirm_block"))
                ArcadeActionButton(localized(TextKey.Cancel), { confirmBlock = false }, style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }
    ArcadeDialog(
        title = localized(TextKey.ClanChatMessageActions),
        subtitle = message.text.take(120),
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("clan_chat_message_actions")
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (own || moderator) ArcadeActionButton(localized(TextKey.ClanChatDelete), {
                actions.onDelete(message.id); onDismiss()
            }, style = ArcadeActionStyle.DANGER, modifier = Modifier.fillMaxWidth().testTag("clan_chat_delete"))
            if (canMute && sender != null) {
                if (mutedUntil != null) ArcadeActionButton(localized(TextKey.ClanChatUnmute), {
                    actions.onMute(sender.userId, 0); onDismiss()
                }, style = ArcadeActionStyle.OUTLINE, modifier = Modifier.fillMaxWidth().testTag("clan_chat_unmute"))
                ArcadeActionButton(localized(TextKey.ClanChatMuteHour), {
                    actions.onMute(sender.userId, 60); onDismiss()
                }, style = ArcadeActionStyle.OUTLINE, modifier = Modifier.fillMaxWidth().testTag("clan_chat_mute_hour"))
                ArcadeActionButton(localized(TextKey.ClanChatMuteDay), {
                    actions.onMute(sender.userId, 24 * 60); onDismiss()
                }, style = ArcadeActionStyle.OUTLINE, modifier = Modifier.fillMaxWidth().testTag("clan_chat_mute_day"))
            }
            report?.let { open ->
                ArcadeActionButton(localized(TextKey.ClanChatReport), { onDismiss(); open() },
                    style = ArcadeActionStyle.OUTLINE, modifier = Modifier.fillMaxWidth().testTag("clan_chat_report"))
            }
            if (!own && message.senderId != null && currentUserId != null) ArcadeActionButton(
                localized(TextKey.ClanChatBlockSender), { confirmBlock = true }, style = ArcadeActionStyle.DANGER,
                modifier = Modifier.fillMaxWidth().testTag("clan_chat_block"))
            ArcadeActionButton(localized(TextKey.Close), onDismiss, style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

/** "14:05" today, "21/11 14:05" on other days, in the viewer's time zone. */
internal fun chatTime(epochMillis: Long, nowMillis: Long, timeZone: TimeZone): String {
    val at = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
    val clock = "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    return if (at.date == now.date) clock
    else "${at.day.toString().padStart(2, '0')}/${at.month.ordinal.plus(1).toString().padStart(2, '0')} $clock"
}

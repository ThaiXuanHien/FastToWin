package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.FriendPresence

private val PresenceOnline = Color(0xFF16A34A)
private val PresenceOffline = Color(0xFFDC2626)
private val PresenceInRoom = Color(0xFFD97706)
private val PresencePlaying = Color(0xFF7C3AED)

@Composable
fun FriendPresenceIndicator(
    presence: FriendPresence,
    modifier: Modifier = Modifier
) {
    val label = presence.accessibilityLabel()
    val accent = presence.accentColor()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(18.dp)
            .testTag("presence_${presence.name.lowercase()}")
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = label
            }
    ) {
        when (presence) {
            FriendPresence.ONLINE,
            FriendPresence.OFFLINE -> Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            FriendPresence.IN_ROOM -> Icon(
                Icons.Rounded.MeetingRoom,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            FriendPresence.PLAYING -> Icon(
                Icons.Rounded.SportsEsports,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun OnlineStatusIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) = FriendPresenceIndicator(
    presence = if (isOnline) FriendPresence.ONLINE else FriendPresence.OFFLINE,
    modifier = modifier
)

fun FriendPresence.accessibilityLabel(): String = when (this) {
    FriendPresence.OFFLINE -> "Đang ngoại tuyến"
    FriendPresence.ONLINE -> "Đang trực tuyến"
    FriendPresence.IN_ROOM -> "Đang trong phòng"
    FriendPresence.PLAYING -> "Đang trong trận"
}

private fun FriendPresence.accentColor(): Color = when (this) {
    FriendPresence.OFFLINE -> PresenceOffline
    FriendPresence.ONLINE -> PresenceOnline
    FriendPresence.IN_ROOM -> PresenceInRoom
    FriendPresence.PLAYING -> PresencePlaying
}

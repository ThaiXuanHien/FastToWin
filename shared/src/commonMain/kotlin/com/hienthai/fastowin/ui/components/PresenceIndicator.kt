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
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized

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

@Composable
fun FriendPresence.accessibilityLabel(): String = localized(when (this) {
    FriendPresence.OFFLINE -> TextKey.PresenceOffline
    FriendPresence.ONLINE -> TextKey.PresenceOnline
    FriendPresence.IN_ROOM -> TextKey.PresenceInRoom
    FriendPresence.PLAYING -> TextKey.PresencePlaying
})

private fun FriendPresence.accentColor(): Color = when (this) {
    FriendPresence.OFFLINE -> PresenceOffline
    FriendPresence.ONLINE -> PresenceOnline
    FriendPresence.IN_ROOM -> PresenceInRoom
    FriendPresence.PLAYING -> PresencePlaying
}

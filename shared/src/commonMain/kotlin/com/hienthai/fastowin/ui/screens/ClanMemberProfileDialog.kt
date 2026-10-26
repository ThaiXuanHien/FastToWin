package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.UgcTargetType
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.FriendPresenceIndicator
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.UgcReportMenuItem
import com.hienthai.fastowin.ui.components.accessibilityLabel
import com.hienthai.fastowin.ui.components.canReportUgcTarget
import com.hienthai.fastowin.ui.theme.ArcadePalette

internal enum class ClanMemberFriendActionType { SEND, ACCEPT, ALREADY_FRIENDS, REQUEST_SENT, BLOCKED }

internal data class ClanMemberFriendAction(
    val type: ClanMemberFriendActionType,
    val enabled: Boolean,
    val requestId: String? = null
)

internal fun clanMemberFriendAction(
    userId: String,
    playerCode: String,
    currentUserId: String?,
    social: FriendsSnapshot,
    isLoading: Boolean,
    isSocialReady: Boolean = true
): ClanMemberFriendAction? {
    if (userId == currentUserId) return null
    val enabled = isSocialReady && !isLoading && playerCode.isNotBlank()
    return when {
        social.blockedPlayers.any { it.userId == userId } ->
            ClanMemberFriendAction(ClanMemberFriendActionType.BLOCKED, false)
        social.friends.any { it.userId == userId } ->
            ClanMemberFriendAction(ClanMemberFriendActionType.ALREADY_FRIENDS, false)
        social.incomingRequests.any { it.userId == userId } ->
            ClanMemberFriendAction(ClanMemberFriendActionType.ACCEPT, enabled,
                social.incomingRequests.first { it.userId == userId }.requestId)
        social.outgoingRequests.any { it.userId == userId } ->
            ClanMemberFriendAction(ClanMemberFriendActionType.REQUEST_SENT, false)
        else -> ClanMemberFriendAction(ClanMemberFriendActionType.SEND, enabled)
    }
}

@Composable
fun ClanMemberProfileDialog(
    member: ClanMemberSnapshot,
    currentUserId: String?,
    social: FriendsSnapshot,
    isSocialLoading: Boolean,
    socialNotice: String?,
    socialError: String?,
    onSendFriendRequest: (String) -> Unit,
    onRespondFriendRequest: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    isSocialReady: Boolean = true,
    onRefreshSocial: () -> Unit = {}
) {
    val action = clanMemberFriendAction(member.userId, member.playerCode, currentUserId, social, isSocialLoading, isSocialReady)
    var showActions by remember(member.userId) { mutableStateOf(false) }
    ArcadeDialog(
        title = localized(TextKey.ClanMemberProfileTitle),
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("clan_member_profile_dialog"),
        scrollableContent = true,
        fixedActions = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                action?.let {
                    val key = when (it.type) {
                        ClanMemberFriendActionType.SEND -> TextKey.AddFriend
                        ClanMemberFriendActionType.ACCEPT -> TextKey.AcceptFriend
                        ClanMemberFriendActionType.ALREADY_FRIENDS -> TextKey.AlreadyFriends
                        ClanMemberFriendActionType.REQUEST_SENT -> TextKey.FriendRequestSent
                        ClanMemberFriendActionType.BLOCKED -> TextKey.Blocked
                    }
                    ArcadeActionButton(
                        label = localized(if (isSocialLoading) TextKey.Processing else key),
                        enabled = it.enabled,
                        onClick = {
                            when (it.type) {
                                ClanMemberFriendActionType.SEND -> onSendFriendRequest(member.playerCode)
                                ClanMemberFriendActionType.ACCEPT -> it.requestId?.let { id -> onRespondFriendRequest(id, true) }
                                else -> Unit
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("clan_member_friend_action")
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (action != null && !isSocialReady && !isSocialLoading) {
                        ArcadeActionButton(
                            label = localized(TextKey.Retry),
                            onClick = onRefreshSocial,
                            style = ArcadeActionStyle.OUTLINE,
                            modifier = Modifier.weight(1f).testTag("clan_member_social_retry")
                        )
                    }
                    ArcadeActionButton(localized(TextKey.Close), onDismiss,
                        style = ArcadeActionStyle.OUTLINE, modifier = Modifier.weight(1f))
                }
            }
        }
    ) {
        (socialError ?: socialNotice)?.let { message ->
            Text(message, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = if (socialError != null) ArcadePalette.Coral400 else ArcadePalette.Mint400)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayerAvatar(member.displayName, member.avatarId, member.userId, member.frameId, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(member.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                if (member.playerCode.isNotBlank()) {
                    Text(localized(TextKey.PlayerCodeShort, "code" to member.playerCode),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (canReportUgcTarget(UgcTargetType.PLAYER_PROFILE, member.userId)) {
                Box {
                    IconButton({ showActions = true }, Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.MoreVert, localized(TextKey.PlayerActions, "player" to member.displayName))
                    }
                    DropdownMenu(showActions, { showActions = false }) {
                        UgcReportMenuItem(UgcTargetType.PLAYER_PROFILE, member.userId, member.displayName) { showActions = false }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FriendPresenceIndicator(member.presence)
            Text(member.presence.accessibilityLabel(), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Blue300) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MemberProfileStat(localized(TextKey.ClanMemberRole), member.role.localizedName())
                MemberProfileStat(localized(TextKey.MemberTrophies), member.trophies.toString())
                MemberProfileStat(localized(TextKey.ClanQuestContribution), member.questContribution.toString())
                MemberProfileStat(localized(TextKey.ClanDonatedGold), member.donatedGold.toString())
                MemberProfileStat(localized(TextKey.ClanDonatedGems), member.donatedGems.toString())
            }
        }
    }
}

@Composable
private fun MemberProfileStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

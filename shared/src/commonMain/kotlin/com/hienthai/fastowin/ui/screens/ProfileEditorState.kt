package com.hienthai.fastowin.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized

/** Session-owned draft. Image bytes/bitmaps stay in memory, never in saved-instance state. */
class ProfileEditorState internal constructor() {
    internal var isEditing by mutableStateOf(false)
        private set
    internal var displayName by mutableStateOf("")
    internal var avatarSelection by mutableStateOf(AvatarEditSelection<ImageBitmap>())
        private set

    internal fun begin(name: String) {
        discard()
        displayName = name
        isEditing = true
    }

    internal fun discard() {
        isEditing = false
        displayName = ""
        avatarSelection.clear()
        // Late results belong to the old edit session, not the next one.
        avatarSelection = AvatarEditSelection()
    }
}

/** Keep this owner above temporary offline UI; replacing the owner drops its draft. */
@Composable
fun rememberProfileEditorState(
    ownerKey: Any?,
    profileNotice: String?,
    isProfileActive: Boolean = true
): ProfileEditorState {
    val editor = remember(ownerKey) { ProfileEditorState() }
    val savedNotice = localized(TextKey.ProfileSaved)
    // Observe acknowledgement here rather than replaying an old success on screen re-entry.
    LaunchedEffect(editor, profileNotice, savedNotice, isProfileActive) {
        if (!isProfileActive || profileNotice == savedNotice) editor.discard()
    }
    DisposableEffect(editor) {
        onDispose { editor.discard() }
    }
    return editor
}

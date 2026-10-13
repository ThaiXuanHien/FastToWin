package com.hienthai.fastowin.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class AvatarEditSelection<Preview> {
    var isPreparing by mutableStateOf(false)
        private set
    var bytes by mutableStateOf<ByteArray?>(null)
        private set
    var preview by mutableStateOf<Preview?>(null)
        private set

    fun beginPreparation() {
        isPreparing = true
    }

    fun finishPreparation(bytes: ByteArray?, preview: Preview?) {
        isPreparing = false
        if (bytes != null && preview != null) {
            this.bytes = bytes
            this.preview = preview
        }
    }

    fun clear() {
        isPreparing = false
        bytes = null
        preview = null
    }

    fun canSave(displayName: String, isProfileSaving: Boolean): Boolean =
        displayName.isNotBlank() && !isProfileSaving && !isPreparing

    fun save(
        displayName: String,
        avatarId: String?,
        isProfileSaving: Boolean,
        onUploadAvatar: (ByteArray) -> Unit,
        onSave: (String, String?) -> Unit
    ) {
        if (!canSave(displayName, isProfileSaving)) return
        bytes?.let(onUploadAvatar)
        onSave(displayName, avatarId)
    }
}

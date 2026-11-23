package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable
data class AvatarFrameCatalog(val frames: List<AvatarFrameDefinition>)

@Serializable
data class AvatarFrameDefinition(
    val id: String,
    val nameVi: String,
    val nameEn: String,
    val imagePath: String
)

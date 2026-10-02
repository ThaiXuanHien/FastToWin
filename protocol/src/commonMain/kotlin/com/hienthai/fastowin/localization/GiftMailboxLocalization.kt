package com.hienthai.fastowin.localization

fun giftErrorTextKey(code: String): TextKey = when (code) {
    "INVALID_REQUEST" -> TextKey.GiftErrorInvalid
    "UNAUTHORIZED" -> TextKey.GiftErrorUnauthorized
    "FORBIDDEN" -> TextKey.GiftErrorForbidden
    "NOT_FOUND" -> TextKey.GiftErrorNotFound
    "CONFLICT" -> TextKey.GiftErrorConflict
    "STORAGE_UNAVAILABLE" -> TextKey.GiftErrorStorage
    "NETWORK_ERROR" -> TextKey.GiftErrorNetwork
    else -> TextKey.GiftErrorGeneric
}

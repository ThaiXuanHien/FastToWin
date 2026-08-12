@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class
)

package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.cinterop.memScoped
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import platform.CoreFoundation.CFDictionaryRef
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

class IosAuthSessionStore : AuthSessionStore {
    override fun load(serverUrl: String): StoredAuthSession? {
        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to key(serverUrl),
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne
        )
        val data = memScoped {
            val result = allocPointerTo<CPointed>()
            val status = withCFDictionary(query) { dictionary ->
                SecItemCopyMatching(dictionary, result.ptr.reinterpret())
            }
            if (status != errSecSuccess) {
                return@memScoped null
            }
            CFBridgingRelease(result.value) as? NSData
        } ?: return null
        val json = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            ?: return null
        return runCatching { ProtocolJson.decodeFromString<StoredAuthSession>(json) }
            .getOrElse {
                clear(serverUrl)
                null
            }
    }

    override fun save(serverUrl: String, session: StoredAuthSession) {
        clear(serverUrl)
        val data = NSString.create(string = ProtocolJson.encodeToString(session))
            .dataUsingEncoding(NSUTF8StringEncoding)
            ?: return
        val attributes = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to key(serverUrl),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData to data
        )
        val status = withCFDictionary(attributes) { SecItemAdd(it, null) }
        check(status == errSecSuccess) {
            "Không thể lưu phiên đăng nhập vào iOS Keychain."
        }
    }

    override fun clear(serverUrl: String) {
        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to key(serverUrl)
        )
        withCFDictionary(query, ::SecItemDelete)
    }

    private fun key(serverUrl: String): String = "auth_session.$serverUrl"

    private companion object {
        const val SERVICE = "com.hienthai.fastowin.auth"
    }
}

private inline fun <T> withCFDictionary(values: Map<*, *>, block: (CFDictionaryRef) -> T): T {
    val retained = checkNotNull(CFBridgingRetain(values))
    return try {
        block(retained.reinterpret())
    } finally {
        CFBridgingRelease(retained)
    }
}

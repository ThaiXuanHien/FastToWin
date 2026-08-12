package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.AuthApiClient
import com.hienthai.fastowin.data.network.AuthApiException
import com.hienthai.fastowin.data.network.AuthSessionStore
import com.hienthai.fastowin.data.network.StoredAuthSession
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.AuthSessionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStage { WELCOME, LOGIN, REGISTER, UPGRADE_GUEST, PLAYING }

data class AuthState(
    val stage: AuthStage = AuthStage.WELCOME,
    val isGuest: Boolean = false,
    val session: StoredAuthSession? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthController(
    private val serverUrl: String,
    private val store: AuthSessionStore,
    private val resumeTokenStore: ResumeTokenStore,
    private val devicePlatform: String,
    private val api: AuthApiClient = AuthApiClient(serverUrl)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val initialSession = store.load(serverUrl).let { stored ->
        if (stored != null && stored.refreshExpiresAtEpochMillis <= epochMillis()) {
            store.clear(serverUrl)
            null
        } else {
            stored
        }
    }
    private val _state = MutableStateFlow(
        if (initialSession == null) AuthState()
        else AuthState(stage = AuthStage.PLAYING, session = initialSession)
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun openLogin() = _state.update { it.copy(stage = AuthStage.LOGIN, error = null) }
    fun openRegister() = _state.update { it.copy(stage = AuthStage.REGISTER, error = null) }
    fun backToWelcome() = _state.update { AuthState() }
    fun playAsGuest() = _state.update { AuthState(stage = AuthStage.PLAYING, isGuest = true) }
    fun openGuestUpgrade() = _state.update {
        if (it.isGuest) it.copy(stage = AuthStage.UPGRADE_GUEST, error = null) else it
    }
    fun cancelGuestUpgrade() = _state.update {
        if (it.stage == AuthStage.UPGRADE_GUEST) {
            AuthState(stage = AuthStage.PLAYING, isGuest = true)
        } else {
            it
        }
    }

    fun login(email: String, password: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            runCatching {
                val response = api.login(email, password, devicePlatform)
                persist(email, response.displayName, response)
            }
                .onFailure(::showError)
        }
    }

    fun register(email: String, password: String, displayName: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            runCatching {
                val response = api.register(email, password, displayName, devicePlatform)
                persist(email, displayName.trim(), response)
            }
                .onFailure(::showError)
        }
    }

    fun upgradeGuest(email: String, password: String) {
        if (_state.value.isLoading) return
        val resumeToken = resumeTokenStore.load(serverUrl)
        if (resumeToken == null) {
            _state.update {
                it.copy(error = "Chưa có phiên khách để lưu. Hãy vào danh sách phòng rồi thử lại.")
            }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            runCatching {
                val response = api.upgradeGuest(resumeToken, email, password, devicePlatform)
                persist(email, response.displayName, response)
                resumeTokenStore.clear(serverUrl)
            }.onFailure(::showError)
        }
    }

    suspend fun validAccessToken(): String? {
        val session = _state.value.session ?: return null
        if (session.accessExpiresAtEpochMillis > epochMillis() + REFRESH_EARLY_MILLIS) {
            return session.accessToken
        }
        return runCatching { api.refresh(session.refreshToken) }
            .mapCatching { response ->
                val refreshed = session.withTokens(response)
                store.save(serverUrl, refreshed)
                _state.update { it.copy(session = refreshed, isLoading = false, error = null) }
                refreshed.accessToken
            }
            .getOrElse {
                expireSession("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
                null
            }
    }

    fun logout() {
        val session = _state.value.session
        _state.update { it.copy(isLoading = true) }
        scope.launch {
            if (session != null) runCatching { api.logout(session.refreshToken) }
            store.clear(serverUrl)
            _state.value = AuthState()
        }
    }

    fun expireSession(message: String = "Phiên đăng nhập không còn hợp lệ.") {
        store.clear(serverUrl)
        _state.value = AuthState(error = message)
    }

    fun close() {
        api.close()
        scope.cancel()
    }

    private fun persist(email: String, displayName: String, response: AuthSessionResponse) {
        val stored = StoredAuthSession(
            userId = response.userId,
            email = email.trim().lowercase(),
            displayName = displayName,
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            accessExpiresAtEpochMillis = response.accessExpiresAtEpochMillis,
            refreshExpiresAtEpochMillis = response.refreshExpiresAtEpochMillis
        )
        store.save(serverUrl, stored)
        _state.value = AuthState(stage = AuthStage.PLAYING, session = stored)
    }

    private fun showError(error: Throwable) {
        _state.update {
            it.copy(
                isLoading = false,
                error = (error as? AuthApiException)?.message
                    ?: "Không thể kết nối máy chủ. Vui lòng thử lại."
            )
        }
    }

    private fun StoredAuthSession.withTokens(response: AuthSessionResponse) = copy(
        userId = response.userId,
        accessToken = response.accessToken,
        refreshToken = response.refreshToken,
        accessExpiresAtEpochMillis = response.accessExpiresAtEpochMillis,
        refreshExpiresAtEpochMillis = response.refreshExpiresAtEpochMillis
    )

    private companion object {
        const val REFRESH_EARLY_MILLIS = 30_000L
    }
}

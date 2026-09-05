package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.AuthApiClient
import com.hienthai.fastowin.data.network.AuthApiException
import com.hienthai.fastowin.data.network.AuthSessionStore
import com.hienthai.fastowin.data.network.StoredAuthSession
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.protocol.PlayerGender
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.AccountSessionSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStage { WELCOME, LOGIN, REGISTER, RESET_PASSWORD, VERIFY_EMAIL, UPGRADE_GUEST, PLAYING }

data class AuthState(
    val stage: AuthStage = AuthStage.WELCOME,
    val isGuest: Boolean = false,
    val session: StoredAuthSession? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val devResetToken: String? = null,
    val devEmailVerificationCode: String? = null,
    val passwordResetEmail: String? = null,
    val accountSessions: List<AccountSessionSnapshot> = emptyList(),
    val areSessionsLoading: Boolean = false
)

class AuthController(
    private val serverUrl: String,
    private val store: AuthSessionStore,
    private val resumeTokenStore: ResumeTokenStore,
    private val devicePlatform: String,
    private val initialGuestSession: Boolean = false,
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
        when {
            initialSession != null -> AuthState(
                stage = if (initialSession.emailVerified) AuthStage.PLAYING else AuthStage.VERIFY_EMAIL,
                session = initialSession
            )
            initialGuestSession -> AuthState(stage = AuthStage.PLAYING, isGuest = true)
            else -> AuthState()
        }
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun openLogin() = _state.update { it.copy(stage = AuthStage.LOGIN, error = null) }
    fun openRegister() = _state.update { it.copy(stage = AuthStage.REGISTER, error = null) }
    fun openPasswordReset() = _state.update {
        it.copy(
            stage = AuthStage.RESET_PASSWORD,
            error = null,
            notice = null,
            devResetToken = null,
            passwordResetEmail = null
        )
    }
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

    fun register(email: String, password: String, displayName: String, gender: PlayerGender) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            runCatching {
                val response = api.register(email, password, displayName, devicePlatform, gender)
                persist(email, displayName.trim(), response)
                if (!response.emailVerified) requestEmailVerification()
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
                if (!response.emailVerified) requestEmailVerification()
            }.onFailure(::showError)
        }
    }

    suspend fun validAccessToken(forceRefresh: Boolean = false): String? {
        val session = _state.value.session ?: return null
        if (!forceRefresh && session.accessExpiresAtEpochMillis > epochMillis() + REFRESH_EARLY_MILLIS) {
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
        store.clear(serverUrl)
        _state.value = AuthState(stage = AuthStage.LOGIN)
        scope.launch {
            if (session != null) runCatching { api.logout(session.refreshToken) }
        }
    }

    fun requestPasswordReset(email: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            runCatching { api.requestPasswordReset(email) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            notice = response.message,
                            devResetToken = response.devResetToken,
                            passwordResetEmail = email.trim().lowercase(),
                            error = null
                        )
                    }
                }
                .onFailure(::showError)
        }
    }

    fun confirmPasswordReset(email: String, resetToken: String, newPassword: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            runCatching { api.confirmPasswordReset(email, resetToken, newPassword) }
                .onSuccess { response ->
                    _state.value = AuthState(stage = AuthStage.LOGIN, notice = response.message)
                }
                .onFailure(::showError)
        }
    }

    fun requestEmailVerification() {
        if (_state.value.isLoading || _state.value.session == null) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            val accessToken = validAccessToken() ?: return@launch
            runCatching { api.requestEmailVerification(accessToken) }
                .onSuccess { response ->
                    if (response.emailVerified == true) {
                        completeEmailVerification(response.message)
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                notice = response.message,
                                devEmailVerificationCode = response.devEmailVerificationCode,
                                error = null
                            )
                        }
                    }
                }
                .onFailure(::showError)
        }
    }

    fun confirmEmailVerification(verificationCode: String) {
        if (_state.value.isLoading || _state.value.session == null) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            val accessToken = validAccessToken() ?: return@launch
            runCatching { api.confirmEmailVerification(accessToken, verificationCode) }
                .onSuccess { response ->
                    completeEmailVerification(response.message)
                }
                .onFailure(::showError)
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        if (_state.value.isLoading) return
        if (_state.value.session == null) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            val accessToken = validAccessToken() ?: return@launch
            runCatching { api.changePassword(accessToken, currentPassword, newPassword) }
                .onSuccess { response ->
                    store.clear(serverUrl)
                    _state.value = AuthState(stage = AuthStage.LOGIN, notice = response.message)
                }
                .onFailure(::showError)
        }
    }

    fun deleteAccount(password: String) {
        if (_state.value.isLoading) return
        if (_state.value.session == null) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            val accessToken = validAccessToken() ?: return@launch
            runCatching { api.deleteAccount(accessToken, password) }
                .onSuccess { response ->
                    store.clear(serverUrl)
                    resumeTokenStore.clear(serverUrl)
                    _state.value = AuthState(notice = response.message)
                }
                .onFailure(::showError)
        }
    }

    fun loadSessions() {
        if (_state.value.session == null || _state.value.areSessionsLoading) return
        _state.update { it.copy(areSessionsLoading = true, error = null) }
        scope.launch {
            val accessToken = validAccessToken() ?: return@launch
            runCatching { api.listSessions(accessToken) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            accountSessions = response.sessions,
                            areSessionsLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure(::showSessionsError)
        }
    }

    fun revokeSession(sessionId: String) {
        val target = _state.value.accountSessions.firstOrNull { it.sessionId == sessionId } ?: return
        if (_state.value.areSessionsLoading) return
        _state.update { it.copy(areSessionsLoading = true, error = null, notice = null) }
        scope.launch {
            val accessToken = validAccessToken() ?: return@launch
            runCatching { api.revokeSession(accessToken, sessionId) }
                .onSuccess { response ->
                    if (target.isCurrent) {
                        store.clear(serverUrl)
                        _state.value = AuthState(stage = AuthStage.LOGIN, notice = response.message)
                    } else {
                        _state.update {
                            it.copy(areSessionsLoading = false, notice = response.message, error = null)
                        }
                        loadSessions()
                    }
                }
                .onFailure(::showSessionsError)
        }
    }

    fun revokeAllSessions() {
        if (_state.value.session == null || _state.value.areSessionsLoading) return
        _state.update { it.copy(areSessionsLoading = true, error = null, notice = null) }
        scope.launch {
            val accessToken = validAccessToken() ?: return@launch
            runCatching { api.revokeAllSessions(accessToken) }
                .onSuccess { response ->
                    store.clear(serverUrl)
                    _state.value = AuthState(stage = AuthStage.LOGIN, notice = response.message)
                }
                .onFailure(::showSessionsError)
        }
    }

    fun expireSession(message: String = "Phiên đăng nhập không còn hợp lệ.") {
        val expiredSession = _state.value.session
        val storedSession = store.load(serverUrl)
        if (expiredSession == null || storedSession?.refreshToken == expiredSession.refreshToken) {
            store.clear(serverUrl)
        }
        _state.value = AuthState(stage = AuthStage.LOGIN, error = message)
    }

    fun updateStoredDisplayName(displayName: String) {
        val current = _state.value.session ?: return
        if (current.displayName == displayName) return
        val updated = current.copy(displayName = displayName)
        store.save(serverUrl, updated)
        _state.update { it.copy(session = updated) }
    }

    fun clearFeedback() = _state.update { it.copy(error = null, notice = null) }

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
            refreshExpiresAtEpochMillis = response.refreshExpiresAtEpochMillis,
            emailVerified = response.emailVerified
        )
        store.save(serverUrl, stored)
        _state.value = AuthState(
            stage = if (stored.emailVerified) AuthStage.PLAYING else AuthStage.VERIFY_EMAIL,
            session = stored
        )
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

    private fun showSessionsError(error: Throwable) {
        _state.update {
            it.copy(
                areSessionsLoading = false,
                error = (error as? AuthApiException)?.message
                    ?: "Không thể tải danh sách thiết bị. Vui lòng thử lại."
            )
        }
    }

    private fun completeEmailVerification(message: String) {
        val current = _state.value.session ?: return
        val verified = current.copy(emailVerified = true)
        store.save(serverUrl, verified)
        _state.value = AuthState(
            stage = AuthStage.PLAYING,
            session = verified,
            notice = message
        )
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

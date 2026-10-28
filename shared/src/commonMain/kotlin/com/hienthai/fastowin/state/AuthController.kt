package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.AuthApiClient
import com.hienthai.fastowin.data.network.AuthApiException
import com.hienthai.fastowin.data.network.AuthSessionStore
import com.hienthai.fastowin.data.network.AuthSessionStoreException
import com.hienthai.fastowin.data.network.AuthRequestConfigurator
import com.hienthai.fastowin.data.network.NoOpAuthRequestConfigurator
import com.hienthai.fastowin.data.network.StoredAuthSession
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.localization.LocalizedMessageMapper
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.PlayerGender
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.AccountSessionSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    initialLanguage: AppLanguage = AppLanguage.VIETNAMESE,
    private val requestConfigurator: AuthRequestConfigurator = NoOpAuthRequestConfigurator,
    private val api: AuthApiClient = AuthApiClient(serverUrl, requestConfigurator)
) {
    private val messageMapper = LocalizedMessageMapper(LocalizationService(initialLanguage))
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
    private val refreshMutex = Mutex()
    private val termsApi = com.hienthai.fastowin.data.network.ContentTermsApiClient(serverUrl)
    val contentEntryConsent = ContentEntryConsent(termsApi::fetch)

    fun updateLanguage(language: AppLanguage) {
        if (messageMapper.language == language) return
        messageMapper.updateLanguage(language)
        _state.update { it.copy(error = null, notice = null) }
    }

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
        scope.launch { contentEntryConsent.begin { version ->
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.register(email, password, displayName, devicePlatform, gender, version)
                persist(email, displayName.trim(), response)
                if (!response.emailVerified) requestEmailVerification()
            } catch (error: Exception) { showError(error); throw error }
        } }
    }

    fun upgradeGuest(email: String, password: String) {
        if (_state.value.isLoading) return
        val resumeToken = resumeTokenStore.load(serverUrl)
        if (resumeToken == null) {
            _state.update { it.copy(error = messageMapper.text(TextKey.GuestSessionMissing)) }
            return
        }
        scope.launch { contentEntryConsent.begin { version ->
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.upgradeGuest(resumeToken, email, password, devicePlatform, version)
                persist(email, response.displayName, response)
                resumeTokenStore.clear(serverUrl)
                if (!response.emailVerified) requestEmailVerification()
            } catch (error: Exception) { showError(error); throw error }
        } }
    }

    suspend fun validAccessToken(forceRefresh: Boolean = false): String? = refreshMutex.withLock {
        val session = _state.value.session ?: return@withLock null
        if (!forceRefresh && session.accessExpiresAtEpochMillis > epochMillis() + REFRESH_EARLY_MILLIS) {
            return@withLock session.accessToken
        }
        runCatching { api.refresh(session.refreshToken) }
            .mapCatching { response ->
                if (_state.value.session?.refreshToken != session.refreshToken ||
                    _state.value.session?.userId != session.userId ||
                    store.load(serverUrl)?.refreshToken != session.refreshToken) return@mapCatching null
                val refreshed = session.withTokens(response)
                store.save(serverUrl, refreshed)
                _state.update { it.copy(session = refreshed, isLoading = false, error = null) }
                refreshed.accessToken
            }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                if (_state.value.session?.refreshToken != session.refreshToken ||
                    _state.value.session?.userId != session.userId) return@getOrElse null
                val rejected = error is AuthApiException && when (error.httpStatus ?: 0) {
                    408, 429, in 500..599 -> false
                    401 -> true
                    else -> error.code in setOf("INVALID_REFRESH_TOKEN", "ACCOUNT_BLOCKED", "SESSION_EXPIRED")
                }
                // A temporary outage must reach the socket's retry loop without destroying credentials.
                if (error !is AuthSessionStoreException && !rejected) throw error
                val message = if (error is AuthSessionStoreException) {
                    messageMapper.message(error.code, null, emptyMap(), error.message.orEmpty())
                } else {
                    messageMapper.message(
                        code = "SESSION_EXPIRED",
                        keyName = null,
                        arguments = emptyMap(),
                        fallback = messageMapper.text(TextKey.ServerSessionExpired)
                    )
                }
                expireSession(message)
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
            runCatching {
                api.requestPasswordReset(email, messageMapper.language.languageTag)
            }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            notice = messageMapper.message(response),
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
                    _state.value = AuthState(stage = AuthStage.LOGIN, notice = messageMapper.message(response))
                }
                .onFailure(::showError)
        }
    }

    fun requestEmailVerification() {
        if (_state.value.isLoading || _state.value.session == null) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            val accessToken = actionAccessToken(::showError) ?: return@launch
            runCatching {
                api.requestEmailVerification(accessToken, messageMapper.language.languageTag)
            }
                .onSuccess { response ->
                    if (response.emailVerified == true) {
                        completeEmailVerification(messageMapper.message(response))
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                notice = messageMapper.message(response),
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
            val accessToken = actionAccessToken(::showError) ?: return@launch
            runCatching { api.confirmEmailVerification(accessToken, verificationCode) }
                .onSuccess { response ->
                    completeEmailVerification(messageMapper.message(response))
                }
                .onFailure(::showError)
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        if (_state.value.isLoading) return
        if (_state.value.session == null) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            val accessToken = actionAccessToken(::showError) ?: return@launch
            runCatching { api.changePassword(accessToken, currentPassword, newPassword) }
                .onSuccess { response ->
                    store.clear(serverUrl)
                    _state.value = AuthState(stage = AuthStage.LOGIN, notice = messageMapper.message(response))
                }
                .onFailure(::showError)
        }
    }

    fun deleteAccount(password: String) {
        if (_state.value.isLoading) return
        if (_state.value.session == null) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            val accessToken = actionAccessToken(::showError) ?: return@launch
            runCatching { api.deleteAccount(accessToken, password) }
                .onSuccess { response ->
                    store.clear(serverUrl)
                    resumeTokenStore.clear(serverUrl)
                    _state.value = AuthState(notice = messageMapper.message(response))
                }
                .onFailure(::showError)
        }
    }

    fun loadSessions() {
        if (_state.value.session == null || _state.value.areSessionsLoading) return
        _state.update { it.copy(areSessionsLoading = true, error = null) }
        scope.launch {
            val accessToken = actionAccessToken(::showSessionsError) ?: return@launch
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
            val accessToken = actionAccessToken(::showSessionsError) ?: return@launch
            runCatching { api.revokeSession(accessToken, sessionId) }
                .onSuccess { response ->
                    if (target.isCurrent) {
                        store.clear(serverUrl)
                        _state.value = AuthState(stage = AuthStage.LOGIN, notice = messageMapper.message(response))
                    } else {
                        _state.update {
                            it.copy(areSessionsLoading = false, notice = messageMapper.message(response), error = null)
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
            val accessToken = actionAccessToken(::showSessionsError) ?: return@launch
            runCatching { api.revokeAllSessions(accessToken) }
                .onSuccess { response ->
                    store.clear(serverUrl)
                    _state.value = AuthState(stage = AuthStage.LOGIN, notice = messageMapper.message(response))
                }
                .onFailure(::showSessionsError)
        }
    }

    private suspend fun actionAccessToken(onFailure: (Throwable) -> Unit): String? = try {
        validAccessToken()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onFailure(failure)
        null
    }

    fun expireSession(
        message: String = messageMapper.message(
            code = "SESSION_EXPIRED",
            keyName = null,
            arguments = emptyMap(),
            fallback = messageMapper.text(TextKey.ServerSessionExpired)
        )
    ) {
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
        contentEntryConsent.dismiss()
        termsApi.close()
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
                error = when (error) {
                    is AuthApiException -> messageMapper.message(
                        error.code, error.messageKey, error.messageArgs, error.message
                    )
                    is AuthSessionStoreException -> messageMapper.message(
                        error.code, null, emptyMap(), error.message.orEmpty()
                    )
                    else -> messageMapper.message(
                        "NETWORK_ERROR", null, emptyMap(), "Could not connect to the server. Please try again."
                    )
                }
            )
        }
    }

    private fun showSessionsError(error: Throwable) {
        _state.update {
            it.copy(
                areSessionsLoading = false,
                error = (error as? AuthApiException)?.let {
                    messageMapper.message(it.code, it.messageKey, it.messageArgs, it.message)
                } ?: messageMapper.message("NETWORK_ERROR", null, emptyMap(), "Could not load signed-in devices. Please try again.")
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

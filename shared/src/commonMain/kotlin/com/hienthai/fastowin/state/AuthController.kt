package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.AuthApiClient
import com.hienthai.fastowin.data.network.AuthApiException
import com.hienthai.fastowin.data.network.AuthSessionStore
import com.hienthai.fastowin.data.network.AuthSessionStoreException
import com.hienthai.fastowin.data.network.AuthRequestConfigurator
import com.hienthai.fastowin.data.network.NoOpAuthRequestConfigurator
import com.hienthai.fastowin.data.network.StoredAuthSession
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.data.network.RecoverableAuthSessionStore
import com.hienthai.fastowin.data.network.sameGeneration
import com.hienthai.fastowin.data.network.sameRefreshOwner
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.localization.LocalizedMessageMapper
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.PlayerGender
import com.hienthai.fastowin.protocol.PendingEmailVerificationResponse
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.AccountSessionSnapshot
import com.hienthai.fastowin.protocol.HUMAN_VERIFICATION_PAGE_PATH
import com.hienthai.fastowin.protocol.HUMAN_VERIFICATION_REQUIRED_CODE
import com.hienthai.fastowin.protocol.HumanVerificationAction
import com.hienthai.fastowin.protocol.HumanVerificationConfigResponse
import kotlinx.coroutines.CompletableDeferred
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStage { WELCOME, LOGIN, REGISTER, RESET_PASSWORD, VERIFY_EMAIL, UPGRADE_GUEST, PLAYING }

enum class VerificationDelivery { UNKNOWN, SENDING, SENT, FAILED }

data class SignupTermsState(
    val version: String? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val accepted: Boolean = false
) {
    val acceptedVersion: String? get() = version.takeIf { accepted && !isLoading && !hasError }
}

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
    val areSessionsLoading: Boolean = false,
    val signupTerms: SignupTermsState = SignupTermsState(),
    val verificationDelivery: VerificationDelivery = VerificationDelivery.UNKNOWN,
    val verificationDeliveryError: String? = null,
    val verificationSendCount: Int = 0
)

/** SEC-07: a Turnstile challenge the UI must show; [challengePageUrl] is the mobile WebView page. */
data class HumanVerificationRequest(
    val id: Long,
    val siteKey: String,
    val action: HumanVerificationAction,
    val challengePageUrl: String,
    val languageTag: String
)

/** The player closed the challenge; the form simply stays as it was. */
class HumanVerificationCancelledException : Exception("Human verification was cancelled.")

class AuthController(
    private val serverUrl: String,
    private val store: AuthSessionStore,
    private val resumeTokenStore: ResumeTokenStore,
    private val devicePlatform: String,
    private val initialGuestSession: Boolean = false,
    initialLanguage: AppLanguage = AppLanguage.VIETNAMESE,
    private val requestConfigurator: AuthRequestConfigurator = NoOpAuthRequestConfigurator,
    private val api: AuthApiClient = AuthApiClient(serverUrl, requestConfigurator),
    diagnosticSink: AuthDiagnosticSink = NoOpAuthDiagnosticSink
) {
    private val diagnostics = AuthDiagnosticRecorder(diagnosticSink)
    private val messageMapper = LocalizedMessageMapper(LocalizationService(initialLanguage))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loginEpoch = 0L
    private val initialSessionRead = runCatching {
        store.load(serverUrl).let { stored ->
            if (stored != null && stored.refreshExpiresAtEpochMillis <= epochMillis()) {
                if (store is RecoverableAuthSessionStore) store.clearSession(serverUrl, stored)
                else store.clear(serverUrl)
                null
            } else {
                stored
            }
        }
    }
    private val initialSession = initialSessionRead.getOrNull()
    private val _state = MutableStateFlow(
        when {
            initialSessionRead.isFailure -> AuthState(
                stage = AuthStage.LOGIN, error = errorMessage(initialSessionRead.exceptionOrNull()!!)
            )
            initialSession != null -> AuthState(
                stage = if (initialSession.emailVerified || initialSession.supportEmailVerificationExempt) AuthStage.PLAYING else AuthStage.VERIFY_EMAIL,
                session = initialSession
            )
            initialGuestSession -> AuthState(stage = AuthStage.PLAYING, isGuest = true)
            else -> AuthState()
        }
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val _humanVerification = MutableStateFlow<HumanVerificationRequest?>(null)
    val humanVerification: StateFlow<HumanVerificationRequest?> = _humanVerification.asStateFlow()
    private var humanVerificationConfig: HumanVerificationConfigResponse? = null
    private var pendingHumanVerification: CompletableDeferred<String?>? = null
    private var nextHumanVerificationId = 0L
    private val refreshMutex = Mutex()
    private class RefreshWork(val session: StoredAuthSession, val result: Deferred<String?>)
    private var refreshWork: RefreshWork? = null
    private var closed = false
    private val termsApi = com.hienthai.fastowin.data.network.ContentTermsApiClient(serverUrl)
    private var signupTermsSequence = 0

    init { diagnostics.emit(AuthDiagnosticPhase.CONTROLLER_CREATED) }

    fun updateLanguage(language: AppLanguage) {
        if (messageMapper.language == language) return
        messageMapper.updateLanguage(language)
        _state.update { it.copy(error = null, notice = null, verificationDeliveryError = null) }
    }

    fun openLogin() = _state.update { it.copy(stage = AuthStage.LOGIN, error = null) }
    fun openRegister() = _state.update {
        if (it.stage == AuthStage.REGISTER) it
        else it.copy(stage = AuthStage.REGISTER, error = null, signupTerms = SignupTermsState())
    }
    fun openPasswordReset() = _state.update {
        it.copy(
            stage = AuthStage.RESET_PASSWORD,
            error = null,
            notice = null,
            devResetToken = null,
            passwordResetEmail = null
        )
    }
    fun backToWelcome() { loginEpoch++; _state.value = AuthState() }
    fun playAsGuest() { loginEpoch++; _state.value = AuthState(stage = AuthStage.PLAYING, isGuest = true) }
    fun openGuestUpgrade() = _state.update {
        if (it.isGuest && it.stage != AuthStage.UPGRADE_GUEST) {
            it.copy(stage = AuthStage.UPGRADE_GUEST, error = null, signupTerms = SignupTermsState())
        } else it
    }
    fun cancelGuestUpgrade() = _state.update {
        if (it.stage == AuthStage.UPGRADE_GUEST) {
            AuthState(stage = AuthStage.PLAYING, isGuest = true)
        } else {
            it
        }
    }

    fun loadSignupTerms() {
        val stage = _state.value.stage
        if (stage !in setOf(AuthStage.REGISTER, AuthStage.UPGRADE_GUEST) || _state.value.signupTerms.isLoading) return
        val sequence = ++signupTermsSequence
        _state.update { it.copy(signupTerms = SignupTermsState(isLoading = true)) }
        scope.launch {
            val result = try {
                SignupTermsState(version = termsApi.fetch().requiredVersion)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                SignupTermsState(hasError = true)
            }
            if (sequence == signupTermsSequence && _state.value.stage == stage) {
                _state.update { it.copy(signupTerms = result) }
            }
        }
    }

    fun acceptSignupTerms(accepted: Boolean) = _state.update {
        if (it.signupTerms.version == null || it.isLoading) it
        else it.copy(signupTerms = it.signupTerms.copy(accepted = accepted))
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

    fun register(email: String, password: String, displayName: String, gender: PlayerGender, referralCode: String? = null) {
        submitSignup(email, HumanVerificationAction.REGISTER) { version, token ->
            api.register(email, password, displayName, devicePlatform, gender, version, token, referralCode)
        }
    }

    suspend fun loadReferralSummary(ownerId: String): com.hienthai.fastowin.protocol.ReferralSummary {
        suspend fun token(forceRefresh: Boolean): String {
            check(state.value.session?.userId == ownerId)
            return checkNotNull(validAccessToken(forceRefresh)).also {
                check(state.value.session?.userId == ownerId)
            }
        }
        return try {
            api.referralSummary(token(false))
        } catch (error: AuthApiException) {
            if (error.httpStatus != 401) throw error
            api.referralSummary(token(true))
        }
    }

    fun upgradeGuest(email: String, password: String, referralCode: String? = null) {
        if (_state.value.isLoading) return
        val resumeToken = resumeTokenStore.load(serverUrl)
        if (resumeToken == null) {
            _state.update { it.copy(error = messageMapper.text(TextKey.GuestSessionMissing)) }
            return
        }
        submitSignup(email, HumanVerificationAction.UPGRADE_GUEST) { version, token ->
            api.upgradeGuest(resumeToken, email, password, devicePlatform, version, token, referralCode)
        }
    }

    private fun submitSignup(
        email: String,
        action: HumanVerificationAction,
        submit: suspend (version: String, token: String?) -> AuthSessionResponse
    ) {
        val current = _state.value
        val version = current.signupTerms.acceptedVersion ?: return
        if (current.isLoading) return
        val epoch = loginEpoch
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            try {
                val response = withHumanVerification(action) { token -> submit(version, token) }
                if (epoch != loginEpoch || _state.value.stage != current.stage) return@launch
                persist(email, response.displayName, response)
                if (action == HumanVerificationAction.UPGRADE_GUEST) resumeTokenStore.clear(serverUrl)
                if (_state.value.stage == AuthStage.VERIFY_EMAIL) requestEmailVerification(challenge = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (epoch != loginEpoch || _state.value.stage != current.stage) return@launch
                showError(error)
                if (error is AuthApiException && error.code in setOf("CONTENT_TERMS_REQUIRED", "CONTENT_TERMS_VERSION_INVALID")) {
                    loadSignupTerms()
                }
            }
        }
    }

    suspend fun validAccessToken(forceRefresh: Boolean = false): String? {
        val refresh = refreshMutex.withLock {
            val session = _state.value.session ?: return null
            refreshWork?.takeIf {
                !it.result.isCompleted && it.session.userId == session.userId &&
                    it.session.refreshToken == session.refreshToken
            }?.let { return@withLock it.result }
            if (!forceRefresh && session.accessExpiresAtEpochMillis > epochMillis() + REFRESH_EARLY_MILLIS) {
                return session.accessToken
            }
            // Refresh rotates a single-use credential. A screen may stop waiting after
            // the server commits, but the controller must still persist the response.
            val epoch = loginEpoch
            scope.async { refreshSession(session, epoch) }.also { refreshWork = RefreshWork(session, it) }
        }
        return refresh.await()
    }

    private suspend fun refreshSession(session: StoredAuthSession, epoch: Long): String? {
        val attempt = diagnostics.beginRefresh()
        var expected = session
        return runCatching {
            if (!ownsRefreshState(session, epoch)) return null
            if (store is RecoverableAuthSessionStore) {
                expected = store.prepareRefresh(serverUrl, session) ?: run {
                    diagnostics.emit(AuthDiagnosticPhase.STALE_RESPONSE, attempt)
                    return null
                }
                // Keep a usable snapshot for logout even if the next disk read fails.
                _state.update { if (ownsRefreshState(expected, epoch)) it.copy(session = expected) else it }
            }
            api.refresh(expected.refreshToken,
                refreshAttemptSecret = if (store is RecoverableAuthSessionStore) expected.refreshAttemptSecret else null
            ) { status, requestId ->
                diagnostics.emit(AuthDiagnosticPhase.HTTP_RESPONSE, attempt, status, requestId)
            }
        }
            .mapCatching { response ->
                if (!ownsRefreshState(expected, epoch) || !ownsStoredRefresh(expected)) {
                    diagnostics.emit(AuthDiagnosticPhase.STALE_RESPONSE, attempt)
                    return@mapCatching null
                }
                val current = store.load(serverUrl) ?: return@mapCatching null
                val refreshed = current.withTokens(response).copy(
                    // A successful verification may have completed locally after this
                    // refresh began. A delayed response must not undo that newer proof.
                    emailVerified = if (current.emailVerified != expected.emailVerified) current.emailVerified else response.emailVerified,
                    refreshAttemptSecret = null
                )
                if (store is RecoverableAuthSessionStore) {
                    if (!store.completeRefresh(serverUrl, expected, refreshed)) {
                        diagnostics.emit(AuthDiagnosticPhase.STALE_RESPONSE, attempt)
                        return@mapCatching null
                    }
                } else store.save(serverUrl, refreshed)
                // Legacy Web persistence deliberately strips bearer credentials. Keep
                // the live response in memory, as before opt-in recovery was added.
                val saved = if (store is RecoverableAuthSessionStore) {
                    store.load(serverUrl)?.takeIf { refreshed.sameGeneration(it) } ?: return@mapCatching null
                } else refreshed
                _state.update {
                    val stage = when (it.stage) {
                        AuthStage.PLAYING, AuthStage.VERIFY_EMAIL ->
                            if (saved.emailVerified || saved.supportEmailVerificationExempt) AuthStage.PLAYING else AuthStage.VERIFY_EMAIL
                        else -> it.stage
                    }
                    it.copy(
                        session = saved,
                        stage = stage,
                        isLoading = it.isLoading && it.stage == AuthStage.VERIFY_EMAIL && stage == AuthStage.VERIFY_EMAIL,
                        error = null
                    )
                }
                diagnostics.credentialsStored(AuthDiagnosticPhase.REFRESH_STORED, attempt)
                saved.accessToken
            }
            .getOrElse { error ->
                if (error is CancellationException) {
                    diagnostics.emit(AuthDiagnosticPhase.REFRESH_CANCELLED, attempt)
                    throw error
                }
                diagnostics.emit(AuthDiagnosticPhase.REFRESH_FAILED, attempt, failure = when (error) {
                    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> AuthDiagnosticFailure.TIMEOUT
                    is AuthApiException -> AuthDiagnosticFailure.HTTP
                    is AuthSessionStoreException -> AuthDiagnosticFailure.STORAGE
                    else -> AuthDiagnosticFailure.TRANSPORT
                }, rejection = (error as? AuthApiException)?.let {
                    when (it.code) {
                        "INVALID_REFRESH_TOKEN" -> AuthDiagnosticRejection.INVALID_REFRESH_TOKEN
                        "ACCOUNT_BLOCKED" -> AuthDiagnosticRejection.ACCOUNT_BLOCKED
                        "SESSION_EXPIRED" -> AuthDiagnosticRejection.SESSION_EXPIRED
                        else -> AuthDiagnosticRejection.OTHER
                    }
                })
                if (!ownsRefreshState(expected, epoch)) return@getOrElse null
                val storedOwnership = runCatching { ownsStoredRefresh(expected) }
                if (storedOwnership.isFailure) {
                    expireSession(errorMessage(storedOwnership.exceptionOrNull()!!), AuthExpirySource.SESSION_STORAGE)
                    return@getOrElse null
                }
                if (storedOwnership.getOrNull() != true) return@getOrElse null
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
                expireSession(message, if (error is AuthSessionStoreException) AuthExpirySource.SESSION_STORAGE else AuthExpirySource.REFRESH_REJECTED)
                null
            }
    }

    fun logout() {
        val current = _state.value.session
        loginEpoch++
        _state.value = AuthState(stage = AuthStage.LOGIN)
        diagnostics.emit(AuthDiagnosticPhase.LOGGED_OUT)
        val stored = runCatching { store.load(serverUrl) }.onFailure(::showError)
        val session = if (stored.isFailure) current else stored.getOrNull()?.takeIf {
            current != null && sameStoredGeneration(current, it)
        }
        runCatching {
            if (session != null && store is RecoverableAuthSessionStore) store.clearSession(serverUrl, session)
            else if (session != null) store.clear(serverUrl)
        }.onFailure(::showError)
        scope.launch {
            if (session != null) runCatching {
                api.logout(session.refreshToken,
                    if (store is RecoverableAuthSessionStore) session.refreshAttemptSecret else null)
            }
        }
    }

    fun requestPasswordReset(email: String) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            runCatching {
                withHumanVerification(HumanVerificationAction.PASSWORD_RESET) { token ->
                    api.requestPasswordReset(email, messageMapper.language.languageTag, token)
                }
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
                    loginEpoch++
                    _state.value = AuthState(stage = AuthStage.LOGIN, notice = messageMapper.message(response))
                }
                .onFailure(::showError)
        }
    }

    /**
     * [challenge] is false for the automatic send right after sign-up: the server lets that one
     * through on the sign-up challenge, and asks again (handled below) only if it was already used.
     */
    fun requestEmailVerification(challenge: Boolean = true) {
        val previous = _state.value
        if (previous.stage != AuthStage.VERIFY_EMAIL || previous.isLoading) return
        val expected = currentOwnership() ?: return
        _state.update {
            it.copy(isLoading = true, error = null, notice = null,
                verificationDelivery = VerificationDelivery.SENDING, verificationDeliveryError = null)
        }
        scope.launch {
            var refreshAddress = false
            try {
                val accessToken = validAccessToken() ?: return@launch
                if (!ownsLogin(expected) || _state.value.stage != AuthStage.VERIFY_EMAIL) return@launch
                val response = withHumanVerification(HumanVerificationAction.EMAIL_VERIFICATION, eager = challenge) { token ->
                    api.requestEmailVerification(accessToken, messageMapper.language.languageTag, token)
                }
                if (!ownsLogin(expected)) return@launch
                if (response.emailVerified == true) {
                    completeEmailVerification(messageMapper.message(response), expected)
                } else {
                    _state.update {
                        it.copy(
                            verificationDelivery = VerificationDelivery.SENT,
                            devEmailVerificationCode = response.devEmailVerificationCode,
                            verificationSendCount = it.verificationSendCount + 1
                        )
                    }
                }
            } catch (_: HumanVerificationCancelledException) {
                if (ownsLogin(expected)) _state.update {
                    it.copy(verificationDelivery = previous.verificationDelivery,
                        verificationDeliveryError = previous.verificationDeliveryError)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val code = (error as? AuthApiException)?.code
                refreshAddress = code == "EMAIL_VERIFICATION_SUPERSEDED"
                if (ownsLogin(expected)) _state.update {
                    it.copy(
                        verificationDelivery = VerificationDelivery.FAILED,
                        verificationDeliveryError = when (code) {
                            "RATE_LIMITED", "TOO_MANY_REQUESTS", "HUMAN_VERIFICATION_FAILED",
                            "HUMAN_VERIFICATION_UNAVAILABLE", "EMAIL_VERIFICATION_SUPERSEDED" -> errorMessage(error)
                            else -> null
                        }
                    )
                }
            } finally {
                if (ownsLogin(expected) && _state.value.stage == AuthStage.VERIFY_EMAIL) {
                    _state.update {
                        it.copy(isLoading = false, verificationDelivery =
                            if (it.verificationDelivery == VerificationDelivery.SENDING) previous.verificationDelivery
                            else it.verificationDelivery)
                    }
                    if (refreshAddress) refreshPendingEmailVerification()
                }
            }
        }
    }

    fun confirmEmailVerification(verificationCode: String) {
        val expected = currentOwnership() ?: return
        if (_state.value.isLoading || _state.value.stage != AuthStage.VERIFY_EMAIL) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            try {
                val accessToken = validAccessToken() ?: return@launch
                if (!ownsLogin(expected) || _state.value.stage != AuthStage.VERIFY_EMAIL) return@launch
                val response = api.confirmEmailVerification(accessToken, verificationCode)
                completeEmailVerification(messageMapper.message(response), expected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (ownsLogin(expected)) {
                    showError(error)
                    if (error !is AuthApiException || error.code == "INVALID_VERIFICATION_CODE" ||
                        (error.httpStatus ?: 0) >= 500
                    ) refreshPendingEmailVerification()
                }
            } finally {
                if (ownsLogin(expected) && _state.value.stage == AuthStage.VERIFY_EMAIL) {
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun changePendingEmail(email: String) {
        val expected = currentOwnership() ?: return
        if (_state.value.isLoading || _state.value.stage != AuthStage.VERIFY_EMAIL) return
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            var sendNewCode = false
            try {
                val accessToken = validAccessToken() ?: return@launch
                if (!ownsLogin(expected) || _state.value.stage != AuthStage.VERIFY_EMAIL) return@launch
                val response = api.changePendingEmail(accessToken, email)
                sendNewCode = applyPendingEmailVerification(response, expected) && !response.emailVerified
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (ownsLogin(expected)) {
                    showError(error)
                    if (error !is AuthApiException || error.code == "EMAIL_ALREADY_VERIFIED" ||
                        (error.httpStatus ?: 0) >= 500
                    ) refreshPendingEmailVerification()
                }
            } finally {
                if (ownsLogin(expected) && _state.value.stage == AuthStage.VERIFY_EMAIL) {
                    _state.update { it.copy(isLoading = false) }
                    if (sendNewCode) requestEmailVerification(challenge = false)
                }
            }
        }
    }

    fun refreshPendingEmailVerification() {
        val current = _state.value
        if (current.stage != AuthStage.VERIFY_EMAIL || current.isLoading) return
        val expected = currentOwnership() ?: return
        scope.launch {
            try {
                val accessToken = validAccessToken() ?: return@launch
                if (!ownsLogin(expected) || _state.value.stage != AuthStage.VERIFY_EMAIL) return@launch
                val response = api.pendingEmailVerification(accessToken)
                val latest = _state.value
                if (latest.stage == AuthStage.VERIFY_EMAIL && !latest.isLoading &&
                    latest.session?.email == current.session?.email
                ) applyPendingEmailVerification(response, expected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The saved session still allows entering an existing code or requesting another.
            }
        }
    }

    private fun applyPendingEmailVerification(
        response: PendingEmailVerificationResponse,
        expected: LoginOwnership
    ): Boolean {
        if (!ownsLogin(expected)) return false
        val current = _state.value.session ?: return false
        if (response.emailVerified) {
            completeEmailVerification(messageMapper.text(TextKey.EmailVerified), expected, response.email)
            return true
        }
        if (current.email == response.email) return true
        val updated = updateSessionMetadata(expected) { it.copy(email = response.email) } ?: return false
        _state.update {
            it.copy(session = updated, error = null, notice = null, devEmailVerificationCode = null,
                verificationDelivery = VerificationDelivery.UNKNOWN, verificationDeliveryError = null)
        }
        return true
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        if (_state.value.isLoading) return
        if (_state.value.session == null) return
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        scope.launch {
            val accessToken = actionAccessToken(::showError) ?: return@launch
            val expected = sessionForAccessToken(accessToken) ?: return@launch
            runCatching { api.changePassword(accessToken, currentPassword, newPassword) }
                .onSuccess { response ->
                    if (!clearOwnedSession(expected)) return@onSuccess
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
            val expected = sessionForAccessToken(accessToken) ?: return@launch
            runCatching { api.deleteAccount(accessToken, password) }
                .onSuccess { response ->
                    if (!clearOwnedSession(expected)) return@onSuccess
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
            val expected = sessionForAccessToken(accessToken) ?: return@launch
            runCatching { api.revokeSession(accessToken, sessionId) }
                .onSuccess { response ->
                    if (target.isCurrent) {
                        if (!clearOwnedSession(expected)) return@onSuccess
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
            val expected = sessionForAccessToken(accessToken) ?: return@launch
            runCatching { api.revokeAllSessions(accessToken) }
                .onSuccess { response ->
                    if (!clearOwnedSession(expected)) return@onSuccess
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
        ),
        source: AuthExpirySource = AuthExpirySource.EXTERNAL
    ) {
        val expiredSession = _state.value.session
        loginEpoch++
        _state.value = AuthState(stage = AuthStage.LOGIN, error = message)
        diagnostics.emit(AuthDiagnosticPhase.SESSION_EXPIRED, source = source)
        runCatching {
            val storedSession = store.load(serverUrl)
            if (expiredSession != null && sameStoredGeneration(expiredSession, storedSession)) {
                if (store is RecoverableAuthSessionStore) store.clearSession(serverUrl, storedSession!!)
                else store.clear(serverUrl)
            }
        }.onFailure(::showError)
    }

    fun updateStoredDisplayName(displayName: String) {
        val updated = updateSessionMetadata { it.copy(displayName = displayName) } ?: return
        _state.update { it.copy(session = updated) }
    }

    fun clearFeedback() = _state.update { it.copy(error = null, notice = null) }

    fun close() {
        loginEpoch++
        closed = true
        diagnostics.emit(AuthDiagnosticPhase.CONTROLLER_CLOSED)
        pendingHumanVerification?.complete(null)
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
            emailVerified = response.emailVerified,
            supportEmailVerificationExempt = response.supportEmailVerificationExempt
        )
        store.save(serverUrl, stored)
        loginEpoch++
        _state.value = AuthState(
            stage = if (stored.emailVerified || stored.supportEmailVerificationExempt) AuthStage.PLAYING else AuthStage.VERIFY_EMAIL,
            session = stored
        )
        diagnostics.credentialsStored(AuthDiagnosticPhase.SIGNED_IN)
    }

    fun completeHumanVerification(token: String) {
        if (token.isNotBlank()) pendingHumanVerification?.complete(token)
    }

    fun cancelHumanVerification() {
        pendingHumanVerification?.complete(null)
    }

    /**
     * Runs a protected call with a Turnstile token when the server asks for one. A stale "off"
     * configuration (Turnstile switched on after it was read) is repaired by one challenged retry.
     */
    private suspend fun <T> withHumanVerification(
        action: HumanVerificationAction,
        eager: Boolean = true,
        call: suspend (token: String?) -> T
    ): T {
        val token = if (eager) humanVerificationToken(action, refreshConfig = false) else null
        return try {
            call(token)
        } catch (error: AuthApiException) {
            if (error.code != HUMAN_VERIFICATION_REQUIRED_CODE) throw error
            val fresh = humanVerificationToken(action, refreshConfig = true) ?: throw error
            call(fresh)
        }
    }

    /** Null when the server does not use Turnstile; throws when the player closes the challenge. */
    private suspend fun humanVerificationToken(action: HumanVerificationAction, refreshConfig: Boolean): String? {
        val config = humanVerificationConfig.takeUnless { refreshConfig }
            ?: runCatching { api.humanVerificationConfig() }
                .onSuccess { humanVerificationConfig = it }
                .getOrNull()
            ?: humanVerificationConfig
            ?: return null
        val siteKey = config.siteKey?.takeIf { config.enabled && it.isNotBlank() } ?: return null
        pendingHumanVerification?.complete(null)
        val pending = CompletableDeferred<String?>()
        pendingHumanVerification = pending
        _humanVerification.value = HumanVerificationRequest(
            id = ++nextHumanVerificationId,
            siteKey = siteKey,
            action = action,
            challengePageUrl = api.serverBaseUrl + HUMAN_VERIFICATION_PAGE_PATH,
            languageTag = messageMapper.language.languageTag
        )
        try {
            return pending.await() ?: throw HumanVerificationCancelledException()
        } finally {
            if (pendingHumanVerification === pending) {
                pendingHumanVerification = null
                _humanVerification.value = null
            }
        }
    }

    private fun showError(error: Throwable) {
        if (error is HumanVerificationCancelledException) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        _state.update {
            it.copy(
                isLoading = false,
                error = errorMessage(error)
            )
        }
    }

    private fun errorMessage(error: Throwable): String = when (error) {
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

    private fun completeEmailVerification(message: String, expected: LoginOwnership, email: String? = null) {
        val verified = updateSessionMetadata(expected) { it.copy(email = email ?: it.email, emailVerified = true) } ?: return
        _state.value = AuthState(
            stage = AuthStage.PLAYING,
            session = verified,
            notice = message
        )
    }

    private fun ownsRefreshState(expected: StoredAuthSession, epoch: Long): Boolean =
        !closed && epoch == loginEpoch && expected.sameGeneration(_state.value.session)

    private fun ownsStoredRefresh(expected: StoredAuthSession): Boolean {
        val stored = store.load(serverUrl)
        return if (store is RecoverableAuthSessionStore) expected.sameRefreshOwner(stored)
        else sameStoredGeneration(expected, stored)
    }

    private fun sameStoredGeneration(expected: StoredAuthSession, stored: StoredAuthSession?): Boolean =
        if (store is RecoverableAuthSessionStore) expected.sameGeneration(stored)
        else stored != null && expected.userId == stored.userId && expected.refreshToken == stored.refreshToken

    private data class LoginOwnership(val epoch: Long, val userId: String)

    private fun currentOwnership(): LoginOwnership? =
        _state.value.session?.let { LoginOwnership(loginEpoch, it.userId) }

    private fun ownsLogin(expected: LoginOwnership): Boolean =
        !closed && expected.epoch == loginEpoch && expected.userId == _state.value.session?.userId

    private fun sessionForAccessToken(accessToken: String): LoginOwnership? =
        _state.value.session?.takeIf { it.accessToken == accessToken }?.let { LoginOwnership(loginEpoch, it.userId) }

    private fun clearOwnedSession(expected: LoginOwnership): Boolean {
        if (!ownsLogin(expected)) return false
        val current = _state.value.session ?: return false
        val stored = runCatching { store.load(serverUrl) }
        if (stored.isSuccess && !sameStoredGeneration(current, stored.getOrNull())) return false
        loginEpoch++
        _state.value = AuthState(stage = AuthStage.LOGIN)
        if (stored.isFailure) { showError(stored.exceptionOrNull()!!); return true }
        return runCatching {
            if (store is RecoverableAuthSessionStore) store.clearSession(serverUrl, stored.getOrNull()!!)
            else { store.clear(serverUrl); true }
        }.onFailure(::showError).getOrDefault(true)
    }

    private fun updateSessionMetadata(
        expected: LoginOwnership? = currentOwnership(),
        update: (StoredAuthSession) -> StoredAuthSession
    ): StoredAuthSession? {
        if (expected == null || !ownsLogin(expected)) return null
        val current = _state.value.session ?: return null
        val stored = store.load(serverUrl)?.takeIf { sameStoredGeneration(current, it) } ?: return null
        val updated = update(if (store is RecoverableAuthSessionStore) stored else current)
        if (store is RecoverableAuthSessionStore) {
            if (!store.updateSession(serverUrl, stored, updated)) return null
        } else store.save(serverUrl, updated)
        return updated
    }

    private fun StoredAuthSession.withTokens(response: AuthSessionResponse) = copy(
        userId = response.userId,
        accessToken = response.accessToken,
        refreshToken = response.refreshToken,
        accessExpiresAtEpochMillis = response.accessExpiresAtEpochMillis,
        refreshExpiresAtEpochMillis = response.refreshExpiresAtEpochMillis,
        emailVerified = response.emailVerified,
        supportEmailVerificationExempt = response.supportEmailVerificationExempt
    )

    private companion object {
        const val REFRESH_EARLY_MILLIS = 30_000L
    }
}

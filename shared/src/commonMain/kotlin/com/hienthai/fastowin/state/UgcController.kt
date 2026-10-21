package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

data class UgcReportDraft(
    val targetType: UgcTargetType,
    val targetId: String,
    val targetLabel: String,
    val reason: UgcReportReason = UgcReportReason.OTHER,
    val note: String = "",
    val request: ContentReportRequest? = null,
    val pending: Boolean = false,
    val error: String? = null,
    val receipt: ContentReportReceipt? = null
)

data class UgcState(
    val report: UgcReportDraft? = null,
    val terms: ContentTermsStatus? = null,
    val showTerms: Boolean = false,
    val termsChecked: Boolean = false,
    val termsPending: Boolean = false,
    val termsError: String? = null
)

/** Owns publication intent and report retry identity for one authenticated session. */
class UgcController(private val send: suspend (ClientMessage) -> Unit) {
    private val mutableState = MutableStateFlow(UgcState())
    val state: StateFlow<UgcState> = mutableState.asStateFlow()
    private var publication: ClientMessage? = null
    private var acceptingVersion: String? = null
    private var lastPublication: ClientMessage? = null
    private var publicationInterrupted = false

    fun openReport(type: UgcTargetType, id: String, label: String) {
        if (id.isBlank()) return
        // An uncertain request must be retried with its original payload, never overwritten.
        if (mutableState.value.report?.let { it.pending || (it.request != null && it.receipt == null) } == true) return
        mutableState.update { it.copy(report = UgcReportDraft(type, id, label)) }
    }
    fun dismissReport() { mutableState.update { it.copy(report = null) } }
    fun editReport(reason: UgcReportReason, note: String, type: UgcTargetType? = null) {
        mutableState.update { state ->
            val draft = state.report ?: return@update state
            if (draft.pending || draft.request != null || draft.receipt != null) state
            else state.copy(report = draft.copy(reason = reason, note = note.take(500), targetType = type ?: draft.targetType))
        }
    }
    suspend fun submitReport() {
        val draft = mutableState.value.report ?: return
        if (draft.pending || draft.receipt != null) return
        val request = draft.request ?: ContentReportRequest(draft.targetType, draft.targetId, draft.reason,
            draft.note, newUgcRequestId())
        mutableState.update { it.copy(report = draft.copy(request = request, pending = true, error = null)) }
        send(ClientMessage.ReportContent(request))
    }
    suspend fun publish(message: ClientMessage) {
        if (publication != null) return
        val terms = mutableState.value.terms
        if (terms != null && (!terms.enforced || terms.requiredVersion == terms.acceptedVersion)) {
            lastPublication = message
            send(message)
            return
        }
        publication = message
        publicationInterrupted = false
        lastPublication = message
        mutableState.update { it.copy(showTerms = true, termsChecked = false, termsPending = true, termsError = null) }
        send(ClientMessage.GetContentTerms)
    }
    fun checkTerms(checked: Boolean) {
        if (acceptingVersion == null) mutableState.update { it.copy(termsChecked = checked) }
    }
    suspend fun acceptTerms() {
        val state = mutableState.value
        val version = state.terms?.requiredVersion?.takeIf { it.isNotBlank() } ?: return
        if (!state.showTerms || !state.termsChecked || state.termsPending) return
        acceptingVersion = version
        mutableState.update { it.copy(termsPending = true, termsError = null) }
        send(ClientMessage.AcceptContentTerms(version))
    }
    suspend fun retryTerms() {
        acceptingVersion = null
        mutableState.update { it.copy(termsPending = true, termsError = null) }
        send(ClientMessage.GetContentTerms)
    }
    fun dismissTerms() {
        publication = null
        publicationInterrupted = false
        lastPublication = null
        acceptingVersion = null
        mutableState.update { it.copy(showTerms = false, termsChecked = false, termsPending = false, termsError = null) }
    }
    fun reportTimedOut(requestId: String) {
        mutableState.update { state ->
            val draft = state.report
            if (draft?.request?.requestId == requestId && draft.pending)
                state.copy(report = draft.copy(pending = false, error = "REPORT_UNCONFIRMED")) else state
        }
    }
    fun termsTimedOut() {
        if (mutableState.value.termsPending) mutableState.update { it.copy(termsPending = false, termsError = "TERMS_UNCONFIRMED") }
    }
    fun clearSession() {
        publication = null
        publicationInterrupted = false
        lastPublication = null
        acceptingVersion = null
        mutableState.value = UgcState()
    }
    fun disconnected() {
        lastPublication = null
        publicationInterrupted = publication != null
        acceptingVersion = null
        mutableState.update { state -> state.copy(
            terms = null, termsPending = false, termsChecked = false,
            termsError = if (state.showTerms) "CONNECTION_FAILED" else null,
            report = state.report?.let { if (it.pending) it.copy(pending = false, error = "CONNECTION_FAILED") else it }
        ) }
    }
    suspend fun receive(message: ServerMessage) {
        when (message) {
            is ServerMessage.ContentReportResult -> mutableState.update { state ->
                val draft = state.report ?: return@update state
                if (draft.request?.requestId != message.requestId) state
                else state.copy(report = draft.copy(pending = false, receipt = message.receipt,
                    error = if (message.receipt == null) message.errorCode ?: "REPORT_FAILED" else null))
            }
            is ServerMessage.ContentTermsData -> {
                val previous = mutableState.value
                val status = message.status
                val changed = previous.terms?.requiredVersion != status.requiredVersion
                val acknowledged = acceptingVersion == status.requiredVersion && status.acceptedVersion == status.requiredVersion
                val mayPublish = publication != null && (acknowledged || (!publicationInterrupted &&
                    (!status.enforced || (acceptingVersion == null && status.requiredVersion == status.acceptedVersion))))
                mutableState.update { it.copy(terms = status,
                    termsChecked = if (changed) false else it.termsChecked,
                    termsPending = false, termsError = null,
                    showTerms = it.showTerms && !mayPublish) }
                if (changed) acceptingVersion = null
                if (mayPublish) {
                    val pending = publication
                    publication = null
                    publicationInterrupted = false
                    acceptingVersion = null
                    pending?.let { send(it) }
                }
            }
            is ServerMessage.Error -> {
                if (message.code in setOf("CONTENT_TERMS_REQUIRED", "CONTENT_TERMS_VERSION_INVALID")) {
                    acceptingVersion = null
                    publication = publication ?: lastPublication
                    mutableState.update { it.copy(terms = null, showTerms = publication != null,
                        termsChecked = false, termsPending = true, termsError = null) }
                    if (publication != null) send(ClientMessage.GetContentTerms)
                } else if (message.code in setOf("CONNECTION_NOT_READY", "CONNECTION_FAILED", "SEND_FAILED", "SESSION_EXPIRED")) {
                    disconnected()
                }
            }
            else -> Unit
        }
    }
}

internal fun newUgcRequestId(): String {
    val bytes = Random.nextBytes(16)
    bytes[6] = ((bytes[6].toInt() and 15) or 64).toByte()
    bytes[8] = ((bytes[8].toInt() and 63) or 128).toByte()
    val hex = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    return "${hex.take(8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}

internal fun ClientMessage.publishesUserContent(): Boolean = when (this) {
    is ClientMessage.UpdateProfile, is ClientMessage.UpdateAvatar, is ClientMessage.UpdateClanLogo,
    is ClientMessage.CreateClan, is ClientMessage.CreateRoom, is ClientMessage.CreateTournament -> true
    else -> false
}

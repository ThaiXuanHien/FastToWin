package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.ContentTermsStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Pre-authentication consent is sent atomically with registration or the guest handshake. */
class ContentEntryConsent(private val fetchTerms: suspend () -> ContentTermsStatus) {
    private val mutableState = MutableStateFlow(UgcState())
    val state = mutableState.asStateFlow()
    private var action: (suspend (String) -> Unit)? = null
    private var generation = 0

    suspend fun begin(publish: suspend (String) -> Unit) {
        if (action != null) return
        action = publish
        mutableState.value = UgcState(showTerms = true)
        retry()
    }
    suspend fun retry() {
        if (action == null || mutableState.value.termsPending) return
        val token = generation
        mutableState.update { it.copy(termsPending = true, termsError = null, termsChecked = false) }
        try {
            val terms = fetchTerms()
            if (generation == token) mutableState.update { it.copy(terms = terms, termsPending = false) }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) {
            if (generation == token) mutableState.update { it.copy(terms = null, termsPending = false, termsError = "TERMS_UNAVAILABLE") }
        }
    }
    fun check(checked: Boolean) {
        if (!mutableState.value.termsPending) mutableState.update { it.copy(termsChecked = checked) }
    }
    suspend fun accept() {
        val current = mutableState.value
        val publish = action ?: return
        val version = current.terms?.requiredVersion?.takeIf { it.isNotBlank() } ?: return
        if (!current.termsChecked || current.termsPending || current.termsError != null) return
        val token = generation
        mutableState.update { it.copy(termsPending = true, termsError = null) }
        try {
            publish(version)
            if (generation == token) dismiss()
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) {
            if (generation == token) mutableState.update { it.copy(termsPending = false, termsError = "TERMS_UNCONFIRMED") }
        }
    }
    fun dismiss() {
        generation++
        action = null
        mutableState.value = UgcState()
    }
}

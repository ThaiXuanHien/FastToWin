package com.hienthai.fastowin.state

import com.hienthai.fastowin.platform.epochMillis
import kotlin.random.Random

enum class AuthDiagnosticPhase {
    CONTROLLER_CREATED, REFRESH_STARTED, HTTP_RESPONSE, REFRESH_STORED,
    REFRESH_FAILED, REFRESH_CANCELLED, STALE_RESPONSE, SIGNED_IN, SESSION_EXPIRED, LOGGED_OUT, CONTROLLER_CLOSED
}

enum class AuthDiagnosticFailure { TIMEOUT, HTTP, STORAGE, TRANSPORT }
enum class AuthDiagnosticRejection { INVALID_REFRESH_TOKEN, ACCOUNT_BLOCKED, SESSION_EXPIRED, OTHER }
enum class AuthExpirySource { REFRESH_REJECTED, SESSION_STORAGE, GAME_CONNECTION, EXTERNAL }

data class AuthDiagnosticEvent(
    val phase: AuthDiagnosticPhase,
    val controllerId: String,
    val timestampEpochMillis: Long,
    val attempt: Int? = null,
    val credentialGeneration: Int = 0,
    val httpStatus: Int? = null,
    val requestId: String? = null,
    val failure: AuthDiagnosticFailure? = null,
    val rejection: AuthDiagnosticRejection? = null,
    val expirySource: AuthExpirySource? = null
)

/** Best-effort, non-blocking local observations. Never pass account data or exception messages. */
fun interface AuthDiagnosticSink { fun record(event: AuthDiagnosticEvent) }
val NoOpAuthDiagnosticSink = AuthDiagnosticSink { }

/** Explicit build opt-in; this sink never uploads observations. */
fun localAuthDiagnosticSink(enabled: Boolean, writeLine: (String) -> Unit): AuthDiagnosticSink =
    if (!enabled) NoOpAuthDiagnosticSink else AuthDiagnosticSink { event ->
        writeLine(buildString {
            append("time=${event.timestampEpochMillis} controller=${safeDiagnosticId(event.controllerId) ?: "invalid"}")
            append(" phase=${event.phase.name} generation=${event.credentialGeneration}")
            event.attempt?.let { append(" attempt=$it") }
            event.httpStatus?.let { append(" http=$it") }
            safeDiagnosticId(event.requestId)?.let { append(" request=$it") }
            event.failure?.let { append(" failure=${it.name}") }
            event.rejection?.let { append(" rejection=${it.name}") }
            event.expirySource?.let { append(" source=${it.name}") }
        })
    }

private val diagnosticIdPattern = Regex("[A-Za-z0-9_-]{1,128}")
private fun safeDiagnosticId(value: String?) = value?.takeIf { diagnosticIdPattern.matches(it) }

internal class AuthDiagnosticRecorder(private val sink: AuthDiagnosticSink) {
    private val enabled = sink !== NoOpAuthDiagnosticSink
    // Random per controller, unrelated to account/session IDs and never sent to the server.
    private val controllerId = if (enabled) Random.nextLong().toULong().toString(16) else ""
    private var attempt = 0
    private var generation = 0

    fun beginRefresh(): Int = (++attempt).also { emit(AuthDiagnosticPhase.REFRESH_STARTED, it) }

    fun credentialsStored(phase: AuthDiagnosticPhase, attempt: Int? = null) {
        generation++
        emit(phase, attempt)
    }

    fun emit(
        phase: AuthDiagnosticPhase,
        attempt: Int? = null,
        httpStatus: Int? = null,
        requestId: String? = null,
        failure: AuthDiagnosticFailure? = null,
        rejection: AuthDiagnosticRejection? = null,
        source: AuthExpirySource? = null
    ) {
        if (!enabled) return
        // Diagnostics cannot turn a successful rotation into an auth failure or interrupt teardown.
        runCatching {
            sink.record(AuthDiagnosticEvent(phase, controllerId, epochMillis(), attempt, generation,
                httpStatus, safeDiagnosticId(requestId), failure, rejection, source))
        }
    }
}

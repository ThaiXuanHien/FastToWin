package com.hienthai.fastowin.server.ownership

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomFaultObservationTest {
    @kotlin.test.Test
    fun safeFailureDiagnosticIsExportedBeforeDisposableContainerCleanup() {
        val bytes = java.io.ByteArrayOutputStream()
        val out = java.io.PrintStream(bytes, true, Charsets.UTF_8)
        val failure = LoadPhaseFailure("command", AcceptanceRejected("RATE_LIMITED"))
        printRehearsalFailureDiagnostic(out, failure)
        val line = bytes.toString(Charsets.UTF_8).trimEnd()
        kotlin.test.assertTrue(line.startsWith("FTW_RUNNER_DIAGNOSTIC "))
        kotlin.test.assertTrue(line.contains("RATE_LIMITED"))
        kotlin.test.assertTrue(line.contains("command"))
        kotlin.test.assertFalse(line.contains('\n'))
        bytes.reset()
        printRehearsalFailureDiagnostic(out, IllegalStateException("private account FAKE_TOKEN", AcceptanceRejected("TOP_SECRET_ACCOUNT")))
        val unknown = bytes.toString(Charsets.UTF_8)
        kotlin.test.assertTrue(unknown.contains("unknown"))
        kotlin.test.assertFalse(unknown.contains("private account"))
        kotlin.test.assertFalse(unknown.contains("FAKE_TOKEN"))
        kotlin.test.assertFalse(unknown.contains("TOP_SECRET_ACCOUNT"))
    }

    @Test
    fun rejectionDiagnosticsExportOnlyKnownCodes() {
        assertTrue(rehearsalFailureDiagnostic(AcceptanceRejected("ROOM_RECOVERING")).contains("ROOM_RECOVERING"))
        assertFalse(rehearsalFailureDiagnostic(AcceptanceRejected("SECRET_ACCOUNT")).contains("SECRET_ACCOUNT"))
        assertTrue(rehearsalFailureDiagnostic(TopologyPhaseFailure("golden-b-create_room",
            AcceptanceRejected("ROOM_RECOVERING"))).contains("golden-b-create_room"))
    }

    @Test
    fun diagnosticBoundsErrorsAndContainsOnlyFixedStates() {
        assertEquals(
            """{"stage":"observation","endedBy":"deadline","elapsedMillis":25000,"errorCodes":["ROOM_NOT_FOUND","unknown"]}""",
            postgresPauseDiagnostic("observation", "deadline", 25000, setOf("ROOM_NOT_FOUND", "secret-sql"))
        )
    }

    @Test
    fun failureSitesExcludeMessagesIdentitiesAndUnknownFiles() {
        val failure = IllegalStateException("secret SQL account-id")
        failure.stackTrace = arrayOf(
            StackTraceElement("secret.class", "secret-method", "RoomOwnershipLoadAcceptance.kt", 157),
            StackTraceElement("secret.class", "secret-method", "secret-account.kt", 9)
        )
        val diagnostic = rehearsalFailureDiagnostic(failure)
        assertTrue(diagnostic.contains("load"))
        assertTrue(diagnostic.contains("157"))
        assertFalse(diagnostic.contains("secret"))
    }

    @Test
    fun closeDiagnosticsRetainOnlyFixedProtocolReasonsAndPhase() {
        val known = rehearsalFailureDiagnostic(LoadPhaseFailure("command",
            ObservedSocketClose(1, 1013, "Service temporarily unavailable")))
        assertTrue(known.contains("storage-unavailable"))
        assertTrue(known.contains("command"))
        assertTrue(known.contains("1013"))
        val unknown = rehearsalFailureDiagnostic(ObservedSocketClose(0, 1000, "private account detail"))
        assertFalse(unknown.contains("private"))
        assertTrue(unknown.contains("other"))
        val drain = rehearsalFailureDiagnostic(DrainAcceptanceFailure("held-policy",
            IllegalStateException("private query details")))
        assertTrue(drain.contains("held-policy"))
        assertFalse(drain.contains("private"))
    }
}

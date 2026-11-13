package com.hienthai.fastowin.server.ownership

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OwnedClusterControlMainTest {
    @Test fun `operator request binds candidate topology and expected coordinator process`() {
        val operation = UUID.randomUUID().toString()
        val process = UUID.randomUUID().toString()
        val identity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
        val binding = OwnedAdmissionBinding("d".repeat(64), "e".repeat(64))
        val request = parseControlArguments(arrayOf("step", operation, "17", process), identity, binding)
        assertEquals("step", request.operation)
        assertEquals(operation, request.operationId)
        assertEquals(17L, request.generation)
        assertEquals(process, request.expectedProcessId)
        assertEquals(identity.candidateSha256, request.candidateSha256)
        assertEquals(binding.topologySha256, request.topologySha256)
        assertFailsWith<IllegalArgumentException> { parseControlArguments(arrayOf("step", operation, "17"), identity, binding) }
        assertFailsWith<IllegalArgumentException> { parseControlArguments(arrayOf("quiesce", operation, "17", process), identity, binding) }
    }
}

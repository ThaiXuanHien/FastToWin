package com.hienthai.fastowin.server.ownership

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class OwnedCandidateIdentityTest {
    @Test
    fun `identity binds server jar and ordered runtime contents without host paths`() {
        val first = Files.createTempDirectory("candidate-a")
        val second = Files.createTempDirectory("candidate-b")
        try {
            fun files(dir: java.nio.file.Path): List<java.nio.file.Path> = listOf("server.jar", "protocol.jar").map {
                Files.write(dir.resolve(it), it.toByteArray())
            }
            val a = files(first)
            val b = files(second)
            val identity = OwnedCandidateIdentity.fromPaths(a[0], a)
            assertEquals(identity, OwnedCandidateIdentity.fromPaths(b[0], b))
            assertNotEquals(identity.candidateSha256, OwnedCandidateIdentity.fromPaths(a[0], a.reversed()).candidateSha256)
            Files.write(b[1], "changed".toByteArray())
            val changed = OwnedCandidateIdentity.fromPaths(b[0], b)
            assertEquals(identity.serverJarSha256, changed.serverJarSha256)
            assertNotEquals(identity.classpathSha256, changed.classpathSha256)
            assertNotEquals(identity.candidateSha256, changed.candidateSha256)
            assertFailsWith<IllegalArgumentException> { OwnedCandidateIdentity.fromPaths(a[0], listOf(a[1])) }
        } finally {
            for (dir in listOf(first, second)) {
                Files.deleteIfExists(dir.resolve("server.jar")); Files.deleteIfExists(dir.resolve("protocol.jar"))
                Files.deleteIfExists(dir)
            }
        }
    }
}

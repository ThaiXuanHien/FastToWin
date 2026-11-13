package com.hienthai.fastowin.server.ownership

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Hash the actual server code source and ordered runtime JARs, never caller-supplied claims. */
internal data class OwnedCandidateIdentity(val serverJarSha256: String, val classpathSha256: String, val candidateSha256: String) {
    companion object {
        fun running(): OwnedCandidateIdentity {
            val source = Path.of(OwnedCandidateIdentity::class.java.protectionDomain.codeSource.location.toURI())
            val classpath = System.getProperty("java.class.path").split(java.io.File.pathSeparatorChar).map(Path::of)
            return fromPaths(source, classpath)
        }

        internal fun fromPaths(server: Path, classpath: List<Path>): OwnedCandidateIdentity {
            require(classpath.isNotEmpty())
            val source = server.toRealPath()
            val files = classpath.map { it.toRealPath() }
            require(Files.isRegularFile(source) && source.fileName.toString().endsWith(".jar"))
            require(files.all { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar") })
            require(files.count { it == source } == 1 && files.map { it.fileName.toString() }.distinct().size == files.size)
            val serverHash = hash(Files.readAllBytes(source))
            val manifest = buildString {
                append("fasttowin/runtime-classpath/v1\n")
                files.forEach { file -> append(file.fileName).append(' ').append(hash(Files.readAllBytes(file))).append('\n') }
            }
            val classpathHash = hash(manifest.toByteArray(Charsets.UTF_8))
            val candidateHash = hash("fasttowin/candidate/v1\n$serverHash\n$classpathHash\n".toByteArray(Charsets.UTF_8))
            return OwnedCandidateIdentity(serverHash, classpathHash, candidateHash)
        }
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}

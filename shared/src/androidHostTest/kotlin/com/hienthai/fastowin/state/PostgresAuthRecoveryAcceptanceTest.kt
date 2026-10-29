package com.hienthai.fastowin.state

import android.content.SharedPreferences
import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.flywaydb.core.Flyway
import org.junit.Assume.assumeTrue
import java.lang.reflect.Proxy
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PostgresAuthRecoveryAcceptanceTest {
    @Test fun committedHttpResponseIsLostThenRecreatedAndroidControllerRecoversExactCredentials() = runTest {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue("Actual local PostgreSQL required; skipped is not integration evidence", !url.isNullOrBlank())
        val schema = "android_recovery_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD")
            maximumPoolSize = 4; connectionInitSql = "SET statement_timeout = '15s'"
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try { HikariDataSource(config().apply { this.schema = schema }).use { source ->
                Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                val now = System.currentTimeMillis()
                fun service() = AuthenticationService(PostgresAuthRepository(source), PasswordHasher(1), { now })
                val firstService = service()
                val initial = assertIs<AuthResult.Success>(firstService.register("acceptance@example.com", "password123", "Recovery", "android")).session
                val disk = EncryptedPreferences()
                disk.store().save(URL, StoredAuthSession(initial.userId, "acceptance@example.com", initial.displayName,
                    initial.accessToken, initial.refreshToken, initial.accessExpiresAtEpochMillis, initial.refreshExpiresAtEpochMillis))
                var committedBody: String? = null
                var firstPair: RefreshTokenRequest? = null
                var recoveredBody: String? = null
                var replayPair: RefreshTokenRequest? = null
                Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
                try {
                    // A full native gameModule responds only after real repository commit.
                    testApplication {
                        application { gameModule(authService = firstService, environment = "dev") }
                        val routeClient = client
                        val api = bridgedApi { request ->
                            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            firstPair = ProtocolJson.decodeFromString(body)
                            assertEquals(firstPair?.refreshAttemptSecret, disk.store().load(URL)?.refreshAttemptSecret)
                            val response = routeClient.post(request.url.encodedPath) { contentType(ContentType.Application.Json); setBody(body) }
                            assertEquals(HttpStatusCode.OK, response.status)
                            val received = response.bodyAsText()
                            committedBody = received
                            assertNotNull(firstService.authenticateAccessToken(ProtocolJson.decodeFromString<AuthSessionResponse>(received).accessToken))
                            assertEquals(1, receiptCount(source))
                            throw DroppedCommittedResponse()
                        }
                        val controller = AuthController(URL, disk.store(), InMemoryResumeTokenStore(), "android", api = api)
                        try {
                            assertFailsWith<DroppedCommittedResponse> { controller.validAccessToken(forceRefresh = true) }
                            assertEquals(AuthStage.PLAYING, controller.state.value.stage)
                            assertNotNull(disk.store().load(URL)?.refreshAttemptSecret)
                            assertEquals(initial.refreshToken, disk.store().load(URL)?.refreshToken)
                        } finally { controller.close() }
                    }
                    val restartedService = service()
                    // Both the route application/service/repository and Android store/controller are recreated.
                    testApplication {
                        application { gameModule(authService = restartedService, environment = "dev") }
                        val routeClient = client
                        val api = bridgedApi { request ->
                            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            replayPair = ProtocolJson.decodeFromString(body)
                            val response = routeClient.post(request.url.encodedPath) { contentType(ContentType.Application.Json); setBody(body) }
                            val received = response.bodyAsText()
                            recoveredBody = received
                            respond(received, response.status, headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                        val recreated = AuthController(URL, disk.store(), InMemoryResumeTokenStore(), "android", api = api)
                        try {
                            val original = ProtocolJson.decodeFromString<AuthSessionResponse>(assertNotNull(committedBody))
                            assertEquals(original.accessToken, recreated.validAccessToken(forceRefresh = true))
                            assertEquals(firstPair, replayPair)
                            assertNotNull(firstPair?.refreshAttemptSecret)
                            assertEquals(committedBody, recoveredBody)
                            assertEquals(AuthStage.PLAYING, recreated.state.value.stage)
                            val stored = assertNotNull(disk.store().load(URL))
                            assertNull(stored.refreshAttemptSecret)
                            assertEquals(original.refreshToken, stored.refreshToken)
                            assertEquals(original.accessExpiresAtEpochMillis, stored.accessExpiresAtEpochMillis)
                            assertEquals(original.refreshExpiresAtEpochMillis, stored.refreshExpiresAtEpochMillis)
                            assertEquals(1, receiptCount(source))
                            assertNull(restartedService.authenticateAccessToken(initial.accessToken))
                            assertNotNull(restartedService.authenticateAccessToken(original.accessToken))
                            assertIs<AuthResult.Failure>(restartedService.refresh(initial.refreshToken))
                            source.connection.use { it.createStatement().use { s ->
                                s.executeQuery("SELECT count(*) FROM sessions").use { rows -> assertTrue(rows.next()); assertEquals(1, rows.getInt(1)) }
                            } }
                        } finally { recreated.close() }
                    }
                } finally { Dispatchers.resetMain() }
            } } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }

    private fun receiptCount(source: HikariDataSource) = source.connection.use { it.createStatement().use { s ->
        s.executeQuery("SELECT count(*) FROM auth_refresh_recoveries r JOIN sessions s ON s.id=r.session_id WHERE r.successor_refresh_hash=s.refresh_token_hash AND r.successor_access_hash=s.access_token_hash").use { rows -> assertTrue(rows.next()); rows.getInt(1) }
    } }
    private fun bridgedApi(handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        AuthApiClient(URL, client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = Dispatchers.Main; addHandler(handler)
        })) { install(ContentNegotiation) { json(ProtocolJson) } })

    private class DroppedCommittedResponse : Exception("Deliberately dropped committed response")

    /** Host substitutes Android Keystore/disk I/O only. Real store CAS, SecureRandom and atomic commit execute. */
    private class EncryptedPreferences {
        private val durable = mutableMapOf<String, String>()
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        private val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> durable[args!![0]] ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected preferences operation")
            }
        } as SharedPreferences
        private fun editor(): SharedPreferences.Editor {
            val writes = mutableMapOf<String, String?>()
            return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
                when (method.name) {
                    "putString" -> { writes[args!![0] as String] = args[1] as String; proxy }
                    "remove" -> { writes[args!![0] as String] = null; proxy }
                    "commit" -> { writes.forEach { (k, v) -> if (v == null) durable.remove(k) else durable[k] = v }; true }
                    else -> error("Unexpected editor operation")
                }
            } as SharedPreferences.Editor
        }
        fun store() = AndroidAuthSessionStore(preferences, { session ->
            val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            Base64.getEncoder().encodeToString(nonce + cipher.doFinal(ProtocolJson.encodeToString(StoredAuthSession.serializer(), session).toByteArray()))
        }, { encoded ->
            val payload = Base64.getDecoder().decode(encoded)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.copyOfRange(0, 12)))
            ProtocolJson.decodeFromString<StoredAuthSession>(cipher.doFinal(payload.copyOfRange(12, payload.size)).decodeToString())
        })
    }
    private companion object { const val URL = "http://localhost" }
}

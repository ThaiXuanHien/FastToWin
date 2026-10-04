package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptAuthRefreshTest {
    @Test fun `late receipt token refresh cannot resurrect logged out account`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val url = "http://example.invalid"
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/auth/refresh") {
                entered.complete(Unit); release.await()
                respond("""{"userId":"old","displayName":"Player","accessToken":"refreshed","refreshToken":"rotated","accessExpiresAtEpochMillis":9999999999999,"refreshExpiresAtEpochMillis":9999999999999}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val store = InMemoryAuthSessionStore().apply {
            save(url, StoredAuthSession("old", "test@example.invalid", "Player", "old-access", "old-refresh", 9999999999999, 9999999999999))
        }
        val controller = AuthController(url, store, InMemoryResumeTokenStore(), "test", api = AuthApiClient(url, client = client))
        try {
            val refresh = async { controller.validAccessToken(true) }
            entered.await(); controller.logout(); release.complete(Unit)
            assertNull(refresh.await())
            assertNull(controller.state.value.session)
            assertNull(store.load(url))
        } finally { controller.close(); Dispatchers.resetMain() }
    }
}

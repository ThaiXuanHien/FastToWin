package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.protocol.AvatarFrameCatalog
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.server.gameModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarFrameRoutesTest {
    @Test
    fun `owner adds and edits a public frame with private audit metadata`() = testApplication {
        val fixture = AdminFixture()
        val owner = runBlocking {
            fixture.owner()
            fixture.login().also { login ->
                fixture.service.createAccount(login.token, login.response.csrfToken, fixture.configuration.origin,
                    CreateAdminRequest("operator@example.com", "GoodPassword123!", AdminRole.OPERATOR, "Access", "op"))
            }
        }
        val operator = runBlocking { fixture.login("operator@example.com") }
        application { gameModule(adminService = fixture.service) }
        val encodedImage = Base64.getEncoder().encodeToString(ByteArrayOutputStream().use { buffer ->
            ImageIO.write(BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", buffer)
            buffer.toByteArray()
        })
        val public = "/api/avatar-frames"
        val admin = "/admin/api/avatar-frames"
        val frame = "$admin/frame_test_admin"
        val body = """{"nameVi":"Khung thử","nameEn":"Test frame","pngBase64":"$encodedImage","version":null,"reason":"Private reason"}"""
        suspend fun save(request: String, login: AdminLogin = owner, csrf: Boolean = true, path: String = frame) = client.put(path) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Origin, fixture.configuration.origin)
            header(HttpHeaders.Cookie, "$ADMIN_COOKIE=${login.token}")
            if (csrf) header(ADMIN_CSRF_HEADER, login.response.csrfToken)
            setBody(request)
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get(admin).status)
        assertEquals(HttpStatusCode.Forbidden, save(body, operator).status)
        assertEquals(HttpStatusCode.Forbidden, save(body, csrf = false).status)
        assertEquals(HttpStatusCode.BadRequest, save(body.replace(encodedImage, "invalid")).status)
        assertEquals(HttpStatusCode.Conflict, save(body, path = "$admin/frame_default").status)
        assertEquals(HttpStatusCode.OK, save(body).status)
        assertEquals(HttpStatusCode.Conflict, save(body).status)
        val catalog = client.get(public)
        assertEquals(HttpStatusCode.OK, catalog.status)
        assertTrue(catalog.bodyAsText().contains("Khung thử"))
        assertTrue(catalog.bodyAsText().contains("frame_default"))
        assertFalse(catalog.bodyAsText().contains("Private reason"))
        assertFalse(catalog.bodyAsText().contains(encodedImage))
        val imagePaths = ProtocolJson.decodeFromString<AvatarFrameCatalog>(catalog.bodyAsText()).frames.map { it.imagePath }
        val seasonalImage = "$public/season_42_master/image"
        assertTrue(imagePaths.size >= 25) // Built-in frames plus the newly added frame.
        for (path in imagePaths + seasonalImage) {
            val image = client.get(path)
            assertEquals(HttpStatusCode.OK, image.status, path)
            assertEquals("image/png", image.headers[HttpHeaders.ContentType])
            assertEquals("nosniff", image.headers["X-Content-Type-Options"])
        }
        assertEquals(HttpStatusCode.NotFound, client.get("$public/frame_missing/image").status)
        assertEquals(HttpStatusCode.OK, save("""{"nameVi":"Đổi tên","nameEn":"Renamed","version":1,"reason":"Edit"}""").status)
        assertTrue(client.get(public).bodyAsText().contains("Renamed"))
        assertEquals(HttpStatusCode.OK, client.get("$public/frame_test_admin/image").status)
        assertEquals(2, runBlocking { fixture.service.audit(owner.token, PageRequest()) }.items.count { it.action.startsWith("AVATAR_FRAME_") })
    }
}

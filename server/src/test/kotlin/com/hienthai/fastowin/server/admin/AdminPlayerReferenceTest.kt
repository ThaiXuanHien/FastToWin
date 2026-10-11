package com.hienthai.fastowin.server.admin

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AdminPlayerReferenceTest {
    @Test fun `public code search and exact gift recipient resolution preserve permissions and privacy`() = runTest {
        val f = AdminFixture(); f.owner(); val owner = f.login()
        val player = UserDetail("11111111-1111-1111-1111-111111111111", "Player", "player@example.com", "REGISTERED", "ACTIVE", 1, 1, 0, 0, 0, 1000, null, null, null, playerCode = "FTW123AB")
        f.repository.seedUser(player)
        f.repository.seedUser(player.copy(id = "22222222-2222-2222-2222-222222222222", email = null, accountType = "GUEST", playerCode = "GUEST123"))
        f.repository.seedUser(player.copy(id = "33333333-3333-3333-3333-333333333333", email = "deleted@example.com", status = "DELETED", playerCode = "DEAD1234"))
        assertEquals(player.id, f.service.users(owner.token, " ftw123ab ", PageRequest()).items.single().id)
        assertEquals(player.playerCode, f.service.user(owner.token, player.id).playerCode)
        for (reference in listOf(" ftw123ab ", " PLAYER@EXAMPLE.COM ")) {
            assertEquals(player.summary(), f.service.giftRecipient(owner.token, reference))
        }
        for (reference in listOf("FTW123", "Player", player.id, "other@example.com", "GUEST123", "DEAD1234")) {
            assertEquals(404, assertFailsWith<AdminException> { f.service.giftRecipient(owner.token, reference) }.status)
        }
        for (reference in listOf("", "x".repeat(255), "FTW123AB\n")) {
            assertEquals(400, assertFailsWith<AdminException> { f.service.giftRecipient(owner.token, reference) }.status)
        }
        assertEquals(401, assertFailsWith<AdminException> { f.service.giftRecipient(null, player.playerCode) }.status)
        for (role in listOf(AdminRole.OPERATOR, AdminRole.SUPPORT)) {
            f.service.createAccount(owner.token, owner.response.csrfToken, f.configuration.origin,
                CreateAdminRequest("${role.name.lowercase()}@example.com", "GoodPassword123!", role, "Test", role.name))
            val login = f.login("${role.name.lowercase()}@example.com")
            if (role == AdminRole.OPERATOR) assertEquals(player.id, f.service.giftRecipient(login.token, player.playerCode).id)
            else {
                assertEquals("p***@e***.com", f.service.users(login.token, player.playerCode, PageRequest()).items.single().email)
                assertEquals(403, assertFailsWith<AdminException> { f.service.giftRecipient(login.token, player.playerCode) }.status)
            }
        }
        val audit = f.service.audit(owner.token, PageRequest(pageSize = 100)).items
        assertTrue(audit.any { it.action == "GIFT_RECIPIENT_LOOKUP" && it.targetId == player.id })
        assertFalse(audit.toString().contains("player@example.com", true))
        assertFalse(audit.toString().contains(player.playerCode))
    }
}

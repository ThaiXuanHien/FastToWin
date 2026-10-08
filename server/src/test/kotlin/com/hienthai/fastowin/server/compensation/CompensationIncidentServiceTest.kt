package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gifts.*
import com.hienthai.fastowin.protocol.CompensationMailboxContext
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

class CompensationIncidentServiceTest {
    private suspend fun fixture(): Pair<MemoryGiftFixture, CompensationIncidentService> {
        val f = MemoryGiftFixture(); f.initialize(); return f to CompensationIncidentService(f.gifts.repository, f.admin, { f.now })
    }
    private suspend fun request(f: MemoryGiftFixture) = CompensationIncidentRequest(" outage_1 ", "Sự cố", "Outage", "Ảnh hưởng", "Affected",
        0, 10, "INTERNAL_CAUSE", "INTERNAL_EVIDENCE", f.admin.session(f.owner.token).admin.id, UUID.randomUUID().toString(), "Create")
    @Test fun `canonical incident key rejects duplicates`() = runTest {
        val (f, s) = fixture(); val r = request(f); s.create(f.owner, r)
        assertEquals(409, assertFailsWith<AdminException> { s.create(f.owner, r.copy(incidentKey = "OUTAGE_1", requestId = "other")) }.status)
    }
    @Test fun `affected interval rejects empty reversed negative and out of range bounds`() = runTest {
        val (f, s) = fixture(); val r = request(f)
        for ((from, until) in listOf(0L to 0L, 10L to 9L, -1L to 1L, 0L to 253402300800000L)) {
            assertEquals(400, assertFailsWith<AdminException> { s.create(f.owner, r.copy(affectedFromEpochMillis = from, affectedUntilEpochMillis = until)) }.status)
        }
    }
    @Test fun `same actor request returns original committed incident and conflicting reuse fails`() = runTest {
        val (f, s) = fixture(); val r = request(f); val created = s.create(f.owner, r)
        assertEquals(created, s.create(f.owner, r))
        assertEquals(409, assertFailsWith<AdminException> { s.create(f.owner, r.copy(titleEn = "Different")) }.status)
    }
    @Test fun `current session origin csrf and support role guard replay and reads`() = runTest {
        val (f, s) = fixture(); val r = request(f); s.create(f.owner, r)
        assertEquals(403, assertFailsWith<AdminException> { s.create(f.owner.copy(csrfToken = null), r) }.status)
        assertEquals(403, assertFailsWith<AdminException> { s.create(f.owner.copy(origin = "http://evil.test"), r) }.status)
        assertEquals(401, assertFailsWith<AdminException> { s.list(GiftAdminContext(null), PageRequest()) }.status)
        val support = f.admin.createAccount(f.owner.token, f.owner.csrfToken, f.owner.origin,
            CreateAdminRequest("support@example.com", "GoodPassword123!", AdminRole.SUPPORT, "Fixture", "support"))
        val login = f.admin.login(support.email, "GoodPassword123!", f.owner.origin, "127.0.0.1")
        val ctx = GiftAdminContext(login.token, login.response.csrfToken, f.owner.origin)
        assertEquals("1", s.list(ctx, PageRequest()).total)
        assertEquals(403, assertFailsWith<AdminException> { s.create(ctx, r.copy(requestId = "support-create")) }.status)
        f.admin.logout(f.owner.token, f.owner.csrfToken, f.owner.origin)
        assertEquals(401, assertFailsWith<AdminException> { s.create(f.owner, r) }.status)
    }
    @Test fun `incident edit preserves creator and key uses version and freezes after first published round`() = runTest {
        val (f, s) = fixture(); val r = request(f); val created = s.create(f.owner, r)
        val edited = s.update(f.owner, created.id, r.copy(titleEn = "Edited", expectedVersion = 1, requestId = "edit"))
        assertEquals(2, edited.version); assertEquals("OUTAGE_1", edited.incidentKey); assertEquals(created.createdByAdminId, edited.createdByAdminId)
        assertEquals(created, s.create(f.owner, r))
        assertEquals(409, assertFailsWith<AdminException> { s.update(f.owner, created.id, r.copy(expectedVersion = 1, requestId = "stale")) }.status)
        assertEquals(409, assertFailsWith<AdminException> { s.update(f.owner, created.id, r.copy(incidentKey = "OTHER", expectedVersion = 2, requestId = "key")) }.status)
        val c = f.publish()
        f.gifts.repository.transaction {
            putCampaign(c.copy(compensation = CompensationMailboxContext(created.id, created.incidentKey, 1, 0, 10, "Ảnh hưởng", "Affected")))
            compensation.putRound(CompensationRound(c.id, created.id, 1, null, "Ảnh hưởng", "Affected"))
        }
        assertEquals(409, assertFailsWith<AdminException> { s.update(f.owner, created.id, r.copy(expectedVersion = 2, requestId = "frozen")) }.status)
    }
    @Test fun `utf16 field limits request guards and current responsible account are enforced`() = runTest {
        val (f, s) = fixture(); val r = request(f)
        val valid = r.copy(titleEn = "😀".repeat(80), descriptionEn = "x".repeat(4000), evidence = "x".repeat(10000), causeCode = "x".repeat(128))
        assertEquals(160, s.create(f.owner, valid).titleEn.length)
        for (bad in listOf(r.copy(titleEn = "😀".repeat(81)), r.copy(descriptionEn = "x".repeat(4001)), r.copy(evidence = "x".repeat(10001)),
            r.copy(causeCode = "x".repeat(129)), r.copy(incidentKey = "bad key"), r.copy(reason = "bad\nreason"), r.copy(requestId = "bad request"),
            r.copy(expectedVersion = 1), r.copy(responsibleAdminId = UUID.randomUUID().toString()))) {
            assertEquals(400, assertFailsWith<AdminException> { s.create(f.owner, bad.copy(requestId = if (bad.requestId == "bad request") bad.requestId else UUID.randomUUID().toString())) }.status)
        }
        val responsible = f.admin.createAccount(f.owner.token, f.owner.csrfToken, f.owner.origin,
            CreateAdminRequest("operator@example.com", "GoodPassword123!", AdminRole.OPERATOR, "Fixture", "operator"))
        val incident = s.create(f.owner, r.copy(incidentKey = "SECOND", requestId = "second", responsibleAdminId = responsible.id))
        f.admin.changeAccess(f.owner.token, f.owner.csrfToken, f.owner.origin, responsible.id, ChangeAdminAccessRequest(AdminRole.OPERATOR, false, "Disable", "disable"))
        assertEquals(responsible.id, s.update(f.owner, incident.id, r.copy(incidentKey = "SECOND", requestId = "edit-inactive-history", expectedVersion = 1, responsibleAdminId = responsible.id)).responsibleAdminId)
        assertEquals(400, assertFailsWith<AdminException> { s.create(f.owner, r.copy(incidentKey = "THIRD", requestId = "third", responsibleAdminId = responsible.id)) }.status)
    }
    @Test fun `round mapping rejects ordinary gift authority`() = runTest {
        val (f, s) = fixture(); val i = s.create(f.owner, request(f)); val c = f.publish()
        assertEquals(409, assertFailsWith<AdminException> {
            f.gifts.repository.transaction(true) { compensation.putRound(CompensationRound(c.id, i.id, 1, null, "Affected", "Affected")) }
        }.status)
        f.gifts.repository.transaction { assertEquals(0, compensation.maximumRoundNumber(i.id)); assertFalse(compensation.hasPublishedRound(i.id)) }
    }
}

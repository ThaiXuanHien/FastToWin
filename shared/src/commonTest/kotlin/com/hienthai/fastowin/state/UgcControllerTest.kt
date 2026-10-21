package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UgcControllerTest {
    @Test fun `report retry preserves request id and draft until matching receipt`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        controller.openReport(UgcTargetType.ROOM, "room-1", "Room")
        controller.editReport(UgcReportReason.HARASSMENT, "Evidence")
        controller.submitReport()
        val first = (sent.single() as ClientMessage.ReportContent).request
        assertNull(controller.state.value.report?.receipt)
        controller.receive(ServerMessage.ContentReportResult(first.requestId, errorCode = "RATE_LIMITED"))
        assertEquals("Evidence", controller.state.value.report?.note)
        controller.submitReport()
        assertEquals(first, (sent.last() as ClientMessage.ReportContent).request)
        controller.receive(ServerMessage.ContentReportResult("old-request", ContentReportReceipt("old")))
        assertNull(controller.state.value.report?.receipt)
        controller.receive(ServerMessage.ContentReportResult(first.requestId, ContentReportReceipt("accepted")))
        assertEquals("accepted", controller.state.value.report?.receipt?.reportId)
    }

    @Test fun `publication waits for checked consent and server acknowledgement of exact version`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        val mutation = ClientMessage.CreateClan("Name", "Description")
        controller.publish(mutation)
        assertEquals(listOf<ClientMessage>(ClientMessage.GetContentTerms), sent)
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("version-2", "version-1", true)))
        controller.acceptTerms()
        assertEquals(1, sent.size)
        controller.checkTerms(true)
        controller.acceptTerms()
        assertEquals(ClientMessage.AcceptContentTerms("version-2"), sent.last())
        assertFalse(sent.contains(mutation))
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("version-3", "version-2", true)))
        assertFalse(sent.contains(mutation))
        assertFalse(controller.state.value.termsChecked)
        controller.checkTerms(true)
        controller.acceptTerms()
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("version-3", "version-3", true)))
        assertEquals(mutation, sent.last())
        assertEquals(1, sent.count { it == mutation })
    }

    @Test fun `declining consent drops pending publication even after late acknowledgement`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        val mutation = ClientMessage.UpdateProfile("Name", null)
        controller.publish(mutation)
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", null, true)))
        controller.checkTerms(true)
        controller.acceptTerms()
        controller.dismissTerms()
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", "v", true)))
        assertFalse(sent.contains(mutation))
    }

    @Test fun `disconnect preserves report for manual retry and never replays public mutation`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        controller.openReport(UgcTargetType.PLAYER_AVATAR, "player", "Player")
        controller.editReport(UgcReportReason.OTHER, "Note")
        controller.submitReport()
        val request = (sent.last() as ClientMessage.ReportContent).request
        controller.publish(ClientMessage.UpdateProfile("Name", null))
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", null, true)))
        controller.checkTerms(true)
        controller.acceptTerms()
        controller.disconnected()
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", "v", true)))
        assertFalse(sent.any { it is ClientMessage.UpdateProfile })
        controller.submitReport()
        assertEquals(request.requestId, (sent.last() as ClientMessage.ReportContent).request.requestId)
    }

    @Test fun `server disabling enforcement permits existing player publication`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        val mutation = ClientMessage.UpdateProfile("Name", null)
        controller.publish(mutation)
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", null, false)))
        assertEquals(mutation, sent.last())
        assertFalse(controller.state.value.showTerms)
    }
    @Test fun `a delayed error from a closed report cannot erase the next draft`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        controller.openReport(UgcTargetType.CLAN, "old-clan", "Old")
        controller.submitReport()
        val oldId = (sent.last() as ClientMessage.ReportContent).request.requestId
        controller.dismissReport()
        controller.openReport(UgcTargetType.ROOM, "new-room", "New")
        controller.editReport(UgcReportReason.SPAM, "Keep this note")
        controller.receive(ServerMessage.ContentReportResult(oldId, errorCode = "REPORT_RATE_LIMITED"))
        assertEquals("new-room", controller.state.value.report?.targetId)
        assertEquals("Keep this note", controller.state.value.report?.note)
        assertNull(controller.state.value.report?.error)
    }

    @Test fun `a lost report response can be retried with the original request identity`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        controller.openReport(UgcTargetType.ROOM, "room", "Room")
        controller.submitReport()
        val request = (sent.last() as ClientMessage.ReportContent).request
        controller.reportTimedOut(request.requestId)
        controller.submitReport()
        assertEquals(request, (sent.last() as ClientMessage.ReportContent).request)
        assertNull(controller.state.value.report?.receipt)
    }

    @Test fun `server rules changing after status lookup require a new acknowledged acceptance`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        val mutation = ClientMessage.UpdateProfile("New name", null)
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("old", "old", true)))
        controller.publish(mutation)
        controller.receive(ServerMessage.Error(code = "CONTENT_TERMS_REQUIRED", message = "Rules changed"))
        assertEquals(ClientMessage.GetContentTerms, sent.last())
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("new", "old", true)))
        assertEquals(1, sent.count { it == mutation })
        controller.checkTerms(true)
        controller.acceptTerms()
        assertEquals(1, sent.count { it == mutation })
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("new", "new", true)))
        assertEquals(2, sent.count { it == mutation })
    }

    @Test fun `ending the account session erases drafts and ignores its late receipt`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        controller.openReport(UgcTargetType.PLAYER_PROFILE, "player", "Player")
        controller.editReport(UgcReportReason.HARASSMENT, "Private report note")
        controller.submitReport()
        val requestId = (sent.last() as ClientMessage.ReportContent).request.requestId
        controller.publish(ClientMessage.UpdateProfile("Pending name", null))
        controller.clearSession()
        controller.receive(ServerMessage.ContentReportResult(requestId, ContentReportReceipt("old-receipt")))
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", "v", true)))
        assertNull(controller.state.value.report)
        assertFalse(controller.state.value.showTerms)
        assertFalse(sent.any { it is ClientMessage.UpdateProfile })
    }

    @Test fun `reconnected publication keeps its draft but requires a fresh deliberate acknowledged acceptance`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        val mutation = ClientMessage.CreateClan("Keep this clan name", "Keep this description")
        controller.publish(mutation)
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", null, true)))
        controller.checkTerms(true)
        controller.acceptTerms()
        controller.disconnected()
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", "v", true)))
        assertFalse(sent.contains(mutation))
        controller.checkTerms(true)
        controller.acceptTerms()
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", "v", true)))
        assertEquals(1, sent.count { it == mutation })
        assertFalse(controller.state.value.showTerms)
    }

    @Test fun `a possibly sent clan creation is never replayed after disconnect`() = runTest {
        val sent = mutableListOf<ClientMessage>()
        val controller = UgcController { sent += it }
        val mutation = ClientMessage.CreateClan("Already sent", "Uncertain outcome")
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", "v", true)))
        controller.publish(mutation)
        controller.disconnected()
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", "v", true)))
        controller.checkTerms(true)
        controller.acceptTerms()
        controller.receive(ServerMessage.ContentTermsData(ContentTermsStatus("v", "v", true)))
        assertEquals(1, sent.count { it == mutation })
        assertFalse(controller.state.value.showTerms)
    }

}

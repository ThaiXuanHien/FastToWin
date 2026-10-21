package com.hienthai.fastowin.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

class UgcWireCompatibilityTest {
    @Test fun `legacy guest and signup decode without acceptance`() {
        assertNull((ProtocolJson.decodeFromString<ClientMessage>("""{"type":"connect_guest","displayName":"Guest"}""") as ClientMessage.ConnectGuest).acceptedContentTermsVersion)
        assertNull(ProtocolJson.decodeFromString<RegisterRequest>("""{"email":"e","password":"p","displayName":"Name"}""").acceptedContentTermsVersion)
    }
    @Test fun `terms and report commands round trip`() {
        val messages = listOf(ClientMessage.GetContentTerms, ClientMessage.AcceptContentTerms(CONTENT_TERMS_VERSION),
            ClientMessage.ReportContent(ContentReportRequest(UgcTargetType.ROOM,"room",UgcReportReason.SPAM,requestId="request")))
        messages.forEach { assertEquals(it, ProtocolJson.decodeFromString<ClientMessage>(ProtocolJson.encodeToString(it))) }
        val result:ServerMessage = ServerMessage.ContentReportResult("request", ContentReportReceipt("report"))
        assertEquals(result, ProtocolJson.decodeFromString<ServerMessage>(ProtocolJson.encodeToString(result)))
        val changed: ServerMessage = ServerMessage.ContentModerationChanged(UgcTargetType.PLAYER_AVATAR, "player")
        assertEquals(changed, ProtocolJson.decodeFromString<ServerMessage>(ProtocolJson.encodeToString(changed)))
    }
}

package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.ContentTermsStatus
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.ui.components.contentTermsLegalUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UgcEntryConsentTest {
    @Test fun `signup publishes only a user checked server version`() = runTest {
        val published = mutableListOf<String>()
        val consent = ContentEntryConsent { ContentTermsStatus("server-version-9", null, false) }
        consent.begin { published += it }
        assertFalse(consent.state.value.termsChecked)
        consent.accept()
        assertTrue(published.isEmpty())
        consent.check(true)
        consent.accept()
        assertEquals(listOf("server-version-9"), published)
        assertFalse(consent.state.value.showTerms)
    }
    @Test fun `registration failure keeps draft and renewed rules require another deliberate check`() = runTest {
        var version = "server-v1"
        var attempts = 0
        val consent = ContentEntryConsent { ContentTermsStatus(version, null, true) }
        consent.begin { attempts++; error("Unconfirmed") }
        consent.check(true)
        consent.accept()
        assertTrue(consent.state.value.showTerms)
        assertNotNull(consent.state.value.termsError)
        version = "server-v2"
        consent.retry()
        assertFalse(consent.state.value.termsChecked)
        consent.accept()
        assertEquals(1, attempts)
        assertEquals("server-v2", consent.state.value.terms?.requiredVersion)
    }
    @Test fun `closing during public rules lookup prevents a late response reopening signup consent`() = runTest {
        val response = CompletableDeferred<ContentTermsStatus>()
        val consent = ContentEntryConsent { response.await() }
        val job = launch { consent.begin { error("Must not publish") } }
        runCurrent()
        consent.dismiss()
        response.complete(ContentTermsStatus("v", null, true))
        job.join()
        assertFalse(consent.state.value.showTerms)
        assertNull(consent.state.value.terms)
    }
    @Test fun `legal links use the trusted public terms pages for selected language`() {
        assertEquals("https://fasttowin.up.railway.app/terms", contentTermsLegalUrl(AppLanguage.VIETNAMESE))
        assertEquals("https://fasttowin.up.railway.app/terms/en", contentTermsLegalUrl(AppLanguage.ENGLISH))
    }
}

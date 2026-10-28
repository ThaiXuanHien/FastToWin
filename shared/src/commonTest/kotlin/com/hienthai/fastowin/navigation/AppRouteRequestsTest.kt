package com.hienthai.fastowin.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppRouteRequestsTest {
    @Test fun explicitNavigationSupersedesPendingBrowserWorkAndOldPublishers() {
        val routes = AppRouteRequests()
        val wallet = routes.request("/account/wallet")
        val waitingRevision = routes.revision
        routes.supersede()
        assertNull(routes.pending)
        assertFalse(routes.acknowledge(wallet, "/account/wallet"))
        assertFalse(routes.canPublish(waitingRevision))
        assertTrue(routes.canPublish(routes.revision))
    }

    @Test fun browserRequestStaysPendingUntilItsDestinationIsApplied() {
        val routes = AppRouteRequests()
        val rooms = routes.request("/rooms")
        assertFalse(routes.acknowledge(rooms, "/account"))
        assertSame(rooms, routes.pending)
        assertFalse(routes.canPublish(routes.revision))
        assertTrue(routes.acknowledge(rooms, "/rooms"))
        assertTrue(routes.canPublish(routes.revision))
    }

    @Test fun olderBackCompletionCannotClearTheNextBackRequest() {
        val routes = AppRouteRequests()
        val account = routes.request("/account")
        val rooms = routes.request("/rooms")
        assertFalse(routes.acknowledge(account, "/account"))
        assertFalse(routes.clear(account))
        assertSame(rooms, routes.pending)
        assertTrue(routes.acknowledge(rooms, "/rooms"))
    }

    @Test fun oldPublicationRemainsStaleAfterTheNewRequestIsAcknowledged() {
        val routes = AppRouteRequests()
        val revisionBeforeBack = routes.revision
        val rooms = routes.request("/rooms")
        assertFalse(routes.canPublish(revisionBeforeBack))
        assertTrue(routes.acknowledge(rooms, "/rooms"))
        assertFalse(routes.canPublish(revisionBeforeBack))
        assertTrue(routes.canPublish(routes.revision))
    }

    @Test fun returningToTheSamePathStillHasANewOwner() {
        val routes = AppRouteRequests()
        val first = routes.request("/account")
        routes.request("/rooms")
        val latest = routes.request("/account")
        assertFalse(routes.acknowledge(first, "/account"))
        assertSame(latest, routes.pending)
        assertTrue(routes.acknowledge(latest, "/account"))
    }

    @Test fun canonicalizationCanClearOnlyTheRequestItHandles() {
        val routes = AppRouteRequests()
        val invalid = routes.request("/friends/missing")
        val latest = routes.request("/rooms")
        assertFalse(routes.clear(invalid))
        assertSame(latest, routes.pending)
        assertTrue(routes.clear(latest))
        assertTrue(routes.canPublish(routes.revision))
    }
}

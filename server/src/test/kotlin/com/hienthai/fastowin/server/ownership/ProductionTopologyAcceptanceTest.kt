package com.hienthai.fastowin.server.ownership

import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductionTopologyAcceptanceTest {
    private val routes = productionWorkerPublicPaths.map { ProductionWorkerRoute(it, 404, 404) }

    @Test fun `golden topology proof requires both real owners and exclusively A public sockets`() {
        val receipt = productionTopologyPreflightProjection(7, listOf(4, 0), listOf(0, 1), routes)
        assertEquals(2, receipt.getValue("goldenOwners").jsonArray.size)
        for (owners in listOf(listOf(0, 0), listOf(1, 1), listOf(0))) {
            assertFailsWith<IllegalStateException> { productionTopologyPreflightProjection(7, listOf(4, 0), owners, routes) }
        }
        for (sockets in listOf(listOf(0, 0), listOf(3, 1), listOf(4))) {
            assertFailsWith<IllegalStateException> { productionTopologyPreflightProjection(7, sockets, listOf(0, 1), routes) }
        }
    }

    @Test fun `missing worker route observation or public response cannot pass`() {
        assertFailsWith<IllegalStateException> { productionTopologyPreflightProjection(7, listOf(4, 0), listOf(0, 1), routes.drop(1)) }
        assertFailsWith<IllegalStateException> { productionTopologyPreflightProjection(7, listOf(4, 0), listOf(0, 1),
            routes.mapIndexed { index, route -> if (index == 0) route.copy(get = 200) else route }) }
        assertFailsWith<IllegalStateException> { productionTopologyPreflightProjection(0, listOf(4, 0), listOf(0, 1), routes) }
    }
}
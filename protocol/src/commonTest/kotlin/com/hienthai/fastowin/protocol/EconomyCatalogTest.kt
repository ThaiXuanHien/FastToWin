package com.hienthai.fastowin.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class EconomyCatalogTest {
    @Test
    fun `gold exchange catalog uses the approved rates`() {
        assertEquals(
            listOf(
                GoldExchangeOffer("gold_bag", gemsCost = 10, goldAmount = 1_000),
                GoldExchangeOffer("gold_chest", gemsCost = 45, goldAmount = 5_000),
                GoldExchangeOffer("gold_vault", gemsCost = 80, goldAmount = 10_000)
            ),
            GOLD_EXCHANGE_OFFERS
        )
    }

    @Test
    fun `gold exchange offer ids are unique`() {
        assertEquals(
            GOLD_EXCHANGE_OFFERS.size,
            GOLD_EXCHANGE_OFFERS.map(GoldExchangeOffer::id).distinct().size
        )
    }
}

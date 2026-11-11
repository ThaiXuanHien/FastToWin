package com.hienthai.fastowin.server.ownership

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomOwnershipRollbackGuardTest {
    @Test
    fun `rollback is allowed only after drain and durable work are empty`() {
        val guard = RoomOwnershipRollbackGuard()

        assertFalse(guard.canRollback(RoomOwnershipDrainReceipt(false, 0, 0)))
        assertFalse(guard.canRollback(RoomOwnershipDrainReceipt(true, 1, 0)))
        assertFalse(guard.canRollback(RoomOwnershipDrainReceipt(true, 0, 1)))
        assertTrue(guard.canRollback(RoomOwnershipDrainReceipt(true, 0, 0)))
    }

    @Test
    fun `receipt cannot claim rollback when counts are negative`() {
        val guard = RoomOwnershipRollbackGuard()

        assertFalse(guard.canRollback(RoomOwnershipDrainReceipt(true, -1, 0)))
        assertFalse(guard.canRollback(RoomOwnershipDrainReceipt(true, 0, -1)))
    }
}


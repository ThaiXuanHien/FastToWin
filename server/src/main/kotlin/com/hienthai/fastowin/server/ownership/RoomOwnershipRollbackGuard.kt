package com.hienthai.fastowin.server.ownership

internal data class RoomOwnershipDrainReceipt(
    val drained: Boolean,
    val remainingOwnedLeases: Int,
    val pendingSettlements: Int
)

internal class RoomOwnershipRollbackGuard {
    fun canRollback(receipt: RoomOwnershipDrainReceipt): Boolean =
        receipt.drained &&
            receipt.remainingOwnedLeases == 0 &&
            receipt.pendingSettlements == 0
}

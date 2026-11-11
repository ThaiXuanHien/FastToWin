package com.hienthai.fastowin.server.ownership

internal data class RoomOwnershipDrainReceipt(
    val drained: Boolean,
    val remainingOwnedLeases: Int,
    val pendingSettlements: Int,
    val remainingOpenRooms: Int = 0,
    val storageVerified: Boolean = true
)

internal data class RoomOwnershipDurableSnapshot(
    val remainingOwnedLeases: Int,
    val remainingOpenRooms: Int,
    val pendingSettlements: Int
)

internal class RoomOwnershipRollbackGuard {
    fun canRollback(receipt: RoomOwnershipDrainReceipt): Boolean =
        receipt.drained && receipt.storageVerified &&
            receipt.remainingOwnedLeases == 0 &&
            receipt.remainingOpenRooms == 0 &&
            receipt.pendingSettlements == 0
}

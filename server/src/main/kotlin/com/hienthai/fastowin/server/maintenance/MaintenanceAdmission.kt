package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.server.Delivery

class MaintenanceAdmissionException : RuntimeException("Game admission unavailable")

data class MaintenanceApplication(
    val deliveries: List<Delivery> = emptyList(),
    val abortedRoomIds: Set<String> = emptySet(),
    val closeSockets: Boolean = false
)

/** Paid receipts were admitted by the store; finishing them never admits a new purchase. */
internal fun ClientMessage.allowedDuringMaintenance(policy: MaintenancePolicy): Boolean = when (this) {
    is ClientMessage.VerifyStorePurchase -> true
    is ClientMessage.SelectNumber, is ClientMessage.SendEmoji, is ClientMessage.LeaveRoom,
    is ClientMessage.MeasureLatency, is ClientMessage.UpdateLatency -> policy.allowsExistingMatch
    ClientMessage.GetProfile, ClientMessage.GetWalletHistory, ClientMessage.GetPlayQuota,
    ClientMessage.GetNotifications, is ClientMessage.GetMatchDetail -> true
    else -> !policy.blocksNewIntent
}

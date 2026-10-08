package com.hienthai.fastowin.server

/** Immutable admission identity, with the durable payload retained after an uncertain write. */
internal class PreparedMatchStart(
    val roomId: String,
    val matchId: String,
    val previousMatchId: String,
    val sequence: Long,
    val playerIds: Set<String>,
    val requesterId: String,
    val fromMatchmaking: Boolean = false,
    var consumption: PlayQuotaConsumption? = null,
    var candidate: StoredActiveRoom? = null
)

package com.hienthai.fastowin.server.activity

interface ActivityRepository {
    suspend fun checkpoint(checkpoint: ActivityCheckpoint)
}

object UnavailableActivityRepository : ActivityRepository {
    override suspend fun checkpoint(checkpoint: ActivityCheckpoint) = Unit
}

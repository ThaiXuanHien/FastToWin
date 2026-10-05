package com.hienthai.fastowin.server.analytics

import com.hienthai.fastowin.server.admin.*

interface AnalyticsRepository {
    suspend fun read(admin: AdminService, token: String?, query: AnalyticsQuery): AnalyticsResponse
}
object UnavailableAnalyticsRepository : AnalyticsRepository {
    override suspend fun read(admin: AdminService, token: String?, query: AnalyticsQuery): AnalyticsResponse {
        admin.repository.transaction { requireMetrics(admin.authorize(this, token), query) }
        unavailable()
    }
}
internal fun requireMetrics(actor: AdminIdentity, query: AnalyticsQuery) {
    if (actor.role == AdminRole.SUPPORT || actor.role != AdminRole.OWNER && query.metrics.any { it.financial }) forbidden()
}
class AnalyticsService(private val repository: AnalyticsRepository, private val admin: AdminService,
    private val nowMillis: () -> Long = System::currentTimeMillis) {
    suspend fun read(token: String?, origin: String?, parameters: Map<String, List<String>>): AnalyticsResponse {
        origin?.let(admin::requireOrigin)
        return repository.read(admin, token, AnalyticsQuery.parse(parameters, nowMillis()))
    }
}

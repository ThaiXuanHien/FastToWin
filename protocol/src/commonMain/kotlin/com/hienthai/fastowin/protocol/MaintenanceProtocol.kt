package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable enum class MaintenanceState { ACTIVE, ANNOUNCING, DRAINING, MAINTENANCE }
@Serializable enum class MaintenanceMode { PLANNED, EMERGENCY }

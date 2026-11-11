package com.hienthai.fastowin.server.ownership

internal enum class RoomOwnershipRuntimeRole {
    INGRESS, OWNED_WORKER;

    companion object {
        fun fromEnvironment(values: Map<String, String> = System.getenv()): RoomOwnershipRuntimeRole =
            when (values["FASTTOWIN_ROOM_OWNERSHIP_ROLE"]?.trim()) {
                null, "", "ingress" -> INGRESS
                "owned-worker" -> OWNED_WORKER
                else -> throw IllegalArgumentException("Unknown room ownership runtime role")
            }
    }

    fun validate(environment: String, values: Map<String, String>) {
        if (this != OWNED_WORKER) return
        require(environment == "prod" && values["FASTTOWIN_ROOM_OWNERSHIP_MODE"]?.trim() == "production") {
            "An owned worker requires production room ownership"
        }
        require(values["DATABASE_URL"]?.startsWith("jdbc:postgresql://") == true) {
            "An owned worker requires PostgreSQL"
        }
    }
}

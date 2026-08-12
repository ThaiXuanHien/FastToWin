package com.hienthai.fastowin.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val environment = System.getenv("FASTTOWIN_ENV")?.lowercase() ?: "dev"
    require(environment in setOf("dev", "prod")) {
        "FASTTOWIN_ENV must be either 'dev' or 'prod'."
    }
    val host = System.getenv("SERVER_HOST") ?: "0.0.0.0"
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val database = DatabaseSettings.fromEnvironment(environment)?.let(DatabaseRuntime::open)
    val identityRepository = database?.identityRepository ?: InMemoryGuestIdentityRepository()
    val matchResultRepository = database?.matchResultRepository ?: NoOpMatchResultRepository
    val playerProfileRepository = database?.playerProfileRepository ?: NoOpPlayerProfileRepository
    val storage = if (database == null) "memory" else "postgresql"

    println("Starting Fast To Win server: environment=$environment, host=$host, port=$port, storage=$storage")
    try {
        embeddedServer(Netty, host = host, port = port) {
            gameModule(GameEngine(identityRepository, matchResultRepository, playerProfileRepository))
        }.start(wait = true)
    } finally {
        database?.close()
    }
}

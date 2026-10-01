package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.DatabaseRuntime
import com.hienthai.fastowin.server.DatabaseSettings
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

/** Offline bootstrap only. No default credentials and no player account is upgraded. */
fun main() = runBlocking {
    val values = System.getenv()
    val environment = values["FASTTOWIN_ENV"]?.lowercase() ?: "dev"
    val configuration = requireNotNull(AdminConfiguration.fromEnvironment(environment, values)) { "Enable and configure admin before bootstrap." }
    val settings = requireNotNull(DatabaseSettings.fromEnvironment(environment, values)) { "Bootstrap requires PostgreSQL persistence." }
    val console = System.console()
    val email = values["FASTTOWIN_ADMIN_BOOTSTRAP_EMAIL"] ?: console?.readLine("Owner email: ")
        ?: error("Set FASTTOWIN_ADMIN_BOOTSTRAP_EMAIL or use an interactive console.")
    val password = values["FASTTOWIN_ADMIN_BOOTSTRAP_PASSWORD_FILE"]?.let { name ->
        val path = Path.of(name)
        require(Files.size(path) in 1..4096) { "Password secret file has invalid size." }
        Files.readString(path).trimEnd('\r', '\n').toCharArray()
    } ?: console?.readPassword("Owner password (at least 12 characters): ")
        ?: error("A masked console or FASTTOWIN_ADMIN_BOOTSTRAP_PASSWORD_FILE is required; plaintext command-line passwords are unsupported.")
    try {
        DatabaseRuntime.open(settings).use { database ->
            val identity = AdminService(database.adminRepository, configuration).bootstrapOwner(email, String(password))
            println("Owner bootstrap complete: id=${identity.id}")
        }
    } finally { password.fill('\u0000') }
}

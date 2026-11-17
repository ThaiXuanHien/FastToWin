package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.DatabaseRuntime
import com.hienthai.fastowin.server.DatabaseSettings
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

/**
 * ADM-10: offline emergency recovery for an admin who lost their password or second
 * factor (including the last Owner). It needs direct database access, so it can never be
 * reached through the console. Runbook: docs/admin-management.md.
 */
fun main() = runBlocking {
    val values = System.getenv()
    val environment = values["FASTTOWIN_ENV"]?.lowercase() ?: "dev"
    val configuration = requireNotNull(AdminConfiguration.fromEnvironment(environment, values)) { "Enable and configure admin before recovery." }
    val settings = requireNotNull(DatabaseSettings.fromEnvironment(environment, values)) { "Recovery requires PostgreSQL persistence." }
    val console = System.console()
    val email = values["FASTTOWIN_ADMIN_RECOVERY_EMAIL"] ?: console?.readLine("Admin email to recover: ")
        ?: error("Set FASTTOWIN_ADMIN_RECOVERY_EMAIL or use an interactive console.")
    val reason = values["FASTTOWIN_ADMIN_RECOVERY_REASON"] ?: console?.readLine("Reason (recorded in the audit log): ")
        ?: error("Set FASTTOWIN_ADMIN_RECOVERY_REASON or use an interactive console.")
    val password = values["FASTTOWIN_ADMIN_RECOVERY_PASSWORD_FILE"]?.let { name ->
        val path = Path.of(name)
        require(Files.size(path) in 1..4096) { "Password secret file has invalid size." }
        Files.readString(path).trimEnd('\r', '\n').toCharArray()
    } ?: console?.readPassword("New password (at least 12 characters): ")
        ?: error("A masked console or FASTTOWIN_ADMIN_RECOVERY_PASSWORD_FILE is required; plaintext command-line passwords are unsupported.")
    try {
        DatabaseRuntime.open(settings).use { database ->
            val identity = AdminService(database.adminRepository, configuration).recoverOffline(email, String(password), reason)
            println("Admin recovery complete: id=${identity.id} role=${identity.role}. Sign in to enroll a new authenticator.")
        }
    } finally { password.fill('\u0000') }
}

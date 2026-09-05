package com.hienthai.fastowin.server

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

internal data class AllowedWebOrigin(
    val authority: String,
    val scheme: String
)

internal fun readEnvironmentSecret(
    name: String,
    values: Map<String, String> = System.getenv(),
    fileReader: (String) -> String = { Files.readString(Path.of(it)) }
): String? {
    val directValue = values[name]?.takeIf(String::isNotBlank)
    val filePath = values["${name}_FILE"]?.trim()?.takeIf(String::isNotEmpty)
    require(directValue == null || filePath == null) {
        "Configure either $name or ${name}_FILE, not both."
    }
    return (directValue ?: filePath?.let { path ->
        runCatching { fileReader(path) }
            .getOrElse { error -> throw IllegalArgumentException("Could not read $name from $path.", error) }
    })
        ?.trimEnd('\r', '\n')
        ?.takeIf(String::isNotBlank)
}

internal fun parseAllowedWebOrigins(
    rawValue: String?,
    requireHttps: Boolean
): List<AllowedWebOrigin> = rawValue
    ?.split(',')
    .orEmpty()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .map { rawOrigin ->
        val uri = runCatching { URI(rawOrigin) }
            .getOrElse { throw IllegalArgumentException("Invalid Web origin: $rawOrigin", it) }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") {
            "Web origin must use http or https: $rawOrigin"
        }
        require(!requireHttps || scheme == "https") {
            "Production Web origin must use https: $rawOrigin"
        }
        require(uri.host != null && uri.userInfo == null) {
            "Web origin must contain a valid host and no credentials: $rawOrigin"
        }
        require(uri.rawQuery == null && uri.rawFragment == null && (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) {
            "Web origin must not contain a path, query or fragment: $rawOrigin"
        }
        require(!requireHttps || (!uri.host.equals("localhost", ignoreCase = true) && uri.host != "127.0.0.1")) {
            "Production Web origin must use a public domain: $rawOrigin"
        }
        AllowedWebOrigin(
            authority = requireNotNull(uri.rawAuthority),
            scheme = requireNotNull(scheme)
        )
    }
    .distinct()

internal fun validateProductionEnvironment(
    values: Map<String, String> = System.getenv(),
    fileReader: (String) -> String = { Files.readString(Path.of(it)) }
) {
    val publicUrl = values.required("FASTTOWIN_PUBLIC_URL")
    val publicUri = runCatching { URI(publicUrl) }
        .getOrElse { throw IllegalArgumentException("FASTTOWIN_PUBLIC_URL is invalid.", it) }
    require(
        publicUri.scheme.equals("https", ignoreCase = true) &&
            publicUri.host != null &&
            publicUri.userInfo == null &&
            publicUri.rawQuery == null &&
            publicUri.rawFragment == null &&
            (publicUri.rawPath.isNullOrEmpty() || publicUri.rawPath == "/")
    ) {
        "FASTTOWIN_PUBLIC_URL must be an HTTPS origin without a path, query or fragment."
    }
    require(!publicUri.host.equals("localhost", ignoreCase = true) && publicUri.host != "127.0.0.1") {
        "FASTTOWIN_PUBLIC_URL must use the public production domain."
    }
    require(!publicUrl.contains("configure-production", ignoreCase = true) && !publicUrl.contains("example.", ignoreCase = true)) {
        "FASTTOWIN_PUBLIC_URL still contains a placeholder domain."
    }

    val allowedOrigins = parseAllowedWebOrigins(values.required("FASTTOWIN_WEB_ORIGINS"), requireHttps = true)
    require(allowedOrigins.isNotEmpty()) { "FASTTOWIN_WEB_ORIGINS must contain at least one HTTPS origin." }
    require(allowedOrigins.any { origin ->
        origin.scheme == publicUri.scheme.lowercase() &&
            origin.authority.equals(publicUri.rawAuthority, ignoreCase = true)
    }) {
        "FASTTOWIN_WEB_ORIGINS must include FASTTOWIN_PUBLIC_URL."
    }

    require(values.required("DATABASE_URL").startsWith("jdbc:postgresql://")) {
        "DATABASE_URL must be a PostgreSQL JDBC URL in production."
    }
    val databasePassword = requireNotNull(readEnvironmentSecret("DATABASE_PASSWORD", values, fileReader)) {
        "DATABASE_PASSWORD or DATABASE_PASSWORD_FILE is required in production."
    }
    require(databasePassword != "fasttowin" && databasePassword.length >= 12) {
        "Use a non-default DATABASE_PASSWORD with at least 12 characters in production."
    }

    values.required("FASTTOWIN_SMTP_HOST")
    values.required("FASTTOWIN_SMTP_USERNAME")
    values.required("FASTTOWIN_SMTP_FROM_EMAIL")
    val smtpPassword = requireNotNull(readEnvironmentSecret("FASTTOWIN_SMTP_PASSWORD", values, fileReader)) {
        "FASTTOWIN_SMTP_PASSWORD or FASTTOWIN_SMTP_PASSWORD_FILE is required in production."
    }
    require(smtpPassword.length >= 8 && !smtpPassword.equals("smtp-secret", ignoreCase = true)) {
        "FASTTOWIN_SMTP_PASSWORD is too weak or still a placeholder."
    }
    val smtpSsl = values.strictBoolean("FASTTOWIN_SMTP_SSL", default = false)
    val smtpStartTls = values.strictBoolean("FASTTOWIN_SMTP_STARTTLS", default = !smtpSsl)
    require(smtpSsl.xor(smtpStartTls)) {
        "Production SMTP must enable exactly one of STARTTLS or SSL."
    }
}

private fun Map<String, String>.required(name: String): String =
    get(name)?.trim()?.takeIf(String::isNotEmpty)
        ?: throw IllegalArgumentException("$name is required in production.")

private fun Map<String, String>.strictBoolean(name: String, default: Boolean): Boolean {
    val rawValue = get(name)?.trim()?.takeIf(String::isNotEmpty) ?: return default
    return rawValue.toBooleanStrictOrNull()
        ?: throw IllegalArgumentException("$name must be either true or false.")
}

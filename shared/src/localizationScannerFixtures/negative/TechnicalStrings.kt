fun technicalStrings(enabled: Boolean): List<String> = listOf(
    "/rooms",
    "language_option_vi",
    "escaped quote: \"",
    "${if (enabled) "enabled" else "disabled"}",
    """protocol.error_code""",
)

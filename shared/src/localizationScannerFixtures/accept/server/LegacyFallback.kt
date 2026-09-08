fun legacyFallback(value: String): String = value

fun compatibilityFallback() = legacyFallback("Thông báo tương thích cũ")

package com.hienthai.fastowin.ui.format

import androidx.compose.runtime.Composable
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalLocalization

/**
 * Quy tắc rút gọn số của thiết kế A1 (canvas bản 80, Design system):
 * tới 9.999 hiện đủ; từ 10.000 rút gọn: 12,5K · 1,25M · 1,2B (tiếng Anh: 12.5K · 1.25M · 1.2B).
 * Số được cắt xuống (không làm tròn lên) để không bao giờ hiện nhiều hơn số thật.
 * Lịch sử, cửa hàng, quyên góp, kết quả và Elo luôn hiện số đầy đủ ([formatFullNumber]).
 */
fun formatCompactNumber(value: Long, language: AppLanguage = AppLanguage.VIETNAMESE): String {
    val negative = value < 0
    val v = if (negative) -value else value
    val decimal = if (language == AppLanguage.VIETNAMESE) ',' else '.'
    val text = when {
        v < 10_000 -> formatFullNumber(v, language)
        v < 1_000_000 -> scaled(v, 1_000, 1, decimal) + "K"
        v < 1_000_000_000 -> scaled(v, 1_000_000, 2, decimal) + "M"
        else -> scaled(v, 1_000_000_000, 1, decimal) + "B"
    }
    return if (negative) "-$text" else text
}

/** Số đầy đủ có phân cách hàng nghìn: 12.500 (vi) hoặc 12,500 (en). */
fun formatFullNumber(value: Long, language: AppLanguage = AppLanguage.VIETNAMESE): String {
    val sep = if (language == AppLanguage.VIETNAMESE) '.' else ','
    val digits = (if (value < 0) -value else value).toString()
    val grouped = buildString {
        digits.forEachIndexed { i, ch ->
            if (i > 0 && (digits.length - i) % 3 == 0) append(sep)
            append(ch)
        }
    }
    return if (value < 0) "-$grouped" else grouped
}

private fun scaled(v: Long, unit: Long, maxDecimals: Int, decimal: Char): String {
    var pow = 1L
    repeat(maxDecimals) { pow *= 10 }
    val truncated = v * pow / unit
    val whole = truncated / pow
    val frac = (truncated % pow).toString().padStart(maxDecimals, '0').trimEnd('0')
    return if (frac.isEmpty()) whole.toString() else "$whole$decimal$frac"
}

@Composable
fun compactNumber(value: Long): String = formatCompactNumber(value, LocalLocalization.current.language)

@Composable
fun fullNumber(value: Long): String = formatFullNumber(value, LocalLocalization.current.language)

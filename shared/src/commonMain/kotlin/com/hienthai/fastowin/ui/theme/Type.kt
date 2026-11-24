package com.hienthai.fastowin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.baloo2_variable
import com.hienthai.fastowin.resources.be_vietnam_pro_bold
import com.hienthai.fastowin.resources.be_vietnam_pro_extrabold
import com.hienthai.fastowin.resources.be_vietnam_pro_medium
import com.hienthai.fastowin.resources.be_vietnam_pro_regular
import com.hienthai.fastowin.resources.be_vietnam_pro_semibold
import org.jetbrains.compose.resources.Font

/** Baloo 2 (tiêu đề, nút, con số) — font biến thiên, chọn độ đậm qua trục wght. */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun balooFamily(): FontFamily {
    val fonts = listOf(500, 600, 700, 800).map { w ->
        Font(
            Res.font.baloo2_variable,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w))
        )
    }
    return remember(fonts) { FontFamily(fonts) }
}

/** Be Vietnam Pro (nội dung) — hỗ trợ đầy đủ dấu tiếng Việt. */
@Composable
private fun beVietnamProFamily(): FontFamily {
    val fonts = listOf(
        Font(Res.font.be_vietnam_pro_regular, FontWeight.Normal),
        Font(Res.font.be_vietnam_pro_medium, FontWeight.Medium),
        Font(Res.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
        Font(Res.font.be_vietnam_pro_bold, FontWeight.Bold),
        Font(Res.font.be_vietnam_pro_extrabold, FontWeight.ExtraBold),
        Font(Res.font.be_vietnam_pro_extrabold, FontWeight.Black)
    )
    return remember(fonts) { FontFamily(fonts) }
}

/** Typography A1: Baloo 2 cho display/headline/title, Be Vietnam Pro cho body/label. */
@Composable
fun a1Typography(): Typography {
    val display = balooFamily()
    val body = beVietnamProFamily()
    return remember(display, body) { buildTypography(display, body) }
}

private fun buildTypography(DisplayFont: FontFamily, BodyFont: FontFamily) = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 39.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 25.sp,
        lineHeight = 31.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    )
)

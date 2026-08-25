package com.hienthai.fastowin.ui.theme

import androidx.compose.ui.graphics.Color

/** Primitive color tokens for the Fast To Win 2D Arcade visual language. */
object ArcadePalette {
    val Blue50 = Color(0xFFF1F7FF)
    val Blue100 = Color(0xFFDCEAFF)
    val Blue300 = Color(0xFF86B5FF)
    val Blue500 = Color(0xFF347CFF)
    val Blue600 = Color(0xFF246BFD)
    val Blue700 = Color(0xFF1552D8)
    val Blue900 = Color(0xFF0A2C79)

    val Violet100 = Color(0xFFEDE5FF)
    val Violet400 = Color(0xFF9D7BFF)
    val Violet600 = Color(0xFF7048E8)
    val Violet900 = Color(0xFF32156F)

    val Coral100 = Color(0xFFFFE1E7)
    val Coral400 = Color(0xFFFF8294)
    val Coral600 = Color(0xFFFF4D67)
    val Coral800 = Color(0xFFB91F3C)

    val Gold100 = Color(0xFFFFF3BF)
    val Gold400 = Color(0xFFFFD84D)
    val Gold500 = Color(0xFFFFC928)
    val Gold800 = Color(0xFF704B00)

    val Mint100 = Color(0xFFD5FAEF)
    val Mint400 = Color(0xFF39DDB0)
    val Mint600 = Color(0xFF08A97D)
    val Mint900 = Color(0xFF064F3E)

    val Navy950 = Color(0xFF06132F)
    val Navy900 = Color(0xFF071A44)
    val Navy800 = Color(0xFF0D285D)
    val Navy700 = Color(0xFF153A7B)
    val Ink = Color(0xFF10203F)
    val MutedInk = Color(0xFF526581)

    val Cloud = Color(0xFFF4F8FF)
    val White = Color(0xFFFFFFFF)
    val OutlineLight = Color(0xFFB9CAE5)
    val OutlineDark = Color(0xFF5572A8)
}

/** Semantic accents that stay recognizable across light and dark surfaces. */
val ArcadeGold = ArcadePalette.Gold500
val ArcadeGem = ArcadePalette.Mint400
val ArcadeOpponent = ArcadePalette.Coral600
val ArcadeSuccess = ArcadePalette.Mint600

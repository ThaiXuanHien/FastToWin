package com.hienthai.fastowin.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Tintable clan artwork. Unknown and legacy missing IDs keep the original shield identity. */
fun clanEmblemImageVector(logoId: String?): ImageVector = when (logoId) {
    "flag" -> ClanEmblemVectors.flag
    "sword" -> ClanEmblemVectors.sword
    "wolf" -> ClanEmblemVectors.wolf
    "eagle" -> ClanEmblemVectors.eagle
    "lion" -> ClanEmblemVectors.lion
    "dragon" -> ClanEmblemVectors.dragon
    "phoenix" -> ClanEmblemVectors.phoenix
    "crown" -> ClanEmblemVectors.crown
    "kirin" -> ClanEmblemVectors.kirin
    else -> ClanEmblemVectors.shield
}

/** One shared 48-unit grid and solid fill keep the emblems coherent at compact sizes. */
private object ClanEmblemVectors {
    val shield = emblem(
        "ClanShield",
        // Broad heraldic shield with a transparent inset, independent of its background.
        """
        M24,3 L41,9 L39,26 Q36,38 24,45 Q12,38 9,26 L7,9 Z
        M24,11 L15,14 L16.5,25 Q18,32 24,36 Q30,32 31.5,25 L33,14 Z
        """
    )

    val flag = emblem(
        "ClanFlag",
        """
        M7,4 L11,4 L11,7 Q20,2 28,7 Q36,11 43,6
        L40,29 Q33,34 25,30 Q17,26 11,30 L11,41 L15,41 L15,45 L3,45 L3,41 L7,41 Z
        M15,13 L15,18 Q21,16 28,20 Q33,23 37,21 L38,16 Q33,18 28,15 Q21,11 15,13 Z
        """
    )

    val sword = emblem(
        "ClanCrossedSwords",
        // Separate paths preserve a solid crossing instead of cutting an even-odd hole.
        "M5,4 L13,7 L31,25 L26,30 L8,12 Z",
        "M23,27 L27,23 L39,35 L35,39 Z",
        "M30,35 L35,30 L43,38 L38,43 Z",
        "M36,42 L42,36 L46,40 L40,46 Z",
        "M43,4 L40,12 L22,30 L17,25 L35,7 Z",
        "M21,23 L25,27 L13,39 L9,35 Z",
        "M13,30 L18,35 L10,43 L5,38 Z",
        "M6,36 L12,42 L8,46 L2,40 Z"
    )

    val wolf = emblem(
        "ClanWolf",
        // Tall ears, swept cheek tufts and a narrow muzzle distinguish the wolf from the lion.
        """
        M6,4 L19,13 L24,11 L29,13 L42,4 L40,22 L44,29
        L35,31 L31,39 L24,45 L17,39 L13,31 L4,29 L8,22 Z
        M11,12 L12,21 L16,17 Z
        M37,12 L32,17 L36,21 Z
        M12,23 L21,26 L19,29 L13,27 Z
        M36,23 L27,26 L29,29 L35,27 Z
        M20,33 L28,33 L24,38 Z
        """
    )

    val eagle = emblem(
        "ClanEagle",
        // Hooked beak, stepped flight feathers and a fanned tail remain visible when reduced.
        """
        M4,7 L19,18 L20,13 Q20,7 25,8 L32,12 L27,15 L28,18 L44,7
        L43,18 L39,20 L44,23 L35,25 L40,28 L30,31 L29,34
        L33,42 L24,38 L15,42 L19,34 L18,31 L8,28 L13,25 L4,23 L9,20 L5,18 Z
        M24,11 L27,12 L25,14 Z
        M10,15 L18,21 L17,24 L10,19 Z
        M38,15 L38,19 L31,24 L30,21 Z
        """
    )

    val lion = emblem(
        "ClanLion",
        // A radial mane frames a round-eared feline face; all dark details are cutouts.
        """
        M24,3 L30,7 L37,6 L39,13 L45,18 L42,25 L44,32
        L37,36 L33,43 L25,41 L18,45 L13,38 L6,35 L7,27 L3,21 L9,16 L10,9 L18,8 Z
        M24,11 Q17,9 12,15 L12,23 L15,32 L24,39 L33,32 L36,23 L36,15 Q31,9 24,11 Z
        M24,15 Q18,12 16,18 L16,24 L19,30 L24,34 L29,30 L32,24 L32,18 Q30,12 24,15 Z
        M18,21 L22,23 L20,26 L18,24 Z
        M30,21 L26,23 L28,26 L30,24 Z
        M21,28 L27,28 L24,31 Z
        """
    )

    val dragon = emblem(
        "ClanDragon",
        // Profile: two horns, a long scaled snout, open jaw and a spiked serpentine neck.
        """
        M24,4 L29,12 L36,7 L33,16 L40,15 L44,22 L41,28
        L32,29 L36,32 L30,35 Q28,40 23,44 L14,43 L17,34
        L9,36 L13,27 L5,26 L12,19 L8,9 L21,15 Z
        M27,19 L34,21 L29,24 Z
        M38,21 L41,22 L39,25 L37,24 Z
        M17,26 L22,24 L20,32 L23,35 L20,39 L17,37 L18,32 Z
        """
    )

    val phoenix = emblem(
        "ClanPhoenix",
        // Flame-shaped wings and three long tail flames give the phoenix a rising silhouette.
        """
        M24,3 Q18,9 22,14 L19,21 L13,15 L10,4 Q3,11 6,21
        L3,17 Q2,28 13,32 L9,38 L20,34 L17,45 L24,40
        L31,45 L28,34 L39,38 L35,32 Q46,28 45,17 L42,21
        Q45,11 38,4 L35,15 L29,21 L27,15 L32,12 L27,10 Z
        M10,16 Q11,23 17,26 L18,30 Q9,28 10,16 Z
        M38,16 Q37,23 31,26 L30,30 Q39,28 38,16 Z
        M23,24 L25,24 L27,32 L24,36 L21,32 Z
        """
    )

    val crown = emblem(
        "ClanCrown",
        """
        M4,11 L16,21 L24,5 L32,21 L44,11 L39,35 L9,35 Z
        M10,38 L38,38 L38,44 L10,44 Z
        M24,20 L28,27 L24,31 L20,27 Z
        """
    )

    val kirin = emblem(
        "ClanKirin",
        // Full mythical beast profile: single long horn, scaled mane, curled tail and hooves.
        """
        M35,10 L38,2 L40,10 L45,13 L42,18 L37,18 L35,23
        L39,28 L36,35 L36,44 L31,44 L31,34 L28,29
        L22,30 L18,35 L18,44 L13,44 L13,33 L16,28
        Q9,27 9,21 Q10,15 17,16 L26,20 L29,18 L30,14 L27,9 L33,11 L31,5 Z
        M15,20 L24,23 L22,26 L15,24 Z
        M38,12 L41,13 L39,15 Z
        M15,40 L15,44 L17,44 L17,40 Z
        M33,40 L33,44 L35,44 L35,40 Z
        """,
        "M11,21 Q5,24 4,19 Q3,15 7,12 Q1,12 1,19 Q1,28 13,27 L15,24 Z"
    )
}

private fun emblem(name: String, vararg silhouettes: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 48f,
        viewportHeight = 48f
    )
    silhouettes.forEach { silhouette ->
        builder.addPath(
            pathData = PathParser().parsePathString(silhouette.trimIndent()).toNodes(),
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.EvenOdd
        )
    }
    return builder.build()
}

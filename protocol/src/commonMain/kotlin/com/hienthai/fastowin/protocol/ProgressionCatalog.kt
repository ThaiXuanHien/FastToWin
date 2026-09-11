package com.hienthai.fastowin.protocol

data class ProgressionCosmeticDefinition(
    val id: String,
    val fallbackName: String,
    val nameKey: String,
    val type: CosmeticType
)

val FRAME_CATALOG = listOf(
    frame("frame_lightning", "Tia Chớp", "FrameLightningName"),
    frame("frame_wildfire", "Liệt Hỏa", "FrameWildfireName"),
    frame("frame_warrior", "Chiến Binh", "FrameWarriorName"),
    frame("frame_veteran", "Bách Chiến", "FrameVeteranName"),
    frame("frame_diamond", "Kim Cương", "FrameDiamondName"),
    frame("frame_challenger", "Thách Đấu", "FrameChallengerName"),
    frame("frame_glory", "Vinh Quang", "FrameGloryName"),
    frame("frame_unyielding", "Bất Khuất", "FrameUnyieldingName"),
    frame("frame_legend", "Huyền Thoại", "FrameLegendName"),
    frame("frame_emperor", "Đế Vương", "FrameEmperorName"),
    frame("frame_speed_shadow", "Tốc Ảnh", "FrameSpeedShadowName"),
    frame("frame_champion", "Quán Quân", "FrameChampionName"),
    frame("frame_immortal", "Bất Diệt", "FrameImmortalName"),
    frame("frame_dragon_might", "Long Uy", "FrameDragonMightName"),
    frame("frame_supreme", "Chí Tôn", "FrameSupremeName"),
    frame("frame_peerless", "Vô Song", "FramePeerlessName")
)

val TITLE_CATALOG = listOf(
    title("title_first_battle", "Khai Chiến", "TitleFirstBattleName"),
    title("title_godspeed", "Thần Tốc", "TitleGodspeedName"),
    title("title_undefeated", "Bất Bại", "TitleUndefeatedName"),
    title("title_veteran", "Bách Chiến", "TitleVeteranName"),
    title("title_divine_eye", "Mắt Thần", "TitleDivineEyeName"),
    title("title_pillar", "Trụ Cột", "TitlePillarName"),
    title("title_one_strike", "Nhất Kích", "TitleOneStrikeName"),
    title("title_golden_reflex", "Phản Xạ Vàng", "TitleGoldenReflexName"),
    title("title_master", "Cao Thủ", "TitleMasterName"),
    title("title_conqueror", "Kẻ Chinh Phục", "TitleConquerorName"),
    title("title_war_god", "Chiến Thần", "TitleWarGodName"),
    title("title_speed_king", "Vua Tốc Độ", "TitleSpeedKingName")
)

private fun frame(id: String, fallbackName: String, nameKey: String) =
    ProgressionCosmeticDefinition(id, fallbackName, nameKey, CosmeticType.FRAME)

private fun title(id: String, fallbackName: String, nameKey: String) =
    ProgressionCosmeticDefinition(id, fallbackName, nameKey, CosmeticType.TITLE)

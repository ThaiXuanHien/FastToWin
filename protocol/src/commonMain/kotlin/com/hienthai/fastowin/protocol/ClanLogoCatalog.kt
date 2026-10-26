package com.hienthai.fastowin.protocol

data class ClanLogoDefinition(val id: String, val requiredLevel: Int)

val CLAN_LOGOS: List<ClanLogoDefinition> = listOf(
    ClanLogoDefinition("shield", 1),
    ClanLogoDefinition("flag", 1),
    ClanLogoDefinition("sword", 3),
    ClanLogoDefinition("wolf", 5),
    ClanLogoDefinition("eagle", 8),
    ClanLogoDefinition("lion", 10),
    ClanLogoDefinition("dragon", 15),
    ClanLogoDefinition("phoenix", 20),
    ClanLogoDefinition("crown", 25),
    ClanLogoDefinition("kirin", 30)
)

fun clanLogoRequiredLevel(id: String): Int? = CLAN_LOGOS.firstOrNull { it.id == id }?.requiredLevel

fun isClanLogoUnlocked(id: String, clanLevel: Int): Boolean =
    clanLogoRequiredLevel(id)?.let { clanLevel >= it } ?: false

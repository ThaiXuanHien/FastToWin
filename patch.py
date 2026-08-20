import re

with open('server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# ProgressionRow
content = re.sub(
    r'private data class ProgressionRow\(\s*val experiencePoints: Int = 0,\s*val equippedFrameId',
    r'private data class ProgressionRow(\n    val experiencePoints: Int = 0,\n    val gold: Int = 0,\n    val gems: Int = 0,\n    val equippedFrameId',
    content
)
content = re.sub(
    r'val equippedTitleId: String = "title_rookie",\s*val currentDailyCheckInStreak',
    r'val equippedTitleId: String = "title_rookie",\n    val equippedCardBackId: String = "card_back_default",\n    val equippedBoardSkinId: String = "board_skin_default",\n    val currentDailyCheckInStreak',
    content
)

# SQL Query
content = re.sub(
    r'SELECT COALESCE\(experience_points, 0\) AS experience_points,\s*COALESCE\(equipped_frame_id',
    r'SELECT COALESCE(experience_points, 0) AS experience_points,\n                       COALESCE(gold, 0) AS gold,\n                       COALESCE(gems, 0) AS gems,\n                       COALESCE(equipped_frame_id',
    content
)
content = re.sub(
    r'COALESCE\(equipped_title_id, \'title_rookie\'\) AS equipped_title_id,\s*COALESCE\(current_daily_check_in_streak',
    r'COALESCE(equipped_title_id, \'title_rookie\') AS equipped_title_id,\n                       COALESCE(equipped_card_back_id, \'card_back_default\') AS equipped_card_back_id,\n                       COALESCE(equipped_board_skin_id, \'board_skin_default\') AS equipped_board_skin_id,\n                       COALESCE(current_daily_check_in_streak',
    content
)

# SQL Mapping
content = re.sub(
    r'ProgressionRow\(\s*experiencePoints = result.getInt\("experience_points"\),\s*equippedFrameId',
    r'ProgressionRow(\n                            experiencePoints = result.getInt("experience_points"),\n                            gold = result.getInt("gold"),\n                            gems = result.getInt("gems"),\n                            equippedFrameId',
    content
)
content = re.sub(
    r'equippedTitleId = result.getString\("equipped_title_id"\),\s*currentDailyCheckInStreak',
    r'equippedTitleId = result.getString("equipped_title_id"),\n                            equippedCardBackId = result.getString("equipped_card_back_id"),\n                            equippedBoardSkinId = result.getString("equipped_board_skin_id"),\n                            currentDailyCheckInStreak',
    content
)

# ownedCosmetics
owned_cosmetics = '''            }.toMap()

            val ownedCosmetics = connection.prepareStatement(
                """
                SELECT cosmetic_id FROM player_cosmetics WHERE user_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    buildSet {
                        while (result.next()) add(result.getString("cosmetic_id"))
                    }
                }
            }'''
content = content.replace('            }.toMap()', owned_cosmetics)

# cosmetic mapping
cosmetic_replace = '''                )
            ) + com.hienthai.fastowin.protocol.SHOP_ITEMS.map { item ->
                cosmetic(
                    id = item.id,
                    name = item.name,
                    type = item.type,
                    unlocked = item.id in ownedCosmetics,
                    equippedId = when (item.type) {
                        com.hienthai.fastowin.protocol.CosmeticType.CARD_BACK -> progressionRow.equippedCardBackId
                        com.hienthai.fastowin.protocol.CosmeticType.BOARD_SKIN -> progressionRow.equippedBoardSkinId
                        else -> null
                    }
                )
            }
            base.copy(
                recentMatches = recentMatches,
                achievements = achievements,
                modeStatistics = modeStatistics,
                progression = com.hienthai.fastowin.protocol.PlayerProgressionSnapshot(
                    level = level,
                    experiencePoints = experiencePoints,
                    gold = progressionRow.gold,
                    gems = progressionRow.gems,'''
content = re.sub(
    r'                \)\n            \)\n            base\.copy\(\n                recentMatches = recentMatches,\n                achievements = achievements,\n                modeStatistics = modeStatistics,\n                progression = PlayerProgressionSnapshot\(\n                    level = level,\n                    experiencePoints = experiencePoints,',
    cosmetic_replace,
    content
)

# Implementation of missing methods
impl = '''
    override suspend fun updateGold(playerId: String, amountDelta: Int): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE player_stats SET gold = GREATEST(0, gold + ?) WHERE user_id = ?"
            ).use { statement ->
                statement.setInt(1, amountDelta)
                statement.setObject(2, java.util.UUID.fromString(playerId))
                statement.executeUpdate() > 0
            }
        }
    }

    override suspend fun buyCosmetic(playerId: String, cosmeticId: String, cosmeticType: String, price: Int): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                // Deduct gold
                val updateGold = connection.prepareStatement("UPDATE player_stats SET gold = gold - ? WHERE user_id = ? AND gold >= ?")
                updateGold.setInt(1, price)
                updateGold.setObject(2, java.util.UUID.fromString(playerId))
                updateGold.setInt(3, price)
                if (updateGold.executeUpdate() == 0) {
                    connection.rollback()
                    return@withContext false // Not enough gold or user not found
                }

                // Add cosmetic
                val addCosmetic = connection.prepareStatement("INSERT INTO player_cosmetics (user_id, cosmetic_id, cosmetic_type, acquired_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING")
                addCosmetic.setObject(1, java.util.UUID.fromString(playerId))
                addCosmetic.setString(2, cosmeticId)
                addCosmetic.setString(3, cosmeticType)
                addCosmetic.setTimestamp(4, java.sql.Timestamp(System.currentTimeMillis()))
                if (addCosmetic.executeUpdate() == 0) {
                    connection.rollback()
                    return@withContext false // Already owned
                }
                
                connection.commit()
                true
            } catch (e: Exception) {
                connection.rollback()
                false
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun equipCosmetic(playerId: String, cosmeticId: String, cosmeticType: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val column = when (cosmeticType) {
                "CARD_BACK" -> "equipped_card_back_id"
                "BOARD_SKIN" -> "equipped_board_skin_id"
                "FRAME" -> "equipped_frame_id"
                "TITLE" -> "equipped_title_id"
                else -> return@withContext false
            }
            connection.prepareStatement("UPDATE player_stats SET \ = ? WHERE user_id = ?").use { statement ->
                statement.setString(1, cosmeticId)
                statement.setObject(2, java.util.UUID.fromString(playerId))
                statement.executeUpdate() > 0
            }
        }
    }
}
'''
content = re.sub(r'\}\s*$', impl, content)

with open('server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt', 'w', encoding='utf-8') as f:
    f.write(content)

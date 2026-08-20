$OutputEncoding = [System.Text.Encoding]::UTF8
$content = [System.IO.File]::ReadAllText('server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt', [System.Text.Encoding]::UTF8)

# ProgressionRow
$content = $content -replace 'private data class ProgressionRow\(\s*val experiencePoints: Int = 0,\s*val equippedFrameId', 'private data class ProgressionRow(
    val experiencePoints: Int = 0,
    val gold: Int = 0,
    val gems: Int = 0,
    val equippedFrameId'
$content = $content -replace 'val equippedTitleId: String = "title_rookie",\s*val currentDailyCheckInStreak', 'val equippedTitleId: String = "title_rookie",
    val equippedCardBackId: String = "card_back_default",
    val equippedBoardSkinId: String = "board_skin_default",
    val currentDailyCheckInStreak'

# SQL Query
$content = $content -replace 'SELECT COALESCE\(experience_points, 0\) AS experience_points,\s*COALESCE\(equipped_frame_id', 'SELECT COALESCE(experience_points, 0) AS experience_points,
                       COALESCE(gold, 0) AS gold,
                       COALESCE(gems, 0) AS gems,
                       COALESCE(equipped_frame_id'
$content = $content -replace 'COALESCE\(equipped_title_id, ''title_rookie''\) AS equipped_title_id,\s*COALESCE\(current_daily_check_in_streak', 'COALESCE(equipped_title_id, ''title_rookie'') AS equipped_title_id,
                       COALESCE(equipped_card_back_id, ''card_back_default'') AS equipped_card_back_id,
                       COALESCE(equipped_board_skin_id, ''board_skin_default'') AS equipped_board_skin_id,
                       COALESCE(current_daily_check_in_streak'

# SQL Mapping
$content = $content -replace 'ProgressionRow\(\s*experiencePoints = result\.getInt\("experience_points"\),\s*equippedFrameId', 'ProgressionRow(
                            experiencePoints = result.getInt("experience_points"),
                            gold = result.getInt("gold"),
                            gems = result.getInt("gems"),
                            equippedFrameId'
$content = $content -replace 'equippedTitleId = result\.getString\("equipped_title_id"\),\s*currentDailyCheckInStreak', 'equippedTitleId = result.getString("equipped_title_id"),
                            equippedCardBackId = result.getString("equipped_card_back_id"),
                            equippedBoardSkinId = result.getString("equipped_board_skin_id"),
                            currentDailyCheckInStreak'

# ownedCosmetics
$owned_cosmetics = "            }.toMap()

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
            }"
$content = $content.Replace('            }.toMap()', $owned_cosmetics)

# cosmetic mapping
$cosmetic_replace = "                )
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
                    gems = progressionRow.gems,"

$content = $content -replace '                \)\r?\n            \)\r?\n            base\.copy\(\r?\n                recentMatches = recentMatches,\r?\n                achievements = achievements,\r?\n                modeStatistics = modeStatistics,\r?\n                progression = PlayerProgressionSnapshot\(\r?\n                    level = level,\r?\n                    experiencePoints = experiencePoints,', $cosmetic_replace

# Implementation of missing methods
$impl = "
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
                val updateGold = connection.prepareStatement("UPDATE player_stats SET gold = gold - ? WHERE user_id = ? AND gold >= ?")
                updateGold.setInt(1, price)
                updateGold.setObject(2, java.util.UUID.fromString(playerId))
                updateGold.setInt(3, price)
                if (updateGold.executeUpdate() == 0) {
                    connection.rollback()
                    return@withContext false
                }

                val addCosmetic = connection.prepareStatement("INSERT INTO player_cosmetics (user_id, cosmetic_id, cosmetic_type, acquired_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING")
                addCosmetic.setObject(1, java.util.UUID.fromString(playerId))
                addCosmetic.setString(2, cosmeticId)
                addCosmetic.setString(3, cosmeticType)
                addCosmetic.setTimestamp(4, java.sql.Timestamp(System.currentTimeMillis()))
                if (addCosmetic.executeUpdate() == 0) {
                    connection.rollback()
                    return@withContext false
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
            connection.prepareStatement("UPDATE player_stats SET $column = ? WHERE user_id = ?").use { statement ->
                statement.setString(1, cosmeticId)
                statement.setObject(2, java.util.UUID.fromString(playerId))
                statement.executeUpdate() > 0
            }
        }
    }
}
"
$content = $content -replace '\}\s*$', $impl

[System.IO.File]::WriteAllText('server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt', $content, [System.Text.Encoding]::UTF8)

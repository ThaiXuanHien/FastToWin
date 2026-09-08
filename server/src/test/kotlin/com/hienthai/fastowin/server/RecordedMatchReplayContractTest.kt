package com.hienthai.fastowin.server

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordedMatchReplayContractTest {
    @Test
    fun `recorded match replay UI symbols are removed while event contracts remain`() {
        val profileScreen = Path("shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ProfileScreen.kt").readText()
        val textKeys = Path("protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt").readText()
        val catalog = Path("protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/ProfileCatalogTexts.kt").readText()
        val protocol = Path("protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt").readText()
        val repository = Path("server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt").readText()
        val migration = Path("server/src/main/resources/db/migration/V3__create_match_events.sql").readText()

        listOf("MatchReplayControls", "NoReplayData", "ReplayTurn", "CorrectSelection", "WrongSelectionNeed", "StopReplay").forEach { symbol ->
            assertFalse(profileScreen.contains(symbol), "replay symbol remains in ProfileScreen: $symbol")
            assertFalse(textKeys.contains(symbol), "replay key remains in TextKey: $symbol")
            assertFalse(catalog.contains(symbol), "replay catalog value remains: $symbol")
        }
        assertTrue(protocol.contains("data class MatchEventSnapshot"))
        assertTrue(protocol.contains("val events: List<MatchEventSnapshot>"))
        assertTrue(protocol.contains("GetMatchDetail"))
        assertTrue(protocol.contains("MatchDetailData"))
        assertTrue(repository.contains("FROM match_events"))
        assertTrue(migration.contains("CREATE TABLE match_events"))
    }
}

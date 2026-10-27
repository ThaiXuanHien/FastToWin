package com.hienthai.fastowin.localization

import com.hienthai.fastowin.protocol.MissionAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissionActionLocalizationTest {
    @Test fun `dynamic target renders in every supported language`() {
        for(language in AppLanguage.entries) for(action in MissionAction.entries) {
            val key=TextKey.valueOf("MissionAction${action.name}")
            val rendered=LocalizationService(language).text(key,mapOf("target" to "7","accuracy" to "93"))
            assertTrue(rendered.contains("7"),"$language $action must render configured target")
        }
        assertEquals("Play 7 matches",LocalizationService(AppLanguage.ENGLISH).text(TextKey.valueOf("MissionActionPLAY"),mapOf("target" to "7")))
    }
}

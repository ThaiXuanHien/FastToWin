package com.hienthai.fastowin.server.missions

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import kotlin.test.Test
import kotlin.test.assertEquals

class MissionCatalogPostgresTest {
    @Test fun `migration persists eighteen personal definitions without changing legacy economics`() {
        val url = System.getenv("TEST_DATABASE_URL") ?: return
        HikariDataSource(HikariConfig().apply {
            jdbcUrl=url; username=System.getenv("TEST_DATABASE_USER"); password=System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize=2
        }).use { source ->
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate()
            source.connection.use { c ->
                val count=c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name='mission_revisions'").use { it.next(); it.getInt(1) } }
                assertEquals(1,count,"Published catalog must have durable revision storage")
                c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM mission_revisions WHERE legacy_code IS NOT NULL").use { it.next(); assertEquals(18,it.getInt(1)) } }
                c.createStatement().use { s -> s.executeQuery("SELECT target,reward_xp,reward_gold,reward_gems FROM mission_revisions WHERE legacy_code='DAILY_PLAY_1'").use { it.next(); assertEquals(listOf(1,10,50,0), (1..4).map(it::getInt)) } }
            }
        }
    }
}

package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import com.zaxxer.hikari.*
import org.flywaydb.core.Flyway
import java.time.Instant
import java.util.UUID
import kotlin.test.assertIs

internal class RaceFixture(val source:HikariDataSource) {
    var now=Instant.parse("2026-10-24T06:00:00Z").toEpochMilli()
    val auth=AuthenticationService(PostgresAuthRepository(source),PasswordHasher(iterations=1000),{now})
    val admins=AdminService(PostgresAdminRepository(source),AdminConfiguration("https://admin.example",false),PasswordHasher(iterations=1000),{now})
    lateinit var login:AdminLogin
    lateinit var actor:AdminIdentity
    suspend fun setup() { actor=admins.bootstrapOwner("race-owner@example.com","GoodPassword123!");login=admins.login(actor.email,"GoodPassword123!","https://admin.example","127.0.0.1") }
    suspend fun player(name:String="Player")=assertIs<AuthResult.Success>(auth.register("${UUID.randomUUID()}@example.com","GoodPassword123!",name,"android")).session
    fun execute(sql:String) {
        source.connection.use { connection ->
            connection.autoCommit=false
            try {
                JdbcClanRaceStore(connection).apply { lock();setTime(now) }
                connection.createStatement().use { it.execute(sql) }
                connection.commit()
            } catch(error:Throwable) { connection.rollback();throw error }
        }
    }
    fun scalar(sql:String):String=source.connection.use { it.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next());r.getString(1) } } }
    fun hasTable(table:String)=scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name='$table'")=="1"
    fun clan(owner:String,name:String="Clan ${UUID.randomUUID().toString().take(8)}"):String {
        val id=UUID.randomUUID().toString()
        execute("INSERT INTO clans(id,name,owner_id,created_at) VALUES ('$id','$name','$owner',CURRENT_TIMESTAMP)")
        execute("INSERT INTO clan_members(clan_id,user_id,role,joined_at) VALUES ('$id','$owner','LEADER',CURRENT_TIMESTAMP)")
        return id
    }
    fun advance(text:String) { now=Instant.parse(text).toEpochMilli() }
}
internal suspend fun raceDatabase(block:suspend RaceFixture.()->Unit) {
    val url=requireNotNull(System.getenv("TEST_DATABASE_URL")) { "Real PostgreSQL is required for clan race coverage." }
    val schema="race_fixture_"+UUID.randomUUID().toString().replace("-","")
    fun source(value:String)=HikariDataSource(HikariConfig().apply { jdbcUrl=value;username=System.getenv("TEST_DATABASE_USER");password=System.getenv("TEST_DATABASE_PASSWORD");maximumPoolSize=6;addDataSourceProperty("ApplicationName",schema) })
    source(url).use { owner ->
        owner.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
        try {
            source(url.substringBefore('?')+"?currentSchema=$schema").use { child ->
                Flyway.configure().dataSource(child).locations("classpath:db/migration").load().migrate()
                RaceFixture(child).apply { setup() }.block()
            }
        } finally { owner.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
    }
}

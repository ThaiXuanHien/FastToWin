package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.PageRequest
import kotlinx.coroutines.test.runTest
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.*

class ClanRaceReadBoundsPostgresTest {
    @Test fun `current and paged history admin reads bound actual queries and returned rows across fifty two settlements`()=runTest {
        raceDatabase {
            val repository=PostgresClanRaceRepository(source)
            val first=LocalDate.parse("2026-10-26")
            val config=ClanRaceConfig("0.01","1",listOf(ClanRaceReward(30),ClanRaceReward(20),ClanRaceReward(10)))
            repository.transaction {
                repeat(53) { publish(ClanRaceAdminView(first.plusWeeks(it.toLong()).toString(),2,ClanRaceConfigState.PUBLISHED,config,2)) }
            }
            now=raceWeekMillis(first.plusWeeks(52))+3600000
            assertEquals(52,ClanRaceSettlement(repository,{now}).settleDueWeeks(100))
            login=admins.login(actor.email,"GoodPassword123!","https://admin.example","127.0.0.1")
            val context=ClanRaceAdminContext(login.token,login.response.csrfToken,"https://admin.example")
            val counter=CountingRaceDataSource(source)
            val service=ClanRaceService(PostgresClanRaceRepository(counter),admins,{now})
            val counts=linkedMapOf<String,Pair<Int,Int>>()
            suspend fun measured(name:String,block:suspend ()->Unit) {
                counter.reset();block();counts[name]=counter.queries.get() to counter.rows.get()
            }
            measured("current") {
                val current=service.current("reader")
                assertEquals("2027-10-25",current.weekStart);assertEquals(ClanRaceState.ACTIVE,current.state)
            }
            measured("history-one") {
                val page=service.history("reader",PageRequest(0,1))
                assertEquals(52,page.total);assertEquals("2027-10-18",page.items.single().weekStart)
                assertEquals(ClanRaceState.SETTLED,page.items.single().state)
                assertEquals("2027-10-18",page.items.single().settlement!!.result!!.weekStart)
            }
            measured("history-last") {
                val page=service.history("reader",PageRequest(51,1))
                assertEquals(52,page.total);assertEquals("2026-10-26",page.items.single().weekStart)
            }
            measured("history-two") { assertEquals(2,service.history("reader",PageRequest(0,2)).items.size) }
            measured("history-empty") { assertTrue(service.history("reader",PageRequest(52,1)).items.isEmpty()) }
            measured("admin-one") {
                val page=service.list(context,PageRequest(1,1))
                assertEquals(53,page.total);assertEquals("2027-10-18",page.items.single().weekStart)
                assertNotNull(page.items.single().settlement?.result)
            }
            measured("admin-detail") { assertEquals("2026-10-26",service.detail(context,"2026-10-26").settlement!!.result!!.weekStart) }
            // Count actual executions and rows read from PostgreSQL, including authorization/gate queries.
            // Bounds permit implementation variation while rejecting catalog scans and off-page projections.
            val limits=mapOf("current" to 4,"history-one" to 7,"history-last" to 7,"history-two" to 10,
                "history-empty" to 3,"admin-one" to 12,"admin-detail" to 11)
            println("Race bounded reads (queries, rows): $counts")
            val exceeded=counts.filter { (name,value) -> value.first>limits.getValue(name) || value.second>limits.getValue(name) }
            assertTrue(exceeded.isEmpty(),"Unbounded actual PostgreSQL work: $exceeded; bounds=$limits")
        }
    }
}

/** Delegates every operation to real JDBC; instrumentation lives only in tests. */
private class CountingRaceDataSource(private val delegate:DataSource):DataSource by delegate {
    val queries=AtomicInteger();val rows=AtomicInteger()
    fun reset() { queries.set(0);rows.set(0) }
    override fun getConnection():Connection=connection(delegate.connection)
    override fun getConnection(username:String?,password:String?):Connection=connection(delegate.getConnection(username,password))
    private fun connection(real:Connection):Connection=wrap(Connection::class.java,real,transform={ result ->
        when(result) {
            is PreparedStatement -> statement(PreparedStatement::class.java,result)
            is Statement -> statement(Statement::class.java,result)
            else -> result
        }
    })
    private fun <T:Statement> statement(type:Class<T>,real:T):T=wrap(type,real,{ result ->
        if(result is ResultSet) wrap(ResultSet::class.java,result,{it}, { method,value ->
            if(method=="next" && value==true) rows.incrementAndGet()
        }) else result
    }, { method,_ -> if(method in setOf("execute","executeQuery","executeUpdate","executeBatch")) queries.incrementAndGet() })
    @Suppress("UNCHECKED_CAST")
    private fun <T> wrap(type:Class<T>,real:T,transform:(Any?)->Any?={it},after:(String,Any?)->Unit={_,_->}):T =
        Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _,method,args ->
            val result=try { method.invoke(real,*(args?:emptyArray())) } catch(error:InvocationTargetException) { throw error.targetException }
            after(method.name,result);transform(result)
        } as T
}

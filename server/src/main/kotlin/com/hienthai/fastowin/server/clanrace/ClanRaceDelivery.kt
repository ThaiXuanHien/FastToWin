package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.server.gifts.*
import io.ktor.server.application.Application
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.sql.Timestamp

class ClanRaceDelivery(private val repository:PostgresClanRaceRepository,private val nowMillis:()->Long=System::currentTimeMillis) {
    suspend fun deliverDueBatch(limit:Int=100):Int {
        require(limit in 1..1000)
        val ids=repository.transaction {
            // Waiting guests keep their rights but do not monopolize the delivery batch before registration.
            rows("""SELECT o.entitlement_id FROM clan_race_outbox o
                JOIN clan_race_entitlements e ON e.id=o.entitlement_id LEFT JOIN users u ON u.id=e.user_id
                WHERE o.state='PENDING' AND (o.last_error_code IS DISTINCT FROM 'REGISTRATION_REQUIRED'
                    OR u.account_type='REGISTERED' OR u.id IS NULL OR u.status='DELETED')
                ORDER BY o.entitlement_id LIMIT ?""",listOf(limit)) { it.getString(1) }
        }
        var processed=0
        for(id in ids) {
            try {
                if(repository.transaction { deliver(id,nowMillis) }) processed++
            } catch(error:CancellationException) { throw error }
            catch(_:Exception) {
                repository.transaction {
                    update("UPDATE clan_race_outbox SET attempts=attempts+1,last_error_code='DELIVERY_FAILED' WHERE entitlement_id=? AND state='PENDING'",uuid(id))
                }
            }
        }
        return processed
    }
}

private fun JdbcClanRaceStore.deliver(id:String,nowMillis:()->Long):Boolean {
    val entitlement=rows("""SELECT e.payload FROM clan_race_entitlements e JOIN clan_race_outbox o ON o.entitlement_id=e.id
        WHERE e.id=? AND o.state='PENDING' FOR UPDATE OF o""",listOf(uuid(id))) {
        Json.decodeFromString<ClanRaceEntitlement>(it.getString(1))
    }.singleOrNull()?:return false
    val account=rows("SELECT account_type,status FROM users WHERE id=? FOR KEY SHARE",listOf(uuid(entitlement.userId))) {
        it.getString(1) to it.getString(2)
    }.singleOrNull()
    // Availability starts after all account/entitlement lock waits, never at batch selection time.
    val now=nowMillis()
    if(account==null || account.second=="DELETED") {
        update("UPDATE clan_race_outbox SET state='ACCOUNT_UNAVAILABLE',attempts=attempts+1,last_error_code='ACCOUNT_UNAVAILABLE',processed_at=? WHERE entitlement_id=?",Timestamp(now),uuid(id))
        return true
    }
    if(account.first!="REGISTERED") {
        update("UPDATE clan_race_outbox SET attempts=attempts+1,last_error_code='REGISTRATION_REQUIRED',processed_at=? WHERE entitlement_id=?",Timestamp(now),uuid(id))
        return false
    }
    val gifts=JdbcGiftTransaction(c)
    val gift=entitlement.gift(now)
    gifts.putCampaign(gift)
    gifts.freeze(id,listOf(entitlement.userId))
    gifts.putRecipient(GiftRecipient(id,entitlement.userId,"DELIVERED"))
    update("""UPDATE clan_race_outbox SET state='DELIVERED',attempts=attempts+1,last_error_code=NULL,
        processed_at=?,available_at=?,expires_at=? WHERE entitlement_id=?""",Timestamp(now),Timestamp(now),Timestamp(gift.draft.expiresAtEpochMillis!!),uuid(id))
    return true
}

internal fun Application.startClanRaceDelivery(settlement:ClanRaceSettlement,delivery:ClanRaceDelivery)=launch {
    while(isActive) {
        try { settlement.settleDueWeeks();delivery.deliverDueBatch() }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(_:Exception) { environment.log.warn("Clan race settlement/delivery failed; durable checkpoints will be retried.") }
        delay(5000)
    }
}

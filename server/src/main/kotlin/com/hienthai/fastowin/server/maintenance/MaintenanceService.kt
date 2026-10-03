package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.hashToken
import com.hienthai.fastowin.server.legacyFallback
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.SQLException
import java.util.UUID

/** Persistence only. Runtime must refresh at most every 5 seconds and gate admission itself. */
class MaintenanceService(
    val repository:MaintenanceRepository,
    private val admins:AdminService?=null,
    private val nowMillis:()->Long=System::currentTimeMillis,
    private val environmentOverride:Boolean=false,
    private val environmentMessageVi:String?=null,
    private val environmentMessageEn:String?=null
) {
    private data class Observed(val policy:MaintenancePolicy=MaintenancePolicy(),val runtime:MaintenanceRuntimeObservation=MaintenanceRuntimeObservation())
    @Volatile private var observed=Observed()
    private val refreshMutex=Mutex()
    fun requireOrigin(origin:String?) { (admins?:unavailable()).requireOrigin(origin) }
    private fun MaintenanceTransaction.authorize(context:MaintenanceAdminContext,mutation:Boolean=false):AdminIdentity {
        val service=admins?:unavailable()
        context.origin?.let(service::requireOrigin)
        if (mutation) service.requireOrigin(context.origin)
        return service.authorize(admin,context.token,context.csrfToken,mutation,ownerOnly=mutation)
    }
    private fun MaintenanceTransaction.audit(actor:String?,action:String,before:MaintenanceSnapshot?=null,after:MaintenanceSnapshot?=null,
        request:MaintenanceUpdateRequest?=null) {
        fun summary(value:MaintenanceSnapshot?)=value?.let { "state=${it.state};mode=${it.mode};version=${it.version}" }
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(),actor,action,null,request?.requestId,request?.reason,
            summary(before),summary(after),nowMillis()))
    }
    private fun response(snapshot:MaintenanceSnapshot):MaintenanceAdminResponse {
        val observation=observed
        return MaintenanceAdminResponse(snapshot,effective(observation.policy),observation.runtime)
    }
    suspend fun read(context:MaintenanceAdminContext):MaintenanceAdminResponse=storage {
        repository.transaction {
            val actor=authorize(context); val value=snapshot()
            audit(actor.id,"MAINTENANCE_READ"); response(value)
        }
    }
    suspend fun update(context:MaintenanceAdminContext,request:MaintenanceUpdateRequest):MaintenanceAdminResponse=storage {
        repository.transaction {
            val actor=authorize(context,true)
            validate(request)
            // Current role, session and override checks precede replay.
            if (environmentOverride && !request.enabled) conflict()
            val fingerprint=hashToken(Json.encodeToString(request))
            request(actor.id,request.requestId)?.let {
                if (it.fingerprint!=fingerprint) conflict()
                return@transaction it.result
            }
            val before=snapshot()
            if (before.version!=request.expectedVersion) conflict()
            val now=validNow()
            val version=try { Math.addExact(before.version,1) } catch (_:ArithmeticException) { conflict() }
            val next=if (!request.enabled) {
                MaintenanceSnapshot(version=version,reason=request.reason,actorId=actor.id,changedAtEpochMillis=now)
            } else {
                val editing=before.state!=MaintenanceState.ACTIVE
                if (editing && before.mode==MaintenanceMode.EMERGENCY && request.mode!=MaintenanceMode.EMERGENCY) {
                    throw AdminException(409,"MODE_LOCKED",legacyFallback("Yêu cầu xung đột với dữ liệu hiện tại."))
                }
                if (editing && (before.warningMinutes!=request.warningMinutes || before.drainMinutes!=request.drainMinutes)) conflict()
                val announcingAt=if(editing) before.transitionAtEpochMillis else addMinutes(now,request.warningMinutes)
                val drainDeadline=if(editing) before.drainDeadlineEpochMillis else addMinutes(announcingAt!!,request.drainMinutes)
                val state=when {
                    request.mode==MaintenanceMode.EMERGENCY -> MaintenanceState.MAINTENANCE
                    editing -> before.state
                    request.warningMinutes==0 -> MaintenanceState.DRAINING
                    else -> MaintenanceState.ANNOUNCING
                }
                MaintenanceSnapshot(version,state,request.mode,request.cause,request.messageVi,request.messageEn,request.reason,
                    actor.id,now,if(editing) before.startedAtEpochMillis else now,
                    when(state) { MaintenanceState.ANNOUNCING -> announcingAt; MaintenanceState.DRAINING -> drainDeadline; else -> null },
                    if(state==MaintenanceState.MAINTENANCE) null else drainDeadline,request.expectedEndEpochMillis,request.warningMinutes,request.drainMinutes)
            }
            putSnapshot(before.version,next); audit(actor.id,"MAINTENANCE_UPDATE",before,next,request)
            val result=response(next)
            putRequest(actor.id,request.requestId,MaintenanceRequestRecord(fingerprint,result)); result
        }
    }
    /** Caller must apply drain/admission blocking before sampling a trusted active-match count.
     * Publishing happens only after commit. Observation is not confirmation of socket closure.
     */
    suspend fun refresh(activeMatches:Int):MaintenancePolicy=refreshMutex.withLock {
        if (activeMatches<0) invalid()
        storage {
            val snapshot=repository.transaction {
                var value=snapshot(); val now=validNow()
                fun advance(state:MaintenanceState) {
                    val next=value.copy(version=try { Math.addExact(value.version,1) } catch (_:ArithmeticException) { conflict() },
                        state=state,changedAtEpochMillis=now,
                        transitionAtEpochMillis=if(state==MaintenanceState.DRAINING) value.drainDeadlineEpochMillis else null)
                    putSnapshot(value.version,next); audit(null,"MAINTENANCE_TRANSITION",value,next); value=next
                }
                if (value.state==MaintenanceState.ANNOUNCING && now>=value.transitionAtEpochMillis!!) advance(MaintenanceState.DRAINING)
                if (value.state==MaintenanceState.DRAINING && (activeMatches==0 || now>=value.drainDeadlineEpochMillis!!)) advance(MaintenanceState.MAINTENANCE)
                value
            }
            observed=Observed(snapshot.policy(),MaintenanceRuntimeObservation(snapshot.version,validNow(),activeMatches,true))
            effectivePolicy()
        }
    }
    fun effectivePolicy():MaintenancePolicy=effective(observed.policy)
    private fun effective(policy:MaintenancePolicy):MaintenancePolicy=if(environmentOverride) {
        MaintenancePolicy(MaintenanceState.MAINTENANCE,MaintenanceMode.EMERGENCY,policy.version,
            environmentMessageVi,environmentMessageEn,source=MaintenanceSource.ENVIRONMENT)
    } else policy
    fun publicStatus():ServiceStatusResponse {
        val policy=effectivePolicy()
        if (!policy.available) unavailable()
        return ServiceStatusResponse(maintenance=policy.isFullyClosed,message=policy.messageVi?:policy.messageEn,
            pollAfterSeconds=if(policy.state==MaintenanceState.ACTIVE) 30 else 5,
            state=policy.state,mode=policy.mode,version=policy.version,messageVi=policy.messageVi,messageEn=policy.messageEn,
            transitionAtEpochMillis=policy.transitionAtEpochMillis,expectedEndEpochMillis=policy.expectedEndEpochMillis)
    }
    private fun MaintenanceSnapshot.policy()=MaintenancePolicy(state,mode,version,messageVi,messageEn,transitionAtEpochMillis,expectedEndEpochMillis)
    private suspend fun <T> storage(block:suspend ()->T):T=try { block() }
        catch (error:AdminException) { if(error.status==503) markUnavailable(); throw error }
        catch (_:SQLException) { markUnavailable(); unavailable() }
    private fun markUnavailable() {
        val old=observed
        observed=old.copy(policy=old.policy.copy(available=false,source=MaintenanceSource.UNAVAILABLE),runtime=old.runtime.copy(available=false))
    }
    private fun validNow():Long=nowMillis().also { if(it !in 0..MAX_TIMESTAMP) invalid() }
    private fun addMinutes(timestamp:Long,minutes:Int):Long=try {
        Math.addExact(timestamp,Math.multiplyExact(minutes.toLong(),60_000L)).also { if(it !in 0..MAX_TIMESTAMP) invalid() }
    } catch (_:ArithmeticException) { invalid() }
    private fun validate(request:MaintenanceUpdateRequest) {
        if(request.expectedVersion<0 || request.reason.isBlank() || request.reason.length>500 || request.reason.any(Char::isISOControl) ||
            !request.requestId.matches(Regex("[A-Za-z0-9_-]{1,128}")) || request.warningMinutes !in 0..60 || request.drainMinutes !in 1..60 ||
            request.expectedEndEpochMillis?.let { it !in 0..MAX_TIMESTAMP }==true) invalid()
        fun validMessage(value:String?)=value!=null && value.isNotBlank() && value.length<=1000 && value.none { it.isISOControl() && it!='\n' && it!='\r' && it!='\t' }
        if(request.enabled && (request.mode==null || request.cause !in CAUSES || !validMessage(request.messageVi) || !validMessage(request.messageEn))) invalid()
        // Bound optional fields even on disabling so replay cannot store arbitrary blobs.
        if(request.cause!=null && request.cause !in CAUSES || request.messageVi!=null && !validMessage(request.messageVi) ||
            request.messageEn!=null && !validMessage(request.messageEn)) invalid()
    }
    companion object {
        const val MAX_TIMESTAMP=253402300799999L
        private val CAUSES=setOf("UPDATE","INCIDENT","SECURITY","OVERLOAD","OTHER")
    }
}

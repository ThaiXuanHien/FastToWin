package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import kotlinx.serialization.Serializable

data class MaintenanceAdminContext(val token:String?,val csrfToken:String?=null,val origin:String?=null)
@Serializable data class MaintenanceUpdateRequest(
    val expectedVersion:Long,val requestId:String,val reason:String,val mode:MaintenanceMode?=null,
    val cause:String?=null,val messageVi:String?=null,val messageEn:String?=null,
    val warningMinutes:Int=5,val drainMinutes:Int=15,val expectedEndEpochMillis:Long?=null,val enabled:Boolean
)
@Serializable data class MaintenanceSnapshot(
    val version:Long=0,val state:MaintenanceState=MaintenanceState.ACTIVE,val mode:MaintenanceMode?=null,
    val cause:String?=null,val messageVi:String?=null,val messageEn:String?=null,val reason:String?=null,
    val actorId:String?=null,val changedAtEpochMillis:Long?=null,val startedAtEpochMillis:Long?=null,
    val transitionAtEpochMillis:Long?=null,val drainDeadlineEpochMillis:Long?=null,
    val expectedEndEpochMillis:Long?=null,val warningMinutes:Int=5,val drainMinutes:Int=15
)
@Serializable enum class MaintenanceSource { DATABASE, ENVIRONMENT, UNAVAILABLE }
@Serializable data class MaintenancePolicy(
    val state:MaintenanceState?=null,val mode:MaintenanceMode?=null,val version:Long?=null,
    val messageVi:String?=null,val messageEn:String?=null,val transitionAtEpochMillis:Long?=null,
    val expectedEndEpochMillis:Long?=null,val source:MaintenanceSource=if(state==null) MaintenanceSource.UNAVAILABLE else MaintenanceSource.DATABASE,
    val available:Boolean=state!=null
) {
    val blocksNewIntent:Boolean get()=!available || state==null || state==MaintenanceState.DRAINING || state==MaintenanceState.MAINTENANCE
    val allowsExistingMatch:Boolean get()=state!=null && state!=MaintenanceState.MAINTENANCE
    val isFullyClosed:Boolean get()=state==null || state==MaintenanceState.MAINTENANCE
}
@Serializable data class MaintenanceRuntimeObservation(
    val version:Long?=null,val observedAtEpochMillis:Long?=null,val activeMatches:Int?=null,val available:Boolean=false
)
@Serializable data class MaintenanceAdminResponse(
    val persisted:MaintenanceSnapshot,val effective:MaintenancePolicy,val runtime:MaintenanceRuntimeObservation,
    val persistedSource:MaintenanceSource=MaintenanceSource.DATABASE
)
data class MaintenanceRequestRecord(val fingerprint:String,val result:MaintenanceAdminResponse)

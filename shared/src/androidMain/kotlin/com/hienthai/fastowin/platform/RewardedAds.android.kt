package com.hienthai.fastowin.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.hienthai.fastowin.data.network.RewardedAdSessionGateway
import java.lang.ref.WeakReference
import com.hienthai.fastowin.ui.components.RewardedAdAgeDialog

val LocalAndroidRewardedAdConfiguration = staticCompositionLocalOf { RewardedAdConfiguration() }

@Composable
actual fun rememberRewardedAdGateway(sessionGateway: RewardedAdSessionGateway?): RewardedAdGateway {
    val configuration = LocalAndroidRewardedAdConfiguration.current
    val context = LocalContext.current
    val application = context.applicationContext
    val scope = rememberCoroutineScope()
    val activityReference = remember(context) { WeakReference(context.findRewardedAdActivity()) }
    val currentActivityReference by rememberUpdatedState(activityReference)
    val driver = remember(sessionGateway, configuration, application) {
        if (sessionGateway == null || !configuration.canRequest) null else AndroidRewardedAdDriver(
            application, { currentActivityReference.get() }, sessionGateway.ownerId, configuration, AndroidRewardedAdStorage(application))
    }
    val gateway = remember(sessionGateway, driver, configuration) {
        if (driver != null && sessionGateway != null) {
            val verified = VerifiedRewardedAdGateway(sessionGateway, driver, AndroidRewardedAdStorage(application), scope)
            object : RewardedAdGateway by verified {
                override val privacyOptionsRequired = driver.privacyOptionsRequired
                override fun showPrivacyOptions() {
                    if (!verified.state.value.isLoading) driver.showPrivacyOptions { /* UMP is checked again before the next load. */ }
                }
            }
        } else if (configuration.mode == RewardedAdMode.SIMULATED) DefaultRewardedAdGateway(isWeb = false)
        else {
            val disabled = DefaultRewardedAdGateway(isWeb = false)
            object : RewardedAdGateway by disabled {
                override fun configure(availability: com.hienthai.fastowin.protocol.RewardedAdAvailability) =
                    disabled.configure(com.hienthai.fastowin.protocol.RewardedAdAvailability.UNAVAILABLE)
            }
        }
    }
    DisposableEffect(gateway) { onDispose(gateway::close) }
    LaunchedEffect(driver) { driver?.refreshOnLaunch() }
    if (driver != null) {
        val chooseAge by driver.ageChoiceRequested.collectAsState()
        if (chooseAge) RewardedAdAgeDialog(driver::selectAge)
    }
    return gateway
}

private fun Context.findRewardedAdActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.findRewardedAdActivity()
    else -> null
}

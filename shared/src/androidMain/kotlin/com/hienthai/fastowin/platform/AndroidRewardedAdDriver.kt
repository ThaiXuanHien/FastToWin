package com.hienthai.fastowin.platform

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.*
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.rewarded.*
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.hienthai.fastowin.protocol.RewardedAdSessionView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** No Activity is retained. Each form/show resolves the current resumed Activity through a weak provider. */
internal class AndroidRewardedAdDriver(
    context: Context,
    private val activity: () -> Activity?,
    private val ownerId: String,
    private val configuration: RewardedAdConfiguration,
    private val storage: AndroidRewardedAdStorage
) : RewardedAdDriver {
    private val application = context.applicationContext
    override val adUnitId = configuration.adUnitId
    val ageChoiceRequested = MutableStateFlow(false)
    val privacyOptionsRequired = MutableStateFlow(false)
    private val consent by lazy { UserMessagingPlatform.getConsentInformation(application) }
    private var ageChoice: CompletableDeferred<RewardedAdAgeBand?>? = null
    private var consentUpdated = false
    private val consentMutex = Mutex()
    @Volatile private var closed = false
    private val main = Handler(Looper.getMainLooper())
    private fun onMain(action: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action) }
    private var generation = 0
    private var loaded: RewardedAd? = null
    private var showing: RewardedAd? = null

    fun selectAge(band: RewardedAdAgeBand?) {
        if (closed) return
        if (band != null && band != RewardedAdAgeBand.UNKNOWN) storage.age(ownerId, band)
        ageChoice?.complete(band)
        ageChoiceRequested.value = false
    }
    override suspend fun authorize(): RewardedAdAuthorization {
        if (closed || !configuration.canRequest) return RewardedAdAuthorization.DENIED
        var band = storage.age(ownerId)
        if (band == RewardedAdAgeBand.UNKNOWN) {
            val choice = CompletableDeferred<RewardedAdAgeBand?>()
            ageChoice = choice; ageChoiceRequested.value = true
            try { band = choice.await() ?: return RewardedAdAuthorization.AGE_REQUIRED }
            finally { ageChoice = null; ageChoiceRequested.value = false }
        }
        val policy = band.privacyPolicy()
        if (!policy.mayRequest) return if (band == RewardedAdAgeBand.UNDER_13) RewardedAdAuthorization.AGE_BLOCKED else RewardedAdAuthorization.AGE_REQUIRED
        // Check the actual device with the SDK's own test-device implementation, before initialization or load.
        val requestConfiguration = requestConfiguration(band)
        if (configuration.mode == RewardedAdMode.TEST && !requestConfiguration.isTestDevice(application)) return RewardedAdAuthorization.DENIED
        val privacyReady = consentMutex.withLock {
            if (!consentUpdated) consentUpdated = withTimeoutOrNull(30_000) { updateConsent(band) } == true
            consentUpdated
        }
        if (!privacyReady) return RewardedAdAuthorization.DENIED
        refreshPrivacyOptions()
        if (closed || !consent.canRequestAds()) return RewardedAdAuthorization.DENIED
        withContext(Dispatchers.IO) {
            if (!closed) {
                if (!MobileAds.isInitialized) MobileAds.initialize(application,
                    InitializationConfig.Builder(configuration.appId).setRequestConfiguration(requestConfiguration).build())
                else MobileAds.setRequestConfiguration(requestConfiguration)
            }
        }
        currentCoroutineContext().ensureActive()
        return if (!closed && consent.canRequestAds()) RewardedAdAuthorization.ALLOWED else RewardedAdAuthorization.DENIED
    }
    private fun requestConfiguration(band: RewardedAdAgeBand): RequestConfiguration = RequestConfiguration.Builder()
        .setAgeRestrictedTreatment(when (band.privacyPolicy().ageTreatment) {
            // Google's current targeting migration maps under-age-of-consent TRUE to CHILD.
            RewardedAdAgeTreatment.CHILD -> AgeRestrictedTreatment.CHILD
            RewardedAdAgeTreatment.TEEN -> AgeRestrictedTreatment.TEEN
            RewardedAdAgeTreatment.UNSPECIFIED -> AgeRestrictedTreatment.UNSPECIFIED
        })
        .setMaxAdContentRating(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G)
        .setPublisherPrivacyPersonalizationState(if (band.privacyPolicy().personalized)
            RequestConfiguration.PublisherPrivacyPersonalizationState.DEFAULT else RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED)
        .setTestDeviceIds(if (configuration.mode == RewardedAdMode.TEST) configuration.testDeviceIds else emptyList())
        .build()

    private suspend fun updateConsent(band: RewardedAdAgeBand): Boolean = suspendCancellableCoroutine { continuation ->
        val currentActivity = activity()?.takeUnless { it.isFinishing || it.isDestroyed }
        if (currentActivity == null || closed) { continuation.resume(false); return@suspendCancellableCoroutine }
        val fence = generation
        consent.requestConsentInfoUpdate(currentActivity,
            ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(band.privacyPolicy().underAgeOfConsent).build(),
            {
                refreshPrivacyOptions()
                val activeActivity = activity()?.takeUnless { it.isFinishing || it.isDestroyed }
                if (!continuation.isActive || closed || fence != generation || activeActivity == null) {
                    if (continuation.isActive) continuation.resume(false)
                } else UserMessagingPlatform.loadAndShowConsentFormIfRequired(activeActivity) { formError ->
                    refreshPrivacyOptions()
                    // A completed denial is still a completed consent update: don't reopen it on every retry.
                    if (continuation.isActive) continuation.resume(!closed && fence == generation && formError == null)
                }
            },
            {
                refreshPrivacyOptions()
                // UMP may allow ads based on a valid prior choice even if a refresh fails offline.
                if (continuation.isActive) continuation.resume(!closed && fence == generation && consent.canRequestAds())
            })
    }
    suspend fun refreshOnLaunch() {
        val band = storage.age(ownerId)
        if (!closed && configuration.canRequest && band.privacyPolicy().mayRequest) {
            consentMutex.withLock { consentUpdated = withTimeoutOrNull(30_000) { updateConsent(band) } == true }
            refreshPrivacyOptions()
        }
    }
    fun refreshPrivacyOptions() {
        privacyOptionsRequired.value = !closed && consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }
    fun showPrivacyOptions(onFinished: () -> Unit) {
        val currentActivity = activity()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        if (closed || !privacyOptionsRequired.value) return
        loaded?.destroy(); loaded = null
        UserMessagingPlatform.showPrivacyOptionsForm(currentActivity) {
            if (!closed) { consentUpdated = false; refreshPrivacyOptions(); onFinished() }
        }
    }
    override suspend fun load(): Boolean {
        if (closed || !configuration.canRequest || !consent.canRequestAds()) return false
        if (configuration.mode == RewardedAdMode.TEST && !MobileAds.getRequestConfiguration().isTestDevice(application)) return false
        loaded?.destroy(); loaded = null
        val fence = ++generation
        return withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { continuation ->
                val request = AdRequest.Builder(configuration.adUnitId)
                if (!storage.age(ownerId).privacyPolicy().personalized) request.setGoogleExtrasBundle(Bundle().apply { putString("npa", "1") })
                RewardedAd.load(request.build(), object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) = onMain {
                        if (closed || fence != generation || !continuation.isActive) ad.destroy()
                        else { loaded = ad; continuation.resume(true) }
                    }
                    override fun onAdFailedToLoad(adError: LoadAdError) = onMain { if (continuation.isActive) continuation.resume(false) }
                })
                continuation.invokeOnCancellation { onMain { generation++; loaded?.destroy(); loaded = null } }
            }
        } ?: false
    }
    override suspend fun show(session: RewardedAdSessionView): RewardedAdShowResult {
        val ad = loaded; loaded = null
        val currentActivity = activity()?.takeUnless { it.isFinishing || it.isDestroyed }
        if (closed || ad == null || currentActivity == null || !consent.canRequestAds() || session.adUnitId != configuration.adUnitId) {
            ad?.destroy(); return RewardedAdShowResult.UNAVAILABLE
        }
        showing = ad
        val fence = generation
        return withTimeoutOrNull(300_000) {
            suspendCancellableCoroutine { continuation ->
                var earned = false
                fun finish(result: RewardedAdShowResult) {
                    ad.adEventCallback = null
                    if (showing === ad) showing = null
                    ad.destroy()
                    if (continuation.isActive) continuation.resume(result)
                }
                // Empty optional user ID. Never pass account ID, email or token to Google.
                ad.setServerSideVerificationOptions(ServerSideVerificationOptions("", session.customData))
                ad.adEventCallback = object : RewardedAdEventCallback {
                    override fun onAdDismissedFullScreenContent() = onMain { finish(if (earned) RewardedAdShowResult.EARNED else RewardedAdShowResult.DISMISSED) }
                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) = onMain { finish(RewardedAdShowResult.UNAVAILABLE) }
                }
                continuation.invokeOnCancellation { onMain { ad.adEventCallback = null; ad.destroy(); if (showing === ad) showing = null } }
                ad.show(currentActivity) { onMain { if (!closed && fence == generation && continuation.isActive) earned = true } }
            }
        } ?: RewardedAdShowResult.UNAVAILABLE
    }
    override fun close() {
        closed = true
        cancel()
    }
    override fun cancel() {
        generation++
        ageChoice?.cancel(); ageChoice = null; ageChoiceRequested.value = false
        loaded?.destroy(); loaded = null
        showing?.let { it.adEventCallback = null; it.destroy() }; showing = null
    }
}

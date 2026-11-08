package com.hienthai.fastowin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.hienthai.fastowin.platform.AppPushBridge
import com.hienthai.fastowin.platform.AppPushStatus
import com.hienthai.fastowin.platform.normalizePushToken
import com.google.firebase.messaging.FirebaseMessaging

internal const val FCM_PREFERENCES = "fcm"
internal const val FCM_TOKEN = "fcm_token"
private const val PERMISSION_REQUESTED = "notification_permission_requested"

internal class AndroidPushBridge(private val activity: ComponentActivity) : AppPushBridge {
    private val preferences = activity.getSharedPreferences(FCM_PREFERENCES, Context.MODE_PRIVATE)
    private val notificationManager = NotificationManagerCompat.from(activity)
    private var requesting = false
    private var statusObserver: ((AppPushStatus) -> Unit)? = null
    private val requestPermission = activity.activityResultRegistry.register(
        "fasttowin.notification-permission",
        activity,
        ActivityResultContracts.RequestPermission()
    ) {
        requesting = false
        statusObserver?.invoke(status)
    }

    override val status: AppPushStatus
        get() = when {
            notificationManager.areNotificationsEnabled() -> AppPushStatus.ENABLED
            requesting -> AppPushStatus.REQUESTING
            canRequestPermission() -> AppPushStatus.PROMPT
            else -> AppPushStatus.DENIED
        }

    override fun observe(
        onStatusChanged: (AppPushStatus) -> Unit,
        onTokenChanged: (String) -> Unit
    ): () -> Unit {
        statusObserver = onStatusChanged
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                onStatusChanged(status)
                refreshCurrentToken(onTokenChanged)
            }
        }
        val tokenListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == FCM_TOKEN) preferences.getString(FCM_TOKEN, null)?.let(onTokenChanged)
        }
        preferences.registerOnSharedPreferenceChangeListener(tokenListener)
        activity.lifecycle.addObserver(lifecycleObserver)
        onStatusChanged(status)
        preferences.getString(FCM_TOKEN, null)?.let(onTokenChanged)
        refreshCurrentToken(onTokenChanged)
        return {
            statusObserver = null
            preferences.unregisterOnSharedPreferenceChangeListener(tokenListener)
            activity.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    private fun refreshCurrentToken(onTokenChanged: (String) -> Unit) {
        val tokenTask = runCatching { FirebaseMessaging.getInstance().token }.getOrNull() ?: return
        tokenTask.addOnSuccessListener { token ->
            normalizePushToken(token)?.let { freshToken ->
                preferences.edit().putString(FCM_TOKEN, freshToken).apply()
                onTokenChanged(freshToken)
            }
        }
    }

    override fun enable() {
        if (notificationManager.areNotificationsEnabled() || requesting) return
        if (canRequestPermission()) {
            requesting = true
            preferences.edit().putBoolean(PERMISSION_REQUESTED, true).apply()
            statusObserver?.invoke(status)
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openSystemSettings()
        }
    }

    override fun disable() = openSystemSettings()

    private fun canRequestPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (!preferences.getBoolean(PERMISSION_REQUESTED, false) ||
                activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))

    private fun openSystemSettings() {
        activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
    }
}

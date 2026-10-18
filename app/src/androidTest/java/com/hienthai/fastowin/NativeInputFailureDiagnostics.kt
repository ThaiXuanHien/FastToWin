package com.hienthai.fastowin

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Capture native focus/IME evidence before any Compose query or keyboard cleanup. */
internal fun captureNativeInputFailure(label: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val cache = instrumentation.targetContext.cacheDir
    val failures = mutableListOf<Throwable>()
    runCatching {
        File(cache, "$label-native.png").outputStream().use {
            check(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }.onFailure(failures::add)
    listOf("window" to "dumpsys window", "input-method" to "dumpsys input_method",
        "activity" to "dumpsys activity top").forEach { (name, command) ->
        runCatching {
            val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
            val snapshot = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            File(cache, "$label-$name.txt").writeText(snapshot)
        }.onFailure(failures::add)
    }
    failures.firstOrNull()?.let { first ->
        failures.drop(1).forEach(first::addSuppressed)
        throw first
    }
}
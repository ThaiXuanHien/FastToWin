package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSBundle

@Composable
actual fun rememberAppVersionLabel(): String? = remember {
    val info = NSBundle.mainBundle.infoDictionary
    val version = info?.get("CFBundleShortVersionString") as? String
    val build = info?.get("CFBundleVersion") as? String
    version?.let { if (build != null) "$it ($build)" else it }
}

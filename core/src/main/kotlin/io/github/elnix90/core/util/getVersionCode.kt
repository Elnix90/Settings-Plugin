package io.github.elnix90.core.util

import android.content.Context
import android.os.Build

private fun Context.getVersionCode(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionCode
    }

/**
 * @return the current app version name (e.g. `2.7.0-Glowel`)
 */
private fun Context.getVersionName(): String =
    packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"

/**
 * @return the current app version name and code formatted (e.g. `2.7.0-Glowel (46)`)
 */
public fun Context.getVersionNameAndCode(): String =
    "${getVersionName()} (${getVersionCode()})"
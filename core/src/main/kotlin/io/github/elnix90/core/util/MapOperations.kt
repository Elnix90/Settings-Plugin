package io.github.elnix90.core.util

import android.content.Context
import io.github.elnix90.core.objects.SettingObject

@Suppress("NOTHING_TO_INLINE")
internal suspend inline fun MutableMap<String, Any>.putIfNonDefault(
    ctx: Context,
    settingObject: SettingObject<*, *>
) {
    if (settingObject.isNotNullOrDefault(ctx)) {
        put(settingObject.key, settingObject.getEncoded(ctx) as Any)
    }
}

@Suppress("NOTHING_TO_INLINE")
internal suspend inline fun MutableMap<String, Any>.putIfNotNull(
    ctx: Context,
    settingObject: SettingObject<*, *>
) {
    val value = settingObject.get(ctx)

    if (value != null) {
        put(settingObject.key, settingObject.getEncoded(ctx) as Any)
    }
}

internal suspend inline fun SettingObject<*, *>.isNotNullOrDefault(
    ctx: Context,
): Boolean {
    val value = this.get(ctx)
    return value != null && value != this.default
}
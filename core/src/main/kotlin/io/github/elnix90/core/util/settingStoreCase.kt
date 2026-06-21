package io.github.elnix90.core.util

import java.util.Locale.getDefault

private val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()

private val settingsStoreRegex = """SettingsStore\.*""".toRegex()

// TODO()
internal fun String.settingsStoreCase(): String {
    val withoutSettingsStoreText = settingsStoreRegex.replace(this) { "" }
    return  camelRegex.replace(withoutSettingsStoreText) {
        "_${it.value}"
    }.lowercase(getDefault())
}

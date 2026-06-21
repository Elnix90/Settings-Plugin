package io.github.elnix90.core.util

import io.github.elnix90.core.stores.SettingsStore

public infix fun SettingsStore<*,*>.prefixes(key: String): String = "${this.name}_$key"
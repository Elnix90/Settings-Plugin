package io.github.elnix90.core.util

public val String.isNotBlankKey: String
    get() = this.ifBlank { error("Key cannot be null or empty") }

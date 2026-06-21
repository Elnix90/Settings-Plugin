package io.github.elnix90.core.util

public val String.isNotBlankKey: String
    get() = this.ifEmpty { error("Key cannot be null or empty") }

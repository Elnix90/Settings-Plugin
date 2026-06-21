package io.github.elnix90.core.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

internal fun Color?.toHexWithAlpha(prefix: Boolean = true): String =
    "${if (prefix) "#" else ""}%08X".format(this?.toArgb())


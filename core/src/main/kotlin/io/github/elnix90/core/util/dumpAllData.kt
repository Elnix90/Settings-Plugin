package io.github.elnix90.core.util

import android.content.Context
import kotlinx.coroutines.flow.first

public suspend fun dumpALlData(ctx: Context): String = ctx.dataStore.data.first().toString()

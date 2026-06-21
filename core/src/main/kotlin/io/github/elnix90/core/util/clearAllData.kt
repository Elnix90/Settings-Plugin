package io.github.elnix90.core.util

import android.content.Context
import androidx.datastore.preferences.core.edit

public suspend fun clearAllData(ctx: Context) {
    ctx.dataStore.edit { preferences ->
        preferences.clear()
    }
}
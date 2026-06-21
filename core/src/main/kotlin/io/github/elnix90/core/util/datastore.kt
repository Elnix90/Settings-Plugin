package io.github.elnix90.core.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

public val Context.dataStore: DataStore<Preferences> by preferencesDataStore("AppDatastore")
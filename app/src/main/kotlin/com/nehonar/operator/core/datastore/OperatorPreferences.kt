package com.nehonar.operator.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class OperatorPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val scanlinesEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_SCANLINES] ?: false }

    suspend fun setScanlinesEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SCANLINES] = enabled }
    }

    private companion object {
        val KEY_SCANLINES = booleanPreferencesKey("scanlines_enabled")
    }
}

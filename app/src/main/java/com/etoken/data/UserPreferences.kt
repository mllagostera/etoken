package com.etoken.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "etoken")

/** Remembers the last Moxfield username so the app doesn't ask for it twice. */
class UserPreferences(private val context: Context) {

    val username: Flow<String> = context.dataStore.data.map { it[USERNAME_KEY].orEmpty() }

    suspend fun setUsername(username: String) {
        context.dataStore.edit { it[USERNAME_KEY] = username }
    }

    private companion object {
        val USERNAME_KEY = stringPreferencesKey("moxfield_username")
    }
}

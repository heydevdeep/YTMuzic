package com.shiftluna.ytmuzic.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_uri")

    val downloadFolderUri: Flow<Uri?> = context.dataStore.data
        .map { preferences ->
            preferences[DOWNLOAD_FOLDER_URI]?.let { Uri.parse(it) }
        }

    suspend fun saveDownloadFolderUri(uri: Uri?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(DOWNLOAD_FOLDER_URI)
            } else {
                preferences[DOWNLOAD_FOLDER_URI] = uri.toString()
            }
        }
    }
}

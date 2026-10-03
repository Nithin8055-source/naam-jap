package com.naamjap.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.naamjap.app.ui.theme.ThemeChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themePreferencesDataStore by preferencesDataStore(name = "user_settings")

@Singleton
class ThemePreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.themePreferencesDataStore

    val themeChoice: Flow<ThemeChoice> = dataStore.data.map { preferences ->
        ThemeChoice.fromPreference(preferences[THEME_CHOICE_KEY])
    }

    suspend fun setThemeChoice(choice: ThemeChoice) {
        dataStore.edit { preferences ->
            preferences[THEME_CHOICE_KEY] = choice.name
        }
    }

    private companion object {
        val THEME_CHOICE_KEY = stringPreferencesKey("theme_choice")
    }
}

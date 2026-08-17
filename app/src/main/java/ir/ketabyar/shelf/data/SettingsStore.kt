package ir.ketabyar.shelf.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.ketabyar.shelf.core.LibraryRules
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.dataStore by preferencesDataStore("library_rules")

class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object K { val separator = stringPreferencesKey("separator"); val literatureConfirmed = booleanPreferencesKey("literature_confirmed") }
    val rules = context.dataStore.data.map { LibraryRules(separator = it[K.separator] ?: "/", literaturePatternConfirmed = it[K.literatureConfirmed] ?: false) }
    suspend fun setSeparator(value: String) = context.dataStore.edit { it[K.separator] = value }
    suspend fun confirmLiterature(value: Boolean) = context.dataStore.edit { it[K.literatureConfirmed] = value }
}


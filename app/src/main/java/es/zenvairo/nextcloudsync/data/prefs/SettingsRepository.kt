package es.zenvairo.nextcloudsync.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import es.zenvairo.nextcloudsync.domain.model.SyncConstraints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val defaultConstraintsKey = stringPreferencesKey("default_sync_constraints")

    val defaultConstraints: Flow<SyncConstraints> = context.dataStore.data.map { prefs ->
        prefs[defaultConstraintsKey]?.let { runCatching { json.decodeFromString(SyncConstraints.serializer(), it) }.getOrNull() }
            ?: SyncConstraints()
    }

    suspend fun updateDefaultConstraints(constraints: SyncConstraints) {
        context.dataStore.edit { it[defaultConstraintsKey] = json.encodeToString(SyncConstraints.serializer(), constraints) }
    }

    fun encode(constraints: SyncConstraints): String = json.encodeToString(SyncConstraints.serializer(), constraints)
    fun decode(value: String): SyncConstraints = json.decodeFromString(SyncConstraints.serializer(), value)
}

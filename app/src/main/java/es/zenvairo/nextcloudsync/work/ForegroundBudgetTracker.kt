package es.zenvairo.nextcloudsync.work

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.fgsBudgetStore by preferencesDataStore(name = "fgs_budget")
private val CONSUMED_KEY = longPreferencesKey("consumed_millis")
private val WINDOW_START_KEY = longPreferencesKey("window_start_millis")

private const val DAILY_BUDGET_MILLIS = 6L * 60 * 60 * 1000 // Android 15+ dataSync FGS cap
private const val WINDOW_MILLIS = 24L * 60 * 60 * 1000

/**
 * Tracks how much of the Android 15+ 6h/24h dataSync-foreground-service budget this app has used,
 * so [es.zenvairo.nextcloudsync.work.SyncWorker] can skip promoting itself to a foreground
 * (visible-notification) transfer once the budget is exhausted and just run as regular
 * background work instead of risking a system-thrown timeout mid-transfer.
 */
@Singleton
class ForegroundBudgetTracker @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun hasBudgetRemaining(): Boolean {
        val prefs = context.fgsBudgetStore.data.first()
        val windowStart = prefs[WINDOW_START_KEY] ?: 0L
        val now = System.currentTimeMillis()
        if (now - windowStart > WINDOW_MILLIS) return true // window rolled over
        val consumed = prefs[CONSUMED_KEY] ?: 0L
        return consumed < DAILY_BUDGET_MILLIS
    }

    suspend fun recordUsage(durationMillis: Long) {
        context.fgsBudgetStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val windowStart = prefs[WINDOW_START_KEY] ?: now
            if (now - windowStart > WINDOW_MILLIS) {
                prefs[WINDOW_START_KEY] = now
                prefs[CONSUMED_KEY] = durationMillis
            } else {
                prefs[CONSUMED_KEY] = (prefs[CONSUMED_KEY] ?: 0L) + durationMillis
            }
        }
    }
}

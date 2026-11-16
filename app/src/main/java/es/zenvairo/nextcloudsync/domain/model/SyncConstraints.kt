package es.zenvairo.nextcloudsync.domain.model

import kotlinx.serialization.Serializable

enum class NetworkTrigger { ANY, UNMETERED, WIFI_ONLY }

enum class SyncEventTrigger { ON_FILE_CHANGE, ON_CHARGER_CONNECT, ON_WIFI_CONNECT, ON_BOOT, MANUAL_ONLY }

/**
 * Sync trigger/filter policy. Used both as the global default (stored in DataStore) and as a
 * per-folder override (stored as JSON on [es.zenvairo.nextcloudsync.data.db.entity.SyncFolderEntity]).
 */
@Serializable
data class SyncConstraints(
    val networkTrigger: NetworkTrigger = NetworkTrigger.UNMETERED,
    val allowRoaming: Boolean = false,
    val allowedSsids: List<String> = emptyList(),
    val minBatteryPercent: Int = 15,
    val chargingOnly: Boolean = false,
    val respectBatterySaver: Boolean = true,
    val intervalMinutes: Long = 60,
    val quietHoursStart: Int? = null, // 0-23, null = disabled
    val quietHoursEnd: Int? = null,
    val eventTriggers: Set<SyncEventTrigger> = setOf(SyncEventTrigger.ON_FILE_CHANGE, SyncEventTrigger.ON_BOOT),
    val excludeGlobs: List<String> = listOf(".git/**", "*.tmp", "~*"),
    val maxFileSizeBytes: Long? = null, // null = unlimited
    val skipHidden: Boolean = true
) {
    fun isWithinQuietHours(hourOfDay: Int): Boolean {
        val start = quietHoursStart ?: return false
        val end = quietHoursEnd ?: return false
        return if (start <= end) hourOfDay in start until end
        else hourOfDay >= start || hourOfDay < end
    }
}

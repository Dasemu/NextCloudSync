package es.zenvairo.nextcloudsync.work

import android.content.Context
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import es.zenvairo.nextcloudsync.domain.model.SyncConstraints
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks the parts of [SyncConstraints] that androidx.work.Constraints cannot express natively:
 * an exact minimum battery percentage, quiet hours, and SSID allow-lists. Network type, charging
 * state and battery-not-low are handled by WorkManager's own Constraints on the work request.
 */
@Singleton
class ConstraintsEvaluator @Inject constructor(@ApplicationContext private val context: Context) {

    fun canRunNow(constraints: SyncConstraints): Boolean {
        if (isInQuietHours(constraints)) return false
        if (!hasEnoughBattery(constraints)) return false
        if (!matchesAllowedSsid(constraints)) return false
        if (constraints.respectBatterySaver && isPowerSaveMode()) return false
        return true
    }

    private fun isInQuietHours(constraints: SyncConstraints): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return constraints.isWithinQuietHours(hour)
    }

    private fun hasEnoughBattery(constraints: SyncConstraints): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return true
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = batteryManager.isCharging
        if (constraints.chargingOnly && !charging) return false
        if (level < 0) return true
        return charging || level >= constraints.minBatteryPercent
    }

    private fun isPowerSaveMode(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isPowerSaveMode
    }

    @Suppress("DEPRECATION")
    private fun matchesAllowedSsid(constraints: SyncConstraints): Boolean {
        if (constraints.allowedSsids.isEmpty()) return true
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return true
        val currentSsid = runCatching { wifiManager.connectionInfo?.ssid?.trim('"') }.getOrNull() ?: return false
        return constraints.allowedSsids.any { it.equals(currentSsid, ignoreCase = true) }
    }
}

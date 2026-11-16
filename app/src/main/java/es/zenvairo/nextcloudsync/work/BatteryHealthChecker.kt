package es.zenvairo.nextcloudsync.work

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class StandbyBucket(val label: String) {
    ACTIVE("Active"), WORKING_SET("Working set"), FREQUENT("Frequent"),
    RARE("Rare"), RESTRICTED("Restricted"), UNKNOWN("Unknown")
}

data class ManufacturerBatteryTip(val manufacturerLabel: String, val settingsIntent: Intent)

data class BatteryHealthStatus(
    val isIgnoringBatteryOptimizations: Boolean,
    val standbyBucket: StandbyBucket,
    val manufacturerTip: ManufacturerBatteryTip?
)

/**
 * Surfaces the reasons background sync silently stops working on real devices: the app not being
 * exempt from Doze, the system having demoted it to a low-priority standby bucket, or an OEM's
 * own battery manager (Xiaomi/Huawei/Samsung/etc.) killing it beyond what stock Android does.
 */
@Singleton
class BatteryHealthChecker @Inject constructor(@ApplicationContext private val context: Context) {

    fun currentStatus(): BatteryHealthStatus = BatteryHealthStatus(
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
        standbyBucket = currentStandbyBucket(),
        manufacturerTip = manufacturerTip()
    )

    fun ignoreBatteryOptimizationsIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun currentStandbyBucket(): StandbyBucket {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return StandbyBucket.UNKNOWN
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return StandbyBucket.UNKNOWN
        return when (runCatching { usageStatsManager.appStandbyBucket }.getOrDefault(UsageStatsManager.STANDBY_BUCKET_ACTIVE)) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> StandbyBucket.ACTIVE
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> StandbyBucket.WORKING_SET
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> StandbyBucket.FREQUENT
            UsageStatsManager.STANDBY_BUCKET_RARE -> StandbyBucket.RARE
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> StandbyBucket.RESTRICTED
            else -> StandbyBucket.UNKNOWN
        }
    }

    private fun manufacturerTip(): ManufacturerBatteryTip? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val (label, componentAction) = when {
            "xiaomi" in manufacturer -> "Xiaomi / MIUI" to ("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")
            "huawei" in manufacturer -> "Huawei" to ("com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            "oppo" in manufacturer -> "Oppo / ColorOS" to ("com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            "vivo" in manufacturer -> "Vivo / FuntouchOS" to ("com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            "oneplus" in manufacturer -> "OnePlus" to ("com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            "samsung" in manufacturer -> "Samsung" to ("com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity")
            else -> return null
        }
        val (pkg, activity) = componentAction
        val intent = Intent().apply {
            setClassName(pkg, activity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolvable = context.packageManager.resolveActivity(intent, 0) != null
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        return ManufacturerBatteryTip(label, if (resolvable) intent else fallback)
    }
}

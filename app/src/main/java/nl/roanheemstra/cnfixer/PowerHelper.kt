package nl.roanheemstra.cnfixer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * HyperOS-specific power handling. Without these two exemptions WorkManager
 * still runs, but HyperOS likes to freeze the whole app between runs.
 */
object PowerHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { manager.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + context.packageName),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) openBatterySettings(context)
    }

    fun openBatterySettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isFailure) openAppDetails(context)
    }

    /** MIUI and HyperOS keep autostart in their own security center. */
    fun openAutostart(context: Context) {
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings",
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
                )
            ),
        )
        for (candidate in candidates) {
            val opened = runCatching {
                context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return
        }
        openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

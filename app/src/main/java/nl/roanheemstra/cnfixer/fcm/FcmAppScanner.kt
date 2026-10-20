package nl.roanheemstra.cnfixer.fcm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

object FcmAppScanner {

    private const val ACTION_C2DM_RECEIVE = "com.google.android.c2dm.intent.RECEIVE"

    /**
     * Lists third-party apps that declare an FCM receive receiver.
     * Uses the manifest <queries> entry, so no QUERY_ALL_PACKAGES is needed.
     */
    fun scan(context: Context): List<FcmApp> {
        val pm = context.packageManager
        val intent = Intent(ACTION_C2DM_RECEIVE)
        val receivers: List<ResolveInfo> = try {
            pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
        } catch (t: Throwable) {
            emptyList()
        }
        return receivers
            .mapNotNull { it.activityInfo?.packageName ?: it.resolvePackageName }
            .filter { it.isNotBlank() }
            .filter { it != MilletList.GMS }
            .distinct()
            .mapNotNull { pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                FcmApp(pkg, label)
            }
            .sortedBy { it.label.lowercase() }
    }
}

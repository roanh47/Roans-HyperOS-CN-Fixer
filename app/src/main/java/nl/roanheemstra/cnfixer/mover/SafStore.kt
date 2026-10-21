package nl.roanheemstra.cnfixer.mover

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cnDataStore by preferencesDataStore(name = "cnfixer")

class SafStore(private val context: Context) {

    val moveSource: Flow<Boolean> = context.cnDataStore.data.map { it[MOVE_SOURCE] ?: true }
    val skipPending: Flow<Boolean> = context.cnDataStore.data.map { it[SKIP_PENDING] ?: true }

    /** Background screenshot moving, while the service runs. */
    val moverAuto: Flow<Boolean> = context.cnDataStore.data.map { it[MOVER_AUTO] ?: false }

    /** Per category notification switches, both on by default. */
    val notifyScreenshots: Flow<Boolean> = context.cnDataStore.data.map { it[NOTIFY_SCREENSHOTS] ?: true }
    val notifyGms: Flow<Boolean> = context.cnDataStore.data.map { it[NOTIFY_GMS] ?: true }

    /** Persistent notification with the live FCM state. */
    val statusNotification: Flow<Boolean> = context.cnDataStore.data.map { it[STATUS_NOTIFICATION] ?: true }

    /** How often the watcher checks DCIM/Screenshots, in seconds. */
    val moverIntervalSeconds: Flow<Int> = context.cnDataStore.data.map { it[MOVER_INTERVAL] ?: 30 }

    /** First-run setup guide, so Settings can replay it for debugging. */
    val setupDone: Flow<Boolean> = context.cnDataStore.data.map { it[SETUP_DONE] ?: false }

    /** Last protection pass, shown as "Last check" on the protection screen. */
    val lastFcmRun: Flow<Long> = context.cnDataStore.data.map { it[LAST_FCM_RUN] ?: 0L }
    val lastFcmOk: Flow<Boolean> = context.cnDataStore.data.map { it[LAST_FCM_OK] ?: false }

    suspend fun setMoveSource(enabled: Boolean) {
        context.cnDataStore.edit { it[MOVE_SOURCE] = enabled }
    }

    suspend fun setSkipPending(enabled: Boolean) {
        context.cnDataStore.edit { it[SKIP_PENDING] = enabled }
    }

    suspend fun setMoverAuto(enabled: Boolean) {
        context.cnDataStore.edit { it[MOVER_AUTO] = enabled }
    }

    suspend fun setNotifyScreenshots(enabled: Boolean) {
        context.cnDataStore.edit { it[NOTIFY_SCREENSHOTS] = enabled }
    }

    suspend fun setNotifyGms(enabled: Boolean) {
        context.cnDataStore.edit { it[NOTIFY_GMS] = enabled }
    }

    suspend fun setStatusNotification(enabled: Boolean) {
        context.cnDataStore.edit { it[STATUS_NOTIFICATION] = enabled }
    }

    suspend fun setMoverIntervalSeconds(seconds: Int) {
        context.cnDataStore.edit { it[MOVER_INTERVAL] = seconds }
    }

    suspend fun setSetupDone(done: Boolean) {
        context.cnDataStore.edit { it[SETUP_DONE] = done }
    }

    suspend fun recordFcmRun(success: Boolean, atMillis: Long = System.currentTimeMillis()) {
        context.cnDataStore.edit {
            it[LAST_FCM_RUN] = atMillis
            it[LAST_FCM_OK] = success
        }
    }

    companion object {
        val MOVE_SOURCE = booleanPreferencesKey("move_source")
        val SKIP_PENDING = booleanPreferencesKey("skip_pending")
        val ASSIST_OVERRIDE = booleanPreferencesKey("assistant_override")
        val MOVER_AUTO = booleanPreferencesKey("mover_auto")
        val MOVER_INTERVAL = intPreferencesKey("mover_interval")
        val NOTIFY_SCREENSHOTS = booleanPreferencesKey("notify_screenshots")
        val NOTIFY_GMS = booleanPreferencesKey("notify_gms")
        val STATUS_NOTIFICATION = booleanPreferencesKey("status_notification")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val LAST_FCM_RUN = longPreferencesKey("last_fcm_run")
        val LAST_FCM_OK = booleanPreferencesKey("last_fcm_ok")
    }
}

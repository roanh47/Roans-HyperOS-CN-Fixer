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
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val LAST_FCM_RUN = longPreferencesKey("last_fcm_run")
        val LAST_FCM_OK = booleanPreferencesKey("last_fcm_ok")
    }
}

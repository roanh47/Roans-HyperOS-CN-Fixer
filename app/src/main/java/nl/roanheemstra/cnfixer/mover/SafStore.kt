package nl.roanheemstra.cnfixer.mover

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cnDataStore by preferencesDataStore(name = "cnfixer")

class SafStore(private val context: Context) {

    val source: Flow<String?> = context.cnDataStore.data.map { it[SRC] }
    val destination: Flow<String?> = context.cnDataStore.data.map { it[DST] }
    val moveSource: Flow<Boolean> = context.cnDataStore.data.map { it[MOVE_SOURCE] ?: true }
    val skipPending: Flow<Boolean> = context.cnDataStore.data.map { it[SKIP_PENDING] ?: true }
    val assistantOverride: Flow<Boolean> = context.cnDataStore.data.map { it[ASSIST_OVERRIDE] ?: false }

    /** Background screenshot moving, every 15 minutes while configured. */
    val moverAuto: Flow<Boolean> = context.cnDataStore.data.map { it[MOVER_AUTO] ?: false }

    /** First-run setup guide, so Settings can replay it for debugging. */
    val setupDone: Flow<Boolean> = context.cnDataStore.data.map { it[SETUP_DONE] ?: false }

    suspend fun setSource(uri: Uri) {
        persist(uri)
        context.cnDataStore.edit { it[SRC] = uri.toString() }
    }

    suspend fun setDestination(uri: Uri) {
        persist(uri)
        context.cnDataStore.edit { it[DST] = uri.toString() }
    }

    suspend fun setMoveSource(enabled: Boolean) {
        context.cnDataStore.edit { it[MOVE_SOURCE] = enabled }
    }

    suspend fun setSkipPending(enabled: Boolean) {
        context.cnDataStore.edit { it[SKIP_PENDING] = enabled }
    }

    suspend fun setAssistantOverride(enabled: Boolean) {
        context.cnDataStore.edit { it[ASSIST_OVERRIDE] = enabled }
    }

    suspend fun setMoverAuto(enabled: Boolean) {
        context.cnDataStore.edit { it[MOVER_AUTO] = enabled }
    }

    suspend fun setSetupDone(done: Boolean) {
        context.cnDataStore.edit { it[SETUP_DONE] = done }
    }

    /** Keeps the SAF grant alive across reboots. */
    private fun persist(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    companion object {
        val SRC = stringPreferencesKey("source_tree")
        val DST = stringPreferencesKey("destination_tree")
        val MOVE_SOURCE = booleanPreferencesKey("move_source")
        val SKIP_PENDING = booleanPreferencesKey("skip_pending")
        val ASSIST_OVERRIDE = booleanPreferencesKey("assistant_override")
        val MOVER_AUTO = booleanPreferencesKey("mover_auto")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
    }
}

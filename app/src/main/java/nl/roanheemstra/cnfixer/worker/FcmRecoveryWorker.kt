package nl.roanheemstra.cnfixer.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import nl.roanheemstra.cnfixer.fcm.FcmEnforcer
import nl.roanheemstra.cnfixer.fcm.FcmVerifier
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.notifications.Notify
import nl.roanheemstra.cnfixer.shizuku.ShellRunner
import java.util.concurrent.TimeUnit

class FcmRecoveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Shizuku may not be running yet (for example right after a reboot).
        if (!ShellRunner.hasPermission()) return Result.retry()

        val report = FcmEnforcer.runCorePass()

        // Record what the device actually says, not just that commands ran.
        val store = SafStore(applicationContext)
        val checks = runCatching { FcmVerifier.run() }.getOrDefault(emptyList())
        val verified = checks.isNotEmpty() && checks.all { it.ok }
        val wasOk = runCatching { store.lastFcmOk.first() }.getOrNull()
        val hasRunBefore = runCatching { store.lastFcmRun.first() }.getOrDefault(0L) > 0L
        store.recordFcmRun(verified)

        // Only when it flips from fine to broken, so this does not repeat every
        // 15 minutes while the device stays broken.
        if (!verified && (!hasRunBefore || wasOk != false)) {
            val reason = checks.firstOrNull { !it.ok }
                ?.let { "${it.label}: ${it.detail}" }
                ?: "The protection check did not pass"
            Notify.gms(
                applicationContext,
                "Google Play services protection is off",
                reason,
            )
        }

        return if (report.failures().isEmpty() || verified) Result.success() else Result.retry()
    }
}

object Scheduler {
    private const val FCM_WORK = "cnfixer-fcm-recovery"
    private const val MOVER_WORK = "cnfixer-mover-scan"

    /** WorkManager's floor is 15 minutes; that is what this app uses. */
    const val INTERVAL_MINUTES = 15L

    fun ensureScheduled(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val fcm = PeriodicWorkRequestBuilder<FcmRecoveryWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            FCM_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            fcm,
        )
    }

    /** Enables or disables the background screenshot mover. */
    fun refreshMover(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(MOVER_WORK)
            return
        }
        val mover = PeriodicWorkRequestBuilder<MoverScanWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            MOVER_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            mover,
        )
    }

    suspend fun syncFromPrefs(context: Context) {
        ensureScheduled(context)
        refreshMover(context, SafStore(context).moverAuto.first())
    }
}

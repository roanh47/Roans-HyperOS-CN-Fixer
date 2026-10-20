package nl.roanheemstra.cnfixer.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.mover.ScreenshotMover
import nl.roanheemstra.cnfixer.shizuku.ShellRunner

class MoverScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SafStore(applicationContext)

        if (!store.moverAuto.first()) return Result.success()

        // The mover drives the shell, so it needs Shizuku. Right after a reboot
        // the service is not up yet: retry instead of doing nothing.
        if (!ShellRunner.hasPermission()) return Result.retry()

        val outcome = ScreenshotMover.run(
            moveSource = store.moveSource.first(),
            skipPending = store.skipPending.first(),
        )

        return if (outcome.failed == 0) Result.success() else Result.retry()
    }
}

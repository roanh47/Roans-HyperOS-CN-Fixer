package nl.roanheemstra.cnfixer.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.mover.ScreenshotMover

class MoverScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SafStore(applicationContext)

        // The user can switch background moving off without us cancelling
        // reliably from every entry point, so check the preference as well.
        if (!store.moverAuto.first()) return Result.success()

        val source = store.source.first() ?: return Result.success()
        val destination = store.destination.first() ?: return Result.success()

        val outcome = ScreenshotMover.run(
            context = applicationContext,
            source = Uri.parse(source),
            destination = Uri.parse(destination),
            moveSource = store.moveSource.first(),
            skipPending = store.skipPending.first(),
        )

        return if (outcome.failed == 0) Result.success() else Result.retry()
    }
}

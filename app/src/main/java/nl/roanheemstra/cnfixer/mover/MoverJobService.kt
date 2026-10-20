package nl.roanheemstra.cnfixer.mover

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs the move when the media store reports new content, so there is no timer
 * and no foreground service keeping a notification on screen.
 */
class MoverJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        if (params == null) return false
        scope.launch {
            try {
                val store = SafStore(applicationContext)
                if (!store.moverAuto.first()) {
                    MoverScheduler.cancel(applicationContext)
                    return@launch
                }
                ScreenshotMover.run(
                    moveSource = store.moveSource.first(),
                    skipPending = store.skipPending.first(),
                )
            } catch (_: Throwable) {
                // A failed pass is not worth retrying with backoff: the next
                // content change triggers another one anyway.
            } finally {
                jobFinished(params, false)
                // The content trigger is one shot, so put it back in place.
                if (SafStore(applicationContext).moverAuto.first()) {
                    MoverScheduler.schedule(applicationContext)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}

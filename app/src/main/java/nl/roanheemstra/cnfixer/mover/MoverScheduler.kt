package nl.roanheemstra.cnfixer.mover

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore

/**
 * JobScheduler content trigger: the system wakes this job when the images
 * collection changes, instead of the app waking itself on a timer.
 *
 * That means no polling, no foreground service and no permanent notification.
 */
object MoverScheduler {

    private const val JOB_ID = 4711

    fun schedule(context: Context) {
        val manager = context.getSystemService(JobScheduler::class.java) ?: return
        val component = ComponentName(context, MoverJobService::class.java)

        val job = JobInfo.Builder(JOB_ID, component)
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                )
            )
            // Wait a moment so a burst of writes is handled in one pass.
            .setTriggerContentUpdateDelay(2_000)
            .setTriggerContentMaxDelay(15_000)
            .setPersisted(true)
            .build()

        runCatching { manager.schedule(job) }
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }
}

package nl.roanheemstra.cnfixer.assist

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Triggers Circle to Search directly, without the MiCTS app.
 *
 * This is what MiCTS does under the hood: it asks the voice interaction service
 * to start the current assistant session with the Circle to Search entry point.
 * On Android 14 and newer HyperOS wants the extra entry point name, which is
 * why the call has a fourth argument there.
 *
 * The default assistant has to be the Google app for this to land anywhere.
 */
object CircleToSearch {

    private const val ENTRY_POINT = 1
    private const val SESSION_ENTRY_POINT_OMNI = 7

    fun trigger(context: Context? = null): Boolean {
        val triggered = runCatching {
            val args = Bundle().apply {
                putLong("invocation_time_ms", SystemClock.elapsedRealtime())
                putInt("omni.entry_point", ENTRY_POINT)
            }

            val serviceClass = Class.forName(
                "com.android.internal.app.IVoiceInteractionManagerService"
            )
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "voiceinteraction")
            val service = Class.forName(
                "com.android.internal.app.IVoiceInteractionManagerService\$Stub"
            )
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HiddenApiBypass.invoke(
                    serviceClass,
                    service,
                    "showSessionFromSession",
                    null,
                    args,
                    SESSION_ENTRY_POINT_OMNI,
                    "hyperOS_home",
                ) as Boolean
            } else {
                HiddenApiBypass.invoke(
                    serviceClass,
                    service,
                    "showSessionFromSession",
                    null,
                    args,
                    SESSION_ENTRY_POINT_OMNI,
                ) as Boolean
            }
        }.getOrDefault(false)

        if (triggered) {
            context?.let { pulse(it) }
        }
        return triggered
    }

    private fun pulse(context: Context) {
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            }
        }
    }
}

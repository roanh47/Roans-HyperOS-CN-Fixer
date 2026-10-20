package nl.roanheemstra.cnfixer.assist

import nl.roanheemstra.cnfixer.shizuku.ShellResult
import nl.roanheemstra.cnfixer.shizuku.ShellRunner

/**
 * MiCTS only triggers Circle to Search when it is reachable through the
 * assist framework. Launching the activity directly is not enough on
 * devices where Google has not whitelisted the model, so MiCTS is
 * registered as the default assistant instead.
 *
 * HyperOS resets this value on reboot, which is why BootReceiver re-applies it.
 */
object AssistantOverride {

    const val MICTs_COMPONENT =
        "com.parallelc.micts/com.parallelc.micts.ui.activity.MainActivity"

    fun isMiCtsInstalled(packageManager: android.content.pm.PackageManager): Boolean =
        runCatching {
            packageManager.getApplicationInfo("com.parallelc.micts", 0)
            true
        }.getOrDefault(false)

    fun apply(component: String = MICTs_COMPONENT): ShellResult =
        ShellRunner.exec("settings", "put", "secure", "assistant", component)

    fun current(): ShellResult =
        ShellRunner.exec("settings", "get", "secure", "assistant")
}

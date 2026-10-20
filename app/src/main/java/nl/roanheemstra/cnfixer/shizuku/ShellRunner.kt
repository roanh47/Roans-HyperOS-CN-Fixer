package nl.roanheemstra.cnfixer.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku

data class ShellResult(
    val ok: Boolean,
    val out: String,
    val err: String,
    val code: Int,
) {
    fun summary(): String = when {
        ok -> out.ifBlank { "ok" }
        else -> err.ifBlank { out }.ifBlank { "exit code $code" }
    }
}

/**
 * Facade over Shizuku shell access.
 *
 * Call [init] once with an application context, then use [exec].
 * Arguments are passed as a list and shell-quoted, so callers never have
 * to worry about spaces in values such as the MILLET package list.
 */
object ShellRunner {

    const val REQUEST_CODE = 1001
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        appContext = context.applicationContext
        ShizukuShell.ensureBound(context.applicationContext)
    }

    fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean {
        if (!isBinderAlive()) return false
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun isShizukuInstalled(): Boolean {
        val context = appContext ?: return false
        return runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * Asks Shizuku for access. Shizuku shows its own dialog on top of this app,
     * so the call has to happen on the main thread. Nothing happens when the
     * Shizuku service is not running: there is no binder to show a dialog.
     */
    fun requestPermission(requestCode: Int = REQUEST_CODE) {
        if (!isBinderAlive()) return
        if (hasPermission()) return
        mainHandler.post {
            runCatching { Shizuku.requestPermission(requestCode) }
        }
    }

    /** Opens the Shizuku app, or its download page when it is not installed. */
    fun openShizuku(context: Context) {
        val launch = runCatching {
            context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        }.getOrNull()
        if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/RikkaApps/Shizuku/releases/latest"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun exec(vararg command: String): ShellResult = exec(command.toList())

    fun exec(command: List<String>): ShellResult {
        if (!isBinderAlive()) {
            return ShellResult(false, "", "Shizuku is not running", -1)
        }
        if (!hasPermission()) {
            return ShellResult(false, "", "Shizuku access has not been granted", -1)
        }
        val context = appContext
            ?: return ShellResult(false, "", "App context is not initialised", -1)

        ShizukuShell.ensureBound(context)
        val shellCommand = command.joinToString(" ") { quote(it) }
        return ShizukuShell.exec(shellCommand)
    }

    private fun quote(argument: String): String = "'" + argument.replace("'", "'\\''") + "'"
}

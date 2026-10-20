package nl.roanheemstra.cnfixer.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

data class ShellResult(
    val ok: Boolean,
    val out: String,
    val err: String,
    val code: Int,
) {
    fun summary(): String = when {
        ok -> out.ifBlank { "ok" }
        else -> err.ifBlank { out }.ifBlank { "foutcode $code" }
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

    private var appContext: Context? = null

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

    fun requestPermission(requestCode: Int = REQUEST_CODE) {
        if (isBinderAlive() && !hasPermission()) {
            runCatching { Shizuku.requestPermission(requestCode) }
        }
    }

    fun exec(vararg command: String): ShellResult = exec(command.toList())

    fun exec(command: List<String>): ShellResult {
        if (!isBinderAlive()) {
            return ShellResult(false, "", "Shizuku is niet beschikbaar", -1)
        }
        if (!hasPermission()) {
            return ShellResult(false, "", "Shizuku-rechten zijn niet gegeven", -1)
        }
        val context = appContext
            ?: return ShellResult(false, "", "App is niet geïnitialiseerd", -1)

        ShizukuShell.ensureBound(context)
        val shellCommand = command.joinToString(" ") { quote(it) }
        return ShizukuShell.exec(shellCommand)
    }

    private fun quote(argument: String): String = "'" + argument.replace("'", "'\\''") + "'"

    const val REQUEST_CODE = 1001
}

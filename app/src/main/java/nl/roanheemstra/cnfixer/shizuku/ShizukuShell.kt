package nl.roanheemstra.cnfixer.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import nl.roanheemstra.cnfixer.IShellService
import rikka.shizuku.Shizuku

/**
 * Binds to the Shizuku UserService that runs as the ADB shell user and
 * forwards commands to it. Binding is asynchronous, so exec() waits a
 * bounded amount of time for the connection (call from a background thread).
 */
object ShizukuShell {

    private const val POLL_INTERVAL_MS = 50L

    @Volatile
    private var service: IShellService? = null

    private var userServiceArgs: Shizuku.UserServiceArgs? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IShellService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun ensureBound(context: Context) {
        if (service != null) return
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return

        val args = userServiceArgs ?: Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shell")
            .version(1)
            .also { userServiceArgs = it }

        runCatching { Shizuku.bindUserService(args, connection) }
    }

    fun exec(command: String, timeoutMs: Long = 5_000L): ShellResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        var bound = service
        while (bound == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
            bound = service
        }

        val shell = bound
            ?: return ShellResult(false, "", "Shizuku service is not connected yet", -1)

        return try {
            val raw = shell.exec(command)
            val firstLine = raw.lineSequence().firstOrNull().orEmpty()
            val code = firstLine.removePrefix("code=").trim().toIntOrNull() ?: -1
            val output = raw.lineSequence().drop(1).joinToString("\n").trim()
            ShellResult(code == 0, if (code == 0) output else "", if (code == 0) "" else output, code)
        } catch (t: Throwable) {
            ShellResult(false, "", t.message ?: t.javaClass.simpleName, -1)
        }
    }
}

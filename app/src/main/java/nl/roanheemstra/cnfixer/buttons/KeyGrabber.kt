package nl.roanheemstra.cnfixer.buttons

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.roanheemstra.cnfixer.assist.CircleToSearch
import nl.roanheemstra.cnfixer.shizuku.ShellRunner
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Runs the native helper and turns its key stream into actions.
 *
 * While the helper holds the device, the system does not see any key of that
 * device, so everything that is not remapped is sent back with `input keyevent`.
 */
object KeyGrabber {

    private const val TAG = "KeyGrabber"
    private const val LONG_PRESS_MS = 600L

    /** Last key code that was seen, so the UI can learn an unknown button. */
    private val _lastCode = MutableStateFlow<Int?>(0)
    val lastCode: StateFlow<Int?> = _lastCode

    private var process: Process? = null
    private var reader: Thread? = null

    fun isRunning(): Boolean = process?.isAlive == true

    /** Path of the helper inside the APK's native library directory. */
    private fun helperPath(context: Context): String =
        File(context.applicationInfo.nativeLibraryDir, "libkeygd.so").absolutePath

    fun findPowerDevice(): String? {
        val listing = ShellRunner.exec("sh", "-c", "getevent -pl 2>/dev/null").out
        var current: String? = null
        for (line in listing.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("add device")) {
                current = trimmed.substringAfter(':', "").trim().ifBlank { null }
            } else if (trimmed.contains("KEY_POWER")) {
                if (current != null) return current
            }
        }
        // Fall back to the first gpio key device.
        val grep = ShellRunner.exec(
            "sh",
            "-c",
            "getevent -pl 2>/dev/null | grep -B4 KEY_POWER | grep -o '/dev/input/event[0-9]*' | head -1",
        )
        return grep.out.trim().ifBlank { null }
    }

    fun start(
        context: Context,
        intercepted: Set<Int>,
        onEvent: (code: Int, value: Int, downAt: Long) -> Unit,
    ): Boolean {
        stop()
        if (!ShellRunner.hasPermission()) return false

        val device = findPowerDevice() ?: run {
            Log.w(TAG, "no device with KEY_POWER")
            return false
        }
        val helper = helperPath(context)
        if (!File(helper).exists()) {
            Log.w(TAG, "helper missing at $helper")
            return false
        }

        val started = runCatching {
            Shizuku.newProcess(arrayOf(helper, device), null, null)
        }.getOrNull() ?: return false

        process = started
        reader = Thread {
            try {
                BufferedReader(InputStreamReader(started.inputStream)).use { input ->
                    while (true) {
                        val line = input.readLine() ?: break
                        if (!line.startsWith("K ")) continue
                        val parts = line.split(' ')
                        if (parts.size < 4) continue
                        val code = parts[1].toIntOrNull() ?: continue
                        val value = parts[2].toIntOrNull() ?: continue
                        val whenMs = parts[3].toLongOrNull() ?: 0L

                        // Report every code so an unknown key can be learned.
                        _lastCode.value = code

                        if (code !in intercepted) {
                            // Give the key back to the system.
                            if (value == 1 || value == 2) {
                                ShellRunner.exec("input", "keyevent", code.toString())
                            }
                            continue
                        }

                        onEvent(code, value, whenMs)
                    }
                }
            } catch (error: Throwable) {
                Log.w(TAG, "reader stopped: ${error.message}")
            }
        }.apply {
            isDaemon = true
            name = "keygd-reader"
            start()
        }
        return true
    }

    fun stop() {
        runCatching { process?.destroy() }
        runCatching { process?.waitFor() }
        process = null
        reader = null
    }

    fun perform(context: Context, action: RemapAction) {
        when (action) {
            RemapAction.Nothing -> Unit
            RemapAction.ScreenOff -> ShellRunner.exec("input", "keyevent", KEYCODE_SLEEP.toString())
            RemapAction.CircleToSearch -> CircleToSearch.trigger(context)
            is RemapAction.LaunchApp -> ShellRunner.exec(
                "monkey", "-p", action.packageName, "-c", "android.intent.category.LAUNCHER", "1",
            )
        }
    }

    fun isLongPress(downAt: Long, upAt: Long): Boolean = (upAt - downAt) >= LONG_PRESS_MS
}

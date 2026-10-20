package nl.roanheemstra.cnfixer.shizuku

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observable Shizuku state shared by the screens. */
object ShizukuState {
    var available by mutableStateOf(false)
        private set
    var granted by mutableStateOf(false)
        private set
    var installed by mutableStateOf(false)
        private set

    private var askedThisSession = false

    fun refresh() {
        available = ShellRunner.isBinderAlive()
        installed = ShellRunner.isShizukuInstalled()
        granted = ShellRunner.hasPermission()
    }

    fun requestPermission() {
        askedThisSession = true
        ShellRunner.requestPermission()
    }

    /**
     * Asks once per app session, and only when Shizuku is actually running.
     * The user then gets Shizuku's own permission dialog instead of a dead end.
     */
    fun autoRequestOnce() {
        refresh()
        if (available && !granted && !askedThisSession) {
            requestPermission()
        }
    }
}

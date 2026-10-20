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

    fun refresh() {
        available = ShellRunner.isBinderAlive()
        granted = ShellRunner.hasPermission()
    }
}

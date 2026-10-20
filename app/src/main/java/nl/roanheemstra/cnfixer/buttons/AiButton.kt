package nl.roanheemstra.cnfixer.buttons

import nl.roanheemstra.cnfixer.shizuku.ShellRunner

/**
 * The AI button has no separate input key to catch: on HyperOS it starts the
 * Xiaomi AI packages. Turning those off is what "button does nothing" means,
 * and it is fully reversible from here.
 *
 * Everything runs as the shell user, so no root and no input grabbing.
 */
object AiButton {

    /** Packages the AI key ends up in on HyperOS. */
    val PACKAGES = listOf(
        "com.xiaomi.aiasst.service",
        "com.xiaomi.aiasst.vision",
    )

    enum class Mode { XIAOMI_AI, OFF }

    data class State(
        val installed: List<String>,
        val enabled: List<String>,
        val errors: List<String>,
    ) {
        val mode: Mode
            get() = when {
                installed.isEmpty() -> Mode.OFF
                enabled.isEmpty() -> Mode.OFF
                else -> Mode.XIAOMI_AI
            }

        val installedLabel: String
            get() = if (installed.isEmpty()) "not installed" else installed.joinToString(", ")
    }

    fun state(): State {
        val installedList = mutableListOf<String>()
        val enabledList = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (pkg in PACKAGES) {
            val all = ShellRunner.exec("pm", "list", "packages", pkg)
            val present = all.ok && all.out.lineSequence().any { it.trim() == "package:$pkg" }
            if (!present) continue
            installedList += pkg

            val disabled = ShellRunner.exec("sh", "-c", "pm list packages -d 2>/dev/null | grep -qx 'package:$pkg'")
            val isDisabled = disabled.ok && disabled.out.isNotBlank()
            if (isDisabled) {
                // listed as disabled
            } else {
                enabledList += pkg
            }
            if (!all.ok) errors += "$pkg: ${all.summary()}"
        }

        return State(installedList, enabledList, errors)
    }

    /** Applies the choice and returns the commands with their result. */
    fun setMode(mode: Mode): List<Pair<String, Boolean>> {
        val state = state()
        val targets = state.installed.ifEmpty { PACKAGES }
        val results = mutableListOf<Pair<String, Boolean>>()

        for (pkg in targets) {
            val command = when (mode) {
                Mode.XIAOMI_AI -> listOf("pm", "enable", "--user", "0", pkg)
                Mode.OFF -> listOf("pm", "disable-user", "--user", "0", pkg)
            }
            val result = ShellRunner.exec(*command.toTypedArray())
            results += (command.joinToString(" ") + " -> " + if (result.ok) "ok" else result.summary()) to result.ok
        }
        return results
    }
}

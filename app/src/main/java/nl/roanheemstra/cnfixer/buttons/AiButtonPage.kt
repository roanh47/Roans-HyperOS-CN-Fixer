package nl.roanheemstra.cnfixer.buttons

import nl.roanheemstra.cnfixer.shizuku.ShellRunner

/**
 * Finds the HyperOS "AI button" page and opens it.
 *
 * Xiaomi keeps the short press and long press actions in its own settings app,
 * and the options there (None, DND, apps) are richer than anything an app can
 * write by itself. So instead of faking a remap, this opens the real page.
 */
object AiButtonPage {

    private val PACKAGES = listOf(
        "com.android.settings",
        "com.miui.securitycenter",
        "com.xiaomi.aiasst.service",
    )

    fun open(): String {
        val pattern = "'[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+'"

        for (pkg in PACKAGES) {
            val dump = ShellRunner.exec(
                "sh",
                "-c",
                "dumpsys package $pkg 2>/dev/null | grep -oE $pattern | sort -u " +
                    "| grep -iE 'ai.?button|button.?ai|aikey|shortcut' | head -20",
            )
            val candidates = dump.out.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.startsWith(pkg + "/") }
                .toList()

            for (component in candidates) {
                val started = ShellRunner.exec("sh", "-c", "am start -n '$component' 2>&1")
                val output = started.out + started.err
                if (started.ok && !output.contains("Error") && !output.contains("Exception")) {
                    return "Opened $component"
                }
            }
        }

        val fallback = ShellRunner.exec("sh", "-c", "am start -a android.settings.SETTINGS 2>&1")
        return if (fallback.ok) {
            "No AI button page found on this build. Opened Settings instead."
        } else {
            "Could not open it: ${fallback.summary()}"
        }
    }
}

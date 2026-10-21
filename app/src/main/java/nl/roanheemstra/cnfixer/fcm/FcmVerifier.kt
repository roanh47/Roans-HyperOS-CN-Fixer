package nl.roanheemstra.cnfixer.fcm

import nl.roanheemstra.cnfixer.shizuku.ShellRunner

/**
 * Reads the state back from the device after a pass.
 *
 * The enforcement itself only sees exit codes, which says nothing about
 * whether HyperOS still blocks Google Play services. These checks read the
 * actual values, so "it works" is an observation instead of an assumption.
 */
object FcmVerifier {

    /**
     * required = false means the value says something about the ROM but cannot
     * break FCM by itself, so it must not paint a working device as failed.
     */
    data class Check(
        val label: String,
        val ok: Boolean,
        val detail: String,
        val required: Boolean = true,
    )

    fun isVerified(checks: List<Check>): Boolean {
        val required = checks.filter { it.required }
        return required.isNotEmpty() && required.all { it.ok }
    }

    fun firstFailure(checks: List<Check>): Check? =
        checks.firstOrNull { it.required && !it.ok }

    fun run(): List<Check> {
        val checks = mutableListOf<Check>()

        val packages = ShellRunner.exec("pm", "list", "packages", MilletList.GMS)
        val present = packages.ok && packages.out.lineSequence()
            .any { it.trim() == "package:${MilletList.GMS}" }
        checks += Check(
            label = "Google Play services installed",
            ok = present,
            detail = if (present) {
                "com.google.android.gms is present"
            } else {
                "not installed, FCM cannot work on this ROM without GMS"
            },
        )

        val exempt = ShellRunner.exec("settings", "get", "system", MilletList.KEY)
        val inList = exempt.ok && MilletList.parse(exempt.out).contains(MilletList.GMS)
        checks += Check(
            label = "GMS exempt from PowerKeeper",
            ok = inList,
            detail = if (inList) "${MilletList.KEY} contains GMS" else exempt.summary(),
        )

        val runAny = ShellRunner.exec("cmd", "appops", "get", MilletList.GMS, "RUN_ANY_IN_BACKGROUND")
        val runAnyAllowed = runAny.ok && runAny.out.contains("allow", ignoreCase = true)
        checks += Check(
            label = "GMS may run in the background",
            ok = runAnyAllowed,
            detail = runAny.summary(),
            required = false,
        )

        val autostart = ShellRunner.exec(
            "cmd", "appops", "get", MilletList.GMS, FcmCommands.OP_AUTOSTART.toString(),
        )
        val autostartAllowed = autostart.ok && autostart.out.contains("allow", ignoreCase = true)
        checks += Check(
            label = "GMS autostart allowed",
            ok = autostartAllowed,
            detail = autostart.summary(),
            required = false,
        )

        return checks
    }
}

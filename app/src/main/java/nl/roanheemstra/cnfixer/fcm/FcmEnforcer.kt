package nl.roanheemstra.cnfixer.fcm

import nl.roanheemstra.cnfixer.shizuku.ShellResult
import nl.roanheemstra.cnfixer.shizuku.ShellRunner

object FcmEnforcer {

    data class Step(val label: String, val result: ShellResult)

    data class Report(val steps: List<Step>) {
        val ok: Boolean get() = steps.isNotEmpty() && steps.all { it.result.ok }
        fun failures(): List<Step> = steps.filter { !it.result.ok }
    }

    /**
     * Appends com.google.android.gms to MILLET_NO_RESTRICT_APP.
     * The list is comma separated and owned by PowerKeeper, so we read,
     * append and write back rather than replacing the value.
     */
    fun ensureGmsInMilletList(): Step {
        val current = ShellRunner.exec("settings", "get", "system", MilletList.KEY)
        if (!current.ok) return Step("Read GMS list", current)

        val existing = MilletList.parse(current.out)
        if (MilletList.GMS in existing) {
            return Step("GMS already in the list", ShellResult(true, "no change needed", "", 0))
        }
        val serialized = MilletList.serialize(existing + MilletList.GMS)
        return Step("Write GMS into the list", ShellRunner.exec("settings", "put", "system", MilletList.KEY, serialized))
    }

    /**
     * Core pass: what has to be repaired after a reboot or an OTA.
     * Per-app policies are persisted by Android itself (AppOps) so they
     * do not need re-applying here.
     */
    fun runCorePass(): Report {
        val steps = mutableListOf<Step>()
        steps += ensureGmsInMilletList()
        FcmCommands.greezerRestore().forEachIndexed { index, command ->
            steps += Step("Restore Greezer limiter (${index + 1})", ShellRunner.exec(command))
        }
        steps += Step("Write AppOps settings", ShellRunner.exec(FcmCommands.writeAppOps()))
        return Report(steps)
    }

    /** Full pass including per-app autostart and battery policy. */
    fun runFullPass(policies: List<AppPolicy>): Report {
        val steps = mutableListOf<Step>()
        steps += runCorePass().steps
        policies.forEach { policy ->
            FcmCommands.autostart(policy.packageName, policy.autostart).forEach { command ->
                steps += Step("Autostart ${policy.packageName}", ShellRunner.exec(command))
            }
            FcmCommands.battery(policy.packageName, policy.battery).forEach { command ->
                steps += Step("Battery ${policy.packageName}", ShellRunner.exec(command))
            }
        }
        steps += Step("Write AppOps settings", ShellRunner.exec(FcmCommands.writeAppOps()))
        return Report(steps)
    }
}

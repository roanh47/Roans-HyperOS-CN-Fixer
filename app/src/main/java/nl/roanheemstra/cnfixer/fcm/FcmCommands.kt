package nl.roanheemstra.cnfixer.fcm

enum class BatteryPolicy { OPTIMIZED, UNRESTRICTED, RESTRICTED }

data class AppPolicy(
    val packageName: String,
    val autostart: Boolean = true,
    val battery: BatteryPolicy = BatteryPolicy.OPTIMIZED,
)

data class FcmApp(val packageName: String, val label: String)

/**
 * Command builders for the FCM enforcement.
 *
 * Op codes come from HyperOS's AppOps service:
 *   10008 = MIUI autostart
 *   10053 = MIUI autostart switch
 * They are the same ones hyperos-fcm-fix uses, so behaviour matches what
 * already works on real devices.
 */
object FcmCommands {
    const val OP_AUTOSTART = 10008
    const val OP_AUTOSTART_SWITCH = 10053

    private const val RUN_IN_BACKGROUND = "RUN_IN_BACKGROUND"
    private const val RUN_ANY_IN_BACKGROUND = "RUN_ANY_IN_BACKGROUND"

    /** Turns off the Greezer limiter for Google Play services. */
    fun greezerRestore(): List<List<String>> = listOf(
        listOf("dumpsys", "greezer", "IM", "GMS", "disable"),
        listOf("dumpsys", "greezer", "LM", "add", MilletList.GMS),
    )

    fun autostart(packageName: String, allow: Boolean): List<List<String>> {
        val mode = if (allow) "allow" else "ignore"
        return listOf(
            listOf("cmd", "appops", "set", "--user", "0", packageName, OP_AUTOSTART.toString(), mode),
            listOf("cmd", "appops", "set", "--user", "0", packageName, OP_AUTOSTART_SWITCH.toString(), mode),
        )
    }

    fun battery(packageName: String, policy: BatteryPolicy): List<List<String>> {
        val allowBackground = policy != BatteryPolicy.RESTRICTED
        val mode = if (allowBackground) "allow" else "ignore"
        val whitelistOperation = if (policy == BatteryPolicy.UNRESTRICTED) "+" else "-"
        return listOf(
            listOf("cmd", "appops", "set", "--user", "0", packageName, RUN_IN_BACKGROUND, mode),
            listOf("cmd", "appops", "set", "--user", "0", packageName, RUN_ANY_IN_BACKGROUND, mode),
            listOf("dumpsys", "deviceidle", "whitelist", "$whitelistOperation$packageName"),
        )
    }

    fun writeAppOps(): List<String> = listOf("cmd", "appops", "write-settings")
}

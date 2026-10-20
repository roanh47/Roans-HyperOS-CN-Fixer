package nl.roanheemstra.cnfixer.shizuku

import nl.roanheemstra.cnfixer.IShellService

/**
 * Runs inside the process Shizuku starts with ADB-shell identity.
 * The AIDL stub is what the app talks to; this class is never a normal
 * Android Service, so it needs no manifest entry.
 */
class ShellUserService : IShellService.Stub() {

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            "code=$code\n$output"
        } catch (t: Throwable) {
            "code=-1\n${t.message ?: t.javaClass.simpleName}"
        }
    }
}

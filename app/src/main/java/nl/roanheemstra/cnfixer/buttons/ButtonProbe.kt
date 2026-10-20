package nl.roanheemstra.cnfixer.buttons

import nl.roanheemstra.cnfixer.shizuku.ShellRunner

/**
 * Reads what the hardware actually sends, instead of guessing key names.
 *
 * `getevent -pl` lists every input device with the key codes it supports, and
 * the settings dump shows whether HyperOS keeps the AI key behaviour in a
 * provider row we can write. Both only need the shell, so this works with
 * Shizuku and without root.
 */
object ButtonProbe {

    data class Device(
        val path: String,
        val name: String,
        val keys: List<String>,
    ) {
        val hasPower: Boolean get() = keys.contains("KEY_POWER")

        /** Vendor keys are the ones without a standard Android name. */
        val vendorKeys: List<String>
            get() = keys.filter { it.startsWith("KEY_VENDOR") || it.contains("AI") }
    }

    data class Report(
        val devices: List<Device>,
        val settings: List<String>,
        val packages: List<String>,
        val errors: List<String>,
        val raw: String,
    ) {
        val readable: String
            get() = buildString {
                devices.forEach { device ->
                    append(device.path).append("  ").append(device.name).append('\n')
                    if (device.keys.isNotEmpty()) {
                        append("  keys: ").append(device.keys.joinToString(" ")).append('\n')
                    }
                }
                if (settings.isNotEmpty()) {
                    append('\n').append("settings:").append('\n')
                    settings.forEach { append("  ").append(it).append('\n') }
                }
                if (packages.isNotEmpty()) {
                    append('\n').append("packages:").append('\n')
                    packages.forEach { append("  ").append(it).append('\n') }
                }
            }
    }

    fun scan(): Report {
        val errors = mutableListOf<String>()

        val getevent = ShellRunner.exec("sh", "-c", "getevent -pl 2>&1")
        if (!getevent.ok) errors += "getevent: ${getevent.summary()}"

        val deviceText = if (getevent.out.isNotBlank()) getevent.out else getevent.err
        val devices = parseDevices(deviceText)

        val settings = ShellRunner.exec(
            "sh",
            "-c",
            "for t in secure system global; do " +
                "settings list \$t 2>/dev/null | grep -iE 'ai_|_ai|key|power|assist|voice|button|side'; done",
        ).out.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        val packages = ShellRunner.exec(
            "sh",
            "-c",
            "pm list packages 2>/dev/null | grep -iE 'aiasst|voiceassist|hyperai|xiaomi\\.ai|micts'",
        ).out.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        return Report(
            devices = devices,
            settings = settings,
            packages = packages,
            errors = errors,
            raw = deviceText,
        )
    }

    private fun parseDevices(text: String): List<Device> {
        val devices = mutableListOf<Device>()
        var path: String? = null
        var name = ""
        var keys = mutableListOf<String>()

        fun flush() {
            val current = path
            if (current != null) {
                devices += Device(current, name, keys.toList())
            }
            path = null
            name = ""
            keys = mutableListOf()
        }

        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("add device") -> {
                    flush()
                    path = trimmed.substringAfter(':', "").trim()
                }

                trimmed.startsWith("name:") -> {
                    name = trimmed.removePrefix("name:").trim().trim('"')
                }

                trimmed.contains("KEY (") || trimmed.startsWith("KEY (") -> {
                    val codes = trimmed.substringAfter(':').trim()
                        .split(Regex("\\s+"))
                        .filter { it.startsWith("KEY_") }
                    keys += codes
                }
            }
        }
        flush()
        return devices
    }
}

package nl.roanheemstra.cnfixer.mover

import nl.roanheemstra.cnfixer.shizuku.ShellRunner

/**
 * Moves screenshots with the ADB shell user, through Shizuku.
 *
 * The paths are fixed on purpose: Xiaomi always writes to DCIM/Screenshots and
 * Pictures/Screenshots is where Google Photos treats them as a separate device
 * folder. Nothing to pick, so there is nothing to configure wrong.
 */
object ScreenshotMover {

    const val SOURCE_DIR = "/sdcard/DCIM/Screenshots"
    const val DEST_DIR = "/sdcard/Pictures/Screenshots"

    data class Outcome(val moved: Int, val failed: Int, val errors: List<String>)

    fun run(moveSource: Boolean, skipPending: Boolean): Outcome {
        val target = ShellRunner.exec("mkdir", "-p", DEST_DIR)
        if (!target.ok) {
            return Outcome(0, 1, listOf("Cannot create $DEST_DIR: ${target.summary()}"))
        }

        val listing = ShellRunner.exec("sh", "-c", "ls -1 '$SOURCE_DIR' 2>/dev/null || true")
        if (!listing.ok) {
            return Outcome(0, 1, listOf("Cannot read $SOURCE_DIR: ${listing.summary()}"))
        }

        val names = listing.out.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith(".") }
            .filterNot { skipPending && it.contains(".pending", ignoreCase = true) }
            .toList()

        if (names.isEmpty()) return Outcome(0, 0, emptyList())

        var moved = 0
        val errors = mutableListOf<String>()

        for (name in names) {
            val source = "$SOURCE_DIR/$name"
            val destination = "$DEST_DIR/$name"
            val verb = if (moveSource) "mv -f" else "cp -f"
            val result = ShellRunner.exec(
                "sh",
                "-c",
                "$verb '${escape(source)}' '${escape(destination)}'",
            )
            if (!result.ok) {
                errors += "$name: ${result.summary()}"
                continue
            }
            moved++
            // Best effort: ask the media scanner to pick the new path up so
            // Google Photos shows it without waiting for a reboot.
            ShellRunner.exec(
                "sh",
                "-c",
                "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE " +
                    "-d 'file://${escape(destination)}' >/dev/null 2>&1 || true",
            )
        }

        return Outcome(moved, errors.size, errors)
    }

    private fun escape(value: String): String = value.replace("'", "'\\''")
}

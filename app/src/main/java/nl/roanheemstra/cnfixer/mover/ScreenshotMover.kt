package nl.roanheemstra.cnfixer.mover

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object ScreenshotMover {

    data class Outcome(
        val moved: Int,
        val failed: Int,
        val errors: List<String>,
    ) {
        val ok: Boolean get() = failed == 0
    }

    fun run(
        context: Context,
        source: Uri,
        destination: Uri,
        moveSource: Boolean,
        skipPending: Boolean,
    ): Outcome {
        if (source == destination) {
            return Outcome(0, 0, listOf("Bron en doel zijn dezelfde map"))
        }

        val sourceDir = DocumentFile.fromTreeUri(context, source)
        val destinationDir = DocumentFile.fromTreeUri(context, destination)

        if (sourceDir == null || !sourceDir.isDirectory) {
            return Outcome(0, 0, listOf("Source folder is not readable"))
        }
        if (destinationDir == null || !destinationDir.isDirectory) {
            return Outcome(0, 0, listOf("Destination folder is not readable"))
        }

        var moved = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for (file in sourceDir.listFiles()) {
            if (!file.isFile) continue
            val name = file.name ?: continue

            // The camera app writes a hidden temp file and renames it afterwards.
            if (skipPending && (name.contains(".pending") || name.startsWith("."))) continue

            // Already present at the destination: leave it alone.
            if (destinationDir.findFile(name) != null) continue

            val target = try {
                destinationDir.createFile(file.type ?: "application/octet-stream", name)
                    ?: throw IllegalStateException("could not create the destination file")
            } catch (t: Throwable) {
                failed++
                errors += "$name: ${t.message ?: t.javaClass.simpleName}"
                continue
            }

            try {
                val input = context.contentResolver.openInputStream(file.uri)
                    ?: throw IllegalStateException("source could not be read")
                input.use { source ->
                    val output = context.contentResolver.openOutputStream(target.uri)
                        ?: throw IllegalStateException("geen schrijftoegang tot doel")
                    output.use { destinationStream -> source.copyTo(destinationStream) }
                }

                if (moveSource && !file.delete()) {
                    errors += "$name: copied, but the source could not be deleted"
                }
                moved++
            } catch (t: Throwable) {
                failed++
                errors += "$name: ${t.message ?: t.javaClass.simpleName}"
                runCatching { target.delete() }
            }
        }

        return Outcome(moved, failed, errors)
    }
}

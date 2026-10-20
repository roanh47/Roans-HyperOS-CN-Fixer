package nl.roanheemstra.cnfixer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.mover.ScreenshotMover
import nl.roanheemstra.cnfixer.worker.Scheduler

@Composable
fun MoverScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SafStore(context) }

    val source by store.source.collectAsState(initial = null)
    val destination by store.destination.collectAsState(initial = null)
    val moveSource by store.moveSource.collectAsState(initial = true)
    val skipPending by store.skipPending.collectAsState(initial = true)
    val moverAuto by store.moverAuto.collectAsState(initial = false)

    var log by remember { mutableStateOf("") }

    val pickSource = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) scope.launch { store.setSource(uri) } }

    val pickDestination = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) scope.launch { store.setDestination(uri) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Folders") {
            Text(
                text = source ?: "no source folder selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { pickSource.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pick source (DCIM/Screenshots)") }

            Spacer(Modifier.height(16.dp))

            Text(
                text = destination ?: "no destination folder selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { pickDestination.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pick destination (Pictures/Screenshots)") }
        }

        SectionCard("Background moving") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Move every 15 minutes", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Runs through WorkManager, also when the app is closed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = moverAuto,
                    enabled = source != null && destination != null,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            store.setMoverAuto(enabled)
                            Scheduler.refreshMover(context, enabled)
                            log = if (enabled) {
                                "Background moving is on."
                            } else {
                                "Background moving is off."
                            }
                        }
                    },
                )
            }
            if (source == null || destination == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pick both folders first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard("Options") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Delete from source", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Move instead of copy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = moveSource,
                    onCheckedChange = { scope.launch { store.setMoveSource(it) } },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Skip temporary files", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Skips the .pending files the camera app creates",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = skipPending,
                    onCheckedChange = { scope.launch { store.setSkipPending(it) } },
                )
            }
        }

        Button(
            enabled = source != null && destination != null,
            onClick = {
                val src = source ?: return@Button
                val dst = destination ?: return@Button
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        ScreenshotMover.run(
                            context = context,
                            source = Uri.parse(src),
                            destination = Uri.parse(dst),
                            moveSource = moveSource,
                            skipPending = skipPending,
                        )
                    }
                    log = buildString {
                        append("Moved: ${outcome.moved}\n")
                        append("Failed: ${outcome.failed}\n")
                        outcome.errors.forEach { append("- $it\n") }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Run now")
        }

        LogBox(log)

        SectionCard("Why not All Files Access") {
            Text(
                "This app uses the Storage Access Framework with a persistent grant on exactly " +
                    "two folders. FolderSync asks for MANAGE_EXTERNAL_STORAGE and runs into the " +
                    "scoped storage restrictions instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

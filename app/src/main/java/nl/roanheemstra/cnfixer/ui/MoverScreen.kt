package nl.roanheemstra.cnfixer.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import nl.roanheemstra.cnfixer.shizuku.ShizukuState
import nl.roanheemstra.cnfixer.worker.Scheduler

@Composable
fun MoverScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SafStore(context) }

    val moveSource by store.moveSource.collectAsState(initial = true)
    val skipPending by store.skipPending.collectAsState(initial = true)
    val moverAuto by store.moverAuto.collectAsState(initial = false)

    var hard by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { ShizukuState.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Folders") {
            StatusRow(
                label = "From",
                value = ScreenshotMover.SOURCE_DIR,
                level = StatusLevel.OK,
            )
            StatusRow(
                label = "To",
                value = ScreenshotMover.DEST_DIR,
                level = StatusLevel.OK,
            )
            Text(
                "Fixed paths. Xiaomi always writes screenshots to DCIM/Screenshots and " +
                    "Google Photos treats Pictures/Screenshots as a separate device folder, so " +
                    "there is nothing to pick. The move runs with Shizuku, which is why this fix " +
                    "needs no storage permission at all.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Automatic moving") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Every 15 minutes", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Runs through WorkManager, also when the app is closed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = moverAuto,
                    enabled = ShizukuState.granted,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            store.setMoverAuto(enabled)
                            Scheduler.refreshMover(context, enabled)
                            log = if (enabled) "Automatic moving is on." else "Automatic moving is off."
                        }
                    },
                )
            }
            if (!ShizukuState.granted) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Shizuku access is missing. Grant it in Settings first.",
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
            enabled = !hard && ShizukuState.granted,
            onClick = {
                hard = true
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        ScreenshotMover.run(
                            moveSource = moveSource,
                            skipPending = skipPending,
                        )
                    }
                    log = buildString {
                        append("Moved: ${outcome.moved}\n")
                        append("Failed: ${outcome.failed}\n")
                        outcome.errors.forEach { append("- $it\n") }
                        if (outcome.moved == 0 && outcome.failed == 0) {
                            append("Nothing new in ${ScreenshotMover.SOURCE_DIR}.")
                        }
                    }
                    hard = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Move now")
        }

        LogBox(log)
    }
}

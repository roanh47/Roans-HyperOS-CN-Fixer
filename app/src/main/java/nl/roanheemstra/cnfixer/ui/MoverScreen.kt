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
import androidx.compose.material3.OutlinedButton
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
import nl.roanheemstra.cnfixer.mover.MoverService
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.mover.ScreenshotMover
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

@Composable
fun MoverScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SafStore(context) }

    val moveSource by store.moveSource.collectAsState(initial = true)
    val skipPending by store.skipPending.collectAsState(initial = true)
    val moverAuto by store.moverAuto.collectAsState(initial = false)
    val interval by store.moverIntervalSeconds.collectAsState(initial = 30)

    var busy by remember { mutableStateOf(false) }
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
                value = ScreenshotMover.DISPLAY_SOURCE,
                level = StatusLevel.OK,
            )
            StatusRow(
                label = "To",
                value = ScreenshotMover.DISPLAY_DEST,
                level = StatusLevel.OK,
            )
            Text(
                "Fixed folders in the storage root. Xiaomi always writes screenshots to " +
                    "DCIM/Screenshots and Google Photos treats Pictures/Screenshots as a separate " +
                    "device folder, so there is nothing to pick. The move runs with Shizuku, which " +
                    "is why this fix needs no storage permission at all.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Automatic moving") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Watch the folder", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "A background service checks the folder every ${interval}s",
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
                            if (enabled) MoverService.start(context) else MoverService.stop(context)
                            log = if (enabled) "Watching started." else "Watching stopped."
                        }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Check every",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { seconds ->
                    if (seconds == interval) {
                        Button(
                            onClick = { scope.launch { store.setMoverIntervalSeconds(seconds) } },
                            modifier = Modifier.weight(1f),
                        ) { Text("${seconds}s") }
                    } else {
                        OutlinedButton(
                            onClick = { scope.launch { store.setMoverIntervalSeconds(seconds) } },
                            modifier = Modifier.weight(1f),
                        ) { Text("${seconds}s") }
                    }
                }
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
            enabled = !busy && ShizukuState.granted,
            onClick = {
                busy = true
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
                            append("Nothing new in ${ScreenshotMover.DISPLAY_SOURCE}.")
                        }
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Move now")
        }

        LogBox(log)
    }
}

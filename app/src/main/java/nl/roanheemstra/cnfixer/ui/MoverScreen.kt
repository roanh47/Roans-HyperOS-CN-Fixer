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

@Composable
fun MoverScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SafStore(context) }

    val source by store.source.collectAsState(initial = null)
    val destination by store.destination.collectAsState(initial = null)
    val moveSource by store.moveSource.collectAsState(initial = true)
    val skipPending by store.skipPending.collectAsState(initial = true)

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
        Text("Screenshots", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Mappen") {
            Text(
                text = source ?: "geen bronmap gekozen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { pickSource.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Bron kiezen (DCIM/Screenshots)") }

            Spacer(Modifier.height(16.dp))

            Text(
                text = destination ?: "geen doelmap gekozen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { pickDestination.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Doel kiezen (Pictures/Screenshots)") }
        }

        SectionCard("Opties") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bron verwijderen", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Verplaatsen in plaats van kopiëren",
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
                    Text("Tijdelijke bestanden overslaan", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Slaat .pending over die de camera-app aanmaakt",
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
                        append("Verplaatst: ${outcome.moved}\n")
                        append("Mislukt: ${outcome.failed}\n")
                        outcome.errors.forEach { append("- $it\n") }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Nu uitvoeren")
        }

        LogBox(log)

        SectionCard("Waarom niet via All Files Access") {
            Text(
                "Deze app gebruikt de Storage Access Framework met een persistente grant op " +
                    "precies twee mappen. FolderSync vraagt MANAGE_EXTERNAL_STORAGE en loopt " +
                    "daardoor juist tegen de scoped-storage-restricties aan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

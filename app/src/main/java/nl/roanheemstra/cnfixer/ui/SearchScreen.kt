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
import nl.roanheemstra.cnfixer.assist.AssistantOverride
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SafStore(context) }

    val overrideEnabled by store.assistantOverride.collectAsState(initial = false)
    var micTsInstalled by remember { mutableStateOf(false) }
    var currentAssistant by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        ShizukuState.refresh()
        micTsInstalled = AssistantOverride.isMiCtsInstalled(context.packageManager)
        if (ShizukuState.granted) {
            currentAssistant = withContext(Dispatchers.IO) { AssistantOverride.current().out }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!ShizukuState.granted) {
            Text(
                "Shizuku access is missing. Grant it in Settings first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SectionCard("Status") {
            StatusRow(
                label = "MiCTS installed",
                value = if (micTsInstalled) "yes" else "no",
                level = if (micTsInstalled) StatusLevel.OK else StatusLevel.WARN,
            )
            StatusRow(
                label = "Current assistant",
                value = currentAssistant.ifBlank { "unknown" },
                level = StatusLevel.OK,
            )
        }

        SectionCard("Why it works this way") {
            Text(
                "Opening MiCTS alone is not enough: HyperOS then gives the app no assist context " +
                    "and the Omni call fails. That is why this app registers MiCTS as the default " +
                    "assistant, so the assist gesture runs through MiCTS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "HyperOS resets this value on every reboot. The background work sets it again as " +
                    "soon as Shizuku is running.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Setting") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MiCTS as assistant", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Re-apply after every reboot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = overrideEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            store.setAssistantOverride(enabled)
                            if (enabled) {
                                val result = withContext(Dispatchers.IO) { AssistantOverride.apply() }
                                log = if (result.ok) "Applied." else "Failed: ${result.summary()}"
                                currentAssistant = withContext(Dispatchers.IO) {
                                    AssistantOverride.current().out
                                }
                            }
                        }
                    },
                )
            }
        }

        Button(
            enabled = ShizukuState.granted && micTsInstalled,
            onClick = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) { AssistantOverride.apply() }
                    log = if (result.ok) {
                        "Applied. Now long press your gesture bar or home button."
                    } else {
                        "Failed: ${result.summary()}"
                    }
                    currentAssistant = withContext(Dispatchers.IO) { AssistantOverride.current().out }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Apply now")
        }

        LogBox(log)

        SectionCard("When it does not work") {
            Text(
                "If Google reports 'Omni invocation failed: not enabled', Google has disabled " +
                    "Circle to Search for this model. That is only bypassable with root " +
                    "(LSPosed and device spoofing). Without root it stops here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

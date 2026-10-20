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
        Text("Circle to Search", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Status") {
            StatusRow(
                label = "MiCTS geïnstalleerd",
                value = if (micTsInstalled) "ja" else "nee",
                level = if (micTsInstalled) StatusLevel.OK else StatusLevel.WARN,
            )
            StatusRow(
                label = "Shizuku-rechten",
                value = if (ShizukuState.granted) "gegeven" else "ontbreken",
                level = if (ShizukuState.granted) StatusLevel.OK else StatusLevel.ERROR,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (currentAssistant.isBlank()) "assistent: onbekend" else "assistent: $currentAssistant",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Waarom dit zo werkt") {
            Text(
                "MiCTS alleen openen is niet genoeg: HyperOS geeft de app dan geen " +
                    "assist-context en de Omni-aanroep faalt. Daarom registreert deze app " +
                    "MiCTS als de standaard assistent, zodat de assist-gesture via MiCTS loopt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "HyperOS zet deze waarde na elke reboot terug. De achtergrondtaak zet hem " +
                    "opnieuw zodra Shizuku weer draait.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Instelling") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MiCTS als assistent", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Opnieuw toepassen na elke reboot",
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
                                log = if (result.ok) "Toegepast." else "Mislukt: ${result.summary()}"
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
                        "Toegepast. Houd nu je gesture-balk of home-knop lang ingedrukt."
                    } else {
                        "Mislukt: ${result.summary()}"
                    }
                    currentAssistant = withContext(Dispatchers.IO) { AssistantOverride.current().out }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Nu toepassen")
        }

        LogBox(log)

        SectionCard("Als het niet werkt") {
            Text(
                "Meldt Google 'Omni invocation failed: not enabled', dan heeft Google Circle " +
                    "to Search voor dit model uitgeschakeld. Dat is alleen te omzeilen met " +
                    "root (LSPosed + device spoof). Zonder root stopt het hier.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

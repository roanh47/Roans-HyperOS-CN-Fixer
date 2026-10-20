package nl.roanheemstra.cnfixer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.roanheemstra.cnfixer.buttons.AiButton
import nl.roanheemstra.cnfixer.buttons.ButtonProbe
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

@Composable
fun ButtonsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf<ButtonProbe.Report?>(null) }
    var aiState by remember { mutableStateOf<AiButton.State?>(null) }
    var aiLog by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        ShizukuState.refresh()
        if (ShizukuState.granted) {
            aiState = withContext(Dispatchers.IO) { runCatching { AiButton.state() }.getOrNull() }
        }
    }

    fun applyAi(mode: AiButton.Mode) {
        aiBusy = true
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                runCatching { AiButton.setMode(mode) }.getOrDefault(emptyList())
            }
            aiState = withContext(Dispatchers.IO) { runCatching { AiButton.state() }.getOrNull() }
            aiLog = results.joinToString("\n") { it.first }
            aiBusy = false
        }
    }

    val current = report

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("What this reads") {
            Text(
                "The power button and the AI button are hardware keys. This screen asks the " +
                    "input layer what they actually send, so a remap can target the right key " +
                    "instead of guessing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val currentAi = aiState
        SectionCard("AI button") {
            if (currentAi == null) {
                Text(
                    "Grant Shizuku access to read the AI packages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                StatusRow(
                    label = "Packages",
                    value = currentAi.installedLabel,
                    level = if (currentAi.installed.isEmpty()) StatusLevel.WARN else StatusLevel.OK,
                )
                StatusRow(
                    label = "Current action",
                    value = when (currentAi.mode) {
                        AiButton.Mode.XIAOMI_AI -> "Xiaomi AI"
                        AiButton.Mode.OFF -> "off"
                    },
                    level = StatusLevel.OK,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentAi.mode == AiButton.Mode.XIAOMI_AI) {
                        Button(
                            enabled = !aiBusy,
                            onClick = { applyAi(AiButton.Mode.XIAOMI_AI) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Xiaomi AI") }
                    } else {
                        OutlinedButton(
                            enabled = !aiBusy,
                            onClick = { applyAi(AiButton.Mode.XIAOMI_AI) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Xiaomi AI") }
                    }
                    if (currentAi.mode == AiButton.Mode.OFF) {
                        Button(
                            enabled = !aiBusy,
                            onClick = { applyAi(AiButton.Mode.OFF) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Off") }
                    } else {
                        OutlinedButton(
                            enabled = !aiBusy,
                            onClick = { applyAi(AiButton.Mode.OFF) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Off") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Off disables the Xiaomi AI packages, so the key does nothing. That is " +
                        "reversible: pick Xiaomi AI and they are enabled again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LogBox(aiLog)
            }
        }

        SectionCard("Power button") {
            StatusRow(
                label = "Short press",
                value = "screen off",
                level = StatusLevel.OK,
            )
            Text(
                "A short press is handled inside system_server before any app sees the key, so " +
                    "changing it needs the input device to be taken over: root, or a native " +
                    "helper that grabs it through the shell. Taking it over also stops the screen " +
                    "from turning off by itself, which has to be rebuilt by hand. No shortcut for " +
                    "that exists at app level.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            enabled = !busy && ShizukuState.granted,
            onClick = {
                busy = true
                note = ""
                scope.launch {
                    val result = withContext(Dispatchers.IO) { ButtonProbe.scan() }
                    report = result
                    note = when {
                        !ShizukuState.granted -> "Shizuku access is missing."
                        result.devices.isEmpty() -> "No input devices were returned."
                        else -> "Found ${result.devices.size} input device(s)."
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (report == null) "Scan buttons" else "Scan again") }

        if (current != null) {
            SectionCard("Input devices") {
                if (current.devices.isEmpty()) {
                    Text(
                        "Nothing returned. Check that Shizuku is running and has access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                current.devices.forEach { device ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        device.name.ifBlank { device.path },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        device.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (device.keys.isNotEmpty()) {
                        Text(
                            device.keys.joinToString("  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val marks = buildList {
                        if (device.hasPower) add("power key here")
                        if (device.vendorKeys.isNotEmpty()) add("vendor keys: ${device.vendorKeys.joinToString()}")
                    }
                    if (marks.isNotEmpty()) {
                        Text(
                            marks.joinToString("  |  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            if (current.settings.isNotEmpty()) {
                SectionCard("HyperOS settings that mention keys") {
                    Text(
                        current.settings.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (current.packages.isNotEmpty()) {
                SectionCard("AI packages") {
                    Text(
                        current.packages.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("CN Fixer buttons", current.readable)
                    )
                    note = "Copied to the clipboard."
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy the result") }

            LogBox(note)

            SectionCard("What can change without root") {
                Text(
                    "Power button, short press: no. The system handles that key before any app " +
                        "sees it, so a remap has to take the input device away from the system. " +
                        "That needs root, or a native helper that grabs the device through the " +
                        "shell. Taking it away also means the screen no longer turns off by " +
                        "itself, which is why this is not a one line fix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "AI button: yes, if it is a HyperOS setting. Xiaomi stores the AI key action " +
                        "in its own setting, so writing that row with the shell changes what the " +
                        "button starts, without touching the input layer at all. The scan above " +
                        "shows whether this device has that row.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

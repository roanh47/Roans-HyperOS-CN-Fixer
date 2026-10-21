package nl.roanheemstra.cnfixer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.roanheemstra.cnfixer.buttons.AiButton
import nl.roanheemstra.cnfixer.buttons.AiButtonPage
import nl.roanheemstra.cnfixer.buttons.ButtonProbe
import nl.roanheemstra.cnfixer.buttons.KeyGrabber
import nl.roanheemstra.cnfixer.buttons.KeyRemapService
import nl.roanheemstra.cnfixer.buttons.RemapAction
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

@Composable
fun ButtonsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SafStore(context) }

    var report by remember { mutableStateOf<ButtonProbe.Report?>(null) }
    var aiState by remember { mutableStateOf<AiButton.State?>(null) }
    var aiLog by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val remapEnabled by store.remapEnabled.collectAsState(initial = false)
    val remapShort by store.remapShort.collectAsState(initial = "screen_off")
    val remapLong by store.remapLong.collectAsState(initial = "none")
    val remapAi by store.remapAi.collectAsState(initial = "none")
    val remapAiCode by store.remapAiCode.collectAsState(initial = -1)
    val lastCode by KeyGrabber.lastCode.collectAsState(initial = 0)

    var pickTarget by remember { mutableStateOf<String?>(null) }

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

    fun saveAction(target: String, encoded: String) {
        scope.launch {
            when (target) {
                "short" -> store.setRemapShort(encoded)
                "long" -> store.setRemapLong(encoded)
                else -> store.setRemapAi(encoded)
            }
            pickTarget = null
            if (remapEnabled) {
                // The intercepted keys changed, so the helper restarts once.
                KeyRemapService.stop(context)
                delay(400)
                KeyRemapService.start(context)
            }
        }
    }

    pickTarget?.let { target ->
        ActionPicker(
            onDismiss = { pickTarget = null },
            onPick = { encoded -> saveAction(target, encoded) },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Key remap") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Take over the power key", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "The key is grabbed through Shizuku, so the system stops seeing it. " +
                            "Every other key is sent back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = remapEnabled,
                    enabled = ShizukuState.granted,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            store.setRemapEnabled(enabled)
                            if (enabled) {
                                KeyRemapService.start(context)
                                note = "Waiting for the first key event."
                            } else {
                                KeyRemapService.stop(context)
                                note = "Key given back to the system."
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            RemapRow("Short press", RemapAction.describe(remapShort)) { pickTarget = "short" }
            RemapRow("Long press", RemapAction.describe(remapLong)) { pickTarget = "long" }
            RemapRow(
                label = "AI button",
                value = if (remapAiCode < 0) {
                    "not learned"
                } else {
                    "code $remapAiCode, ${RemapAction.describe(remapAi)}"
                },
            ) { pickTarget = "ai" }

            Spacer(Modifier.height(8.dp))
            if (lastCode != null && lastCode != 0 && lastCode != 116) {
                OutlinedButton(
                    onClick = {
                        val code = lastCode ?: return@OutlinedButton
                        scope.launch {
                            store.setRemapAiCode(code)
                            note = "Stored code $code for the AI button."
                            if (remapEnabled) {
                                KeyRemapService.stop(context)
                                delay(400)
                                KeyRemapService.start(context)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Use the last key I pressed (code $lastCode)") }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                "To learn the AI button: turn this on, press the AI button once, then tap the " +
                    "button above. The helper reports every code it sees.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LogBox(note)
        }

        val currentAi = aiState
        SectionCard("AI button (Xiaomi)") {
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

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        aiLog = withContext(Dispatchers.IO) { AiButtonPage.open() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open the HyperOS AI button page") }
        }

        SectionCard("What this reads") {
            Text(
                "The power button and the AI button are hardware keys. This screen asks the " +
                    "input layer what they actually send, so a remap can target the right key " +
                    "instead of guessing.",
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

        if (report != null) {
            val current = report!!
            SectionCard("Input devices") {
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
                }
            }

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("CN Fixer buttons", current.readable))
                    note = "Copied to the clipboard."
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy the result") }

            LogBox(note)
        }
    }
}

@Composable
private fun RemapRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) { Text("Change") }
    }
}

@Composable
private fun ActionPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var showApps by remember { mutableStateOf(false) }
    val apps = remember { launchableApps(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showApps) "Pick an app" else "What should it do") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (!showApps) {
                    listOf(
                        RemapAction.Nothing,
                        RemapAction.ScreenOff,
                        RemapAction.CircleToSearch,
                    ).forEach { action ->
                        TextButton(
                            onClick = { onPick(action.encode()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(RemapAction.describe(action.encode())) }
                    }
                    TextButton(
                        onClick = { showApps = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick an app") }
                } else {
                    apps.forEach { (label, packageName) ->
                        TextButton(
                            onClick = { onPick(RemapAction.LaunchApp(packageName, label).encode()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun launchableApps(context: Context): List<Pair<String, String>> {
    val manager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return manager.queryIntentActivities(intent, 0)
        .mapNotNull { info ->
            val app = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            manager.getApplicationLabel(app).toString() to app.packageName
        }
        .distinctBy { it.second }
        .sortedBy { it.first.lowercase() }
}

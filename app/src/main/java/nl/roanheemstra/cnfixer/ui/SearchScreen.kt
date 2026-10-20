package nl.roanheemstra.cnfixer.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import nl.roanheemstra.cnfixer.assist.CircleToSearch
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

/**
 * Circle to Search is built into this app now. There is no MiCTS to install:
 * the trigger calls the same hidden voice interaction API MiCTS used.
 */
@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var assistant by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }
    var googleInstalled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ShizukuState.refresh()
        googleInstalled = runCatching {
            context.packageManager.getApplicationInfo(
                "com.google.android.googlequicksearchbox",
                0,
            )
            true
        }.getOrDefault(false)
        assistant = withContext(Dispatchers.IO) {
            nl.roanheemstra.cnfixer.shizuku.ShellRunner
                .exec("settings", "get", "secure", "assistant")
                .out
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Status") {
            StatusRow(
                label = "Google app",
                value = if (googleInstalled) "installed" else "missing",
                level = if (googleInstalled) StatusLevel.OK else StatusLevel.ERROR,
            )
            StatusRow(
                label = "Default assistant",
                value = when {
                    assistant.isBlank() -> "not set"
                    assistant.contains("googlequicksearchbox") -> "Google"
                    else -> assistant.substringAfter('/').substringAfterLast('.')
                        .ifBlank { "custom" }
                },
                level = if (assistant.contains("googlequicksearchbox")) {
                    StatusLevel.OK
                } else {
                    StatusLevel.WARN
                },
            )
            if (!assistant.contains("googlequicksearchbox")) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Circle to Search starts the session of the current assistant, so the Google " +
                        "app has to be the default assistant. Set it in the screen behind the " +
                        "button below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                        }.onFailure {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_SETTINGS)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open assistant settings") }
            }
        }

        SectionCard("Trigger") {
            Text(
                "This calls the hidden voice interaction API directly, which is exactly what " +
                    "MiCTS did. No extra app needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    log = if (CircleToSearch.trigger(context)) {
                        "Triggered."
                    } else {
                        "Failed. Check that Google is the default assistant."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Trigger now") }
        }

        SectionCard("Quick Settings tile") {
            Text(
                "Add the Circle to Search tile to your Quick Settings panel. That is the no-root " +
                    "replacement for the long press gesture, which only an Xposed module can hook.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LogBox(log)

        SectionCard("When it does not work") {
            Text(
                "If the session does not appear, Google has disabled Circle to Search for this " +
                    "model ('Omni invocation failed: not enabled' in logcat). That is only " +
                    "bypassable with root, LSPosed and a device spoof. Without root it stops here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

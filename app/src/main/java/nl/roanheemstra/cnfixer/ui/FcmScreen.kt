package nl.roanheemstra.cnfixer.ui

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
import androidx.compose.material3.CircularProgressIndicator
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
import nl.roanheemstra.cnfixer.fcm.FcmApp
import nl.roanheemstra.cnfixer.fcm.FcmAppScanner
import nl.roanheemstra.cnfixer.fcm.FcmEnforcer
import nl.roanheemstra.cnfixer.fcm.FcmVerifier
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

@Composable
fun FcmScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<FcmApp>>(emptyList()) }
    var checks by remember { mutableStateOf<List<FcmVerifier.Check>>(emptyList()) }

    LaunchedEffect(Unit) { ShizukuState.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!ShizukuState.granted) {
            Text(
                "Shizuku access is missing. Everything below stays disabled until you start " +
                    "Shizuku and grant access in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SectionCard("Repair") {
            Text(
                "Puts com.google.android.gms back on the no-restrict list and removes the " +
                    "Greezer limiter. This is what HyperOS reverts after a reboot or an OTA.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !busy && ShizukuState.granted,
                onClick = {
                    busy = true
                    scope.launch {
                        val report = withContext(Dispatchers.IO) { FcmEnforcer.runCorePass() }
                        log = buildString {
                            report.steps.forEach { step ->
                                append(if (step.result.ok) "OK   " else "FAIL ")
                                append(step.label)
                                append(" -> ")
                                append(step.result.summary())
                                append('\n')
                            }
                        }
                        ShizukuState.refresh()
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Run repair")
            }
        }

        SectionCard("Verify") {
            Text(
                "Reads the values back from the device. A green check means the change is " +
                    "actually in place, not just that a command exited with zero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                enabled = !busy && ShizukuState.granted,
                onClick = {
                    busy = true
                    scope.launch {
                        checks = withContext(Dispatchers.IO) { FcmVerifier.run() }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Run checks")
            }
            if (checks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                checks.forEach { check ->
                    StatusRow(
                        label = check.label,
                        value = if (check.ok) "pass" else "fail",
                        level = if (check.ok) StatusLevel.OK else StatusLevel.ERROR,
                    )
                    Text(
                        check.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionCard("Apps with push") {
            Text(
                "Only apps that declare an FCM receiver. Autostart on means HyperOS may start " +
                    "the process when a push arrives. Leave battery on Optimized unless an app " +
                    "keeps missing notifications.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        apps = withContext(Dispatchers.IO) { FcmAppScanner.scan(context) }
                        log = "Found ${apps.size} apps."
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scan apps")
            }
            if (apps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                apps.forEach { app ->
                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (busy) CircularProgressIndicator()

        LogBox(log)
    }
}

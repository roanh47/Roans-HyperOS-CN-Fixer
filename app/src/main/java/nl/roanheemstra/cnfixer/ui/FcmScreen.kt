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
import androidx.compose.runtime.collectAsState
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
import nl.roanheemstra.cnfixer.fcm.MilletList
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.shizuku.ShellRunner
import nl.roanheemstra.cnfixer.shizuku.ShizukuState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mirrors the wording of the reference app: protection is applied periodically,
 * not once. "Apply now" runs the same pass the background task runs.
 */
@Composable
fun FcmScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SafStore(context) }

    val lastRun by store.lastFcmRun.collectAsState(initial = 0L)
    val lastOk by store.lastFcmOk.collectAsState(initial = false)

    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<FcmApp>>(emptyList()) }
    var checks by remember { mutableStateOf<List<FcmVerifier.Check>>(emptyList()) }
    var exempt by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refreshExemptList() {
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                ShellRunner.exec("settings", "get", "system", MilletList.KEY)
            }
            exempt = if (raw.ok) MilletList.parse(raw.out) else emptyList()
        }
    }

    LaunchedEffect(Unit) {
        ShizukuState.refresh()
        refreshExemptList()
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
                "Shizuku access is missing. Everything below stays disabled until you start " +
                    "Shizuku and grant access in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SectionCard("Protection") {
            StatusRow(
                label = "Google Play services",
                value = if (lastRun > 0L && lastOk) "protected" else "not verified",
                level = if (lastRun > 0L && lastOk) StatusLevel.OK else StatusLevel.WARN,
            )
            StatusRow(
                label = "Last check",
                value = if (lastRun == 0L) {
                    "never"
                } else {
                    val stamp = SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH).format(Date(lastRun))
                    (if (lastOk) "successful " else "failed ") + stamp
                },
                level = if (lastRun > 0L && lastOk) StatusLevel.OK else StatusLevel.WARN,
            )
            Text(
                "HyperOS reverts these values after every reboot and after an OTA. That is why " +
                    "the protection is re-applied on a schedule instead of once, and why the date " +
                    "above moves while the phone is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Automatic enforcement") {
            StatusRow(
                label = "Frequency",
                value = "every 15 minutes",
                level = if (ShizukuState.granted) StatusLevel.OK else StatusLevel.WARN,
            )
            Text(
                "The background task repeats the same pass. Without it HyperOS quietly stops " +
                    "FCM again within an hour. Apply now runs that pass immediately, it does not " +
                    "replace the schedule.",
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
                        // The status badge follows the device state, not the exit codes.
                        val verified = withContext(Dispatchers.IO) {
                            val results = FcmVerifier.run()
                            results.isNotEmpty() && results.all { it.ok }
                        }
                        withContext(Dispatchers.IO) { store.recordFcmRun(verified) }
                        log = buildString {
                            report.steps.forEach { step ->
                                append(if (step.result.ok) "OK   " else "FAIL ")
                                append(step.label)
                                append(" -> ")
                                append(step.result.summary())
                                append('\n')
                            }
                        }
                        refreshExemptList()
                        ShizukuState.refresh()
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply now")
            }
        }

        SectionCard("Exempt from HyperOS restrictions") {
            Text(
                "MILLET_NO_RESTRICT_APP, the list HyperOS reads to decide what may keep running.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            StatusRow(
                label = "Entries",
                value = exempt.size.toString(),
                level = if (exempt.isNotEmpty()) StatusLevel.OK else StatusLevel.WARN,
            )
            StatusRow(
                label = "Google Play services",
                value = if (MilletList.GMS in exempt) "in the list" else "missing",
                level = if (MilletList.GMS in exempt) StatusLevel.OK else StatusLevel.ERROR,
            )
            if (exempt.isNotEmpty()) {
                Text(
                    exempt.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                enabled = !busy && ShizukuState.granted,
                onClick = { refreshExemptList() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Refresh status")
            }
        }

        SectionCard("Check status") {
            Text(
                "Reads the values back from the device. A pass means the change is in place right " +
                    "now, not that a command exited with zero.",
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

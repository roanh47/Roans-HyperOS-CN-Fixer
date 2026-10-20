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
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

@Composable
fun FcmScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<FcmApp>>(emptyList()) }

    LaunchedEffect(Unit) { ShizukuState.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("FCM", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Status") {
            StatusRow(
                label = "Shizuku",
                value = if (ShizukuState.available) "actief" else "niet actief",
                level = if (ShizukuState.available) StatusLevel.OK else StatusLevel.ERROR,
            )
            StatusRow(
                label = "Rechten",
                value = if (ShizukuState.granted) "gegeven" else "ontbreken",
                level = if (ShizukuState.granted) StatusLevel.OK else StatusLevel.WARN,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Zonder Shizuku kan deze app niets aanpassen. Installeer Shizuku, start hem " +
                    "via wireless debugging en geef rechten. Na een reboot moet Shizuku opnieuw starten.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Herstel") {
            Text(
                "Zet com.google.android.gms terug in de no-restrict lijst en haalt de " +
                    "Greezer-limiter weg. Dit is wat HyperOS na een reboot of OTA terugdraait.",
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
                Text("Nu toepassen")
            }
        }

        SectionCard("Apps met push") {
            Text(
                "Alleen apps die een FCM-receiver hebben. Autostart aan betekent dat HyperOS " +
                    "het proces mag starten als er een push binnenkomt. Laat de batterij op " +
                    "Optimized staan tenzij een app echt blijft haperen.",
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
                        log = "Gevonden: ${apps.size} apps."
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apps scannen")
            }
            if (apps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                apps.forEach { app ->
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = app.packageName,
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

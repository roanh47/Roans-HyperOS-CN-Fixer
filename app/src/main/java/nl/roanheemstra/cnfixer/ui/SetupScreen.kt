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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import nl.roanheemstra.cnfixer.PowerHelper
import nl.roanheemstra.cnfixer.shizuku.ShellRunner
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

/**
 * Setup guide, shown on first launch and replayable from Settings without
 * touching what the user already configured.
 */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    var ignoringBattery by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        ShizukuState.refresh()
        ignoringBattery = PowerHelper.isIgnoringBatteryOptimizations(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Setup", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Five steps. Shizuku is the only hard requirement: it gives this app ADB shell " +
                "access without root.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard("1. Install Shizuku") {
            StatusRow(
                label = "Installed",
                value = if (ShizukuState.installed) "yes" else "no",
                level = if (ShizukuState.installed) StatusLevel.OK else StatusLevel.WARN,
            )
            Text(
                "Shizuku is a separate app. This fixer only talks to it, it does not bundle it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { ShellRunner.openShizuku(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ShizukuState.installed) "Open Shizuku" else "Get Shizuku") }
        }

        SectionCard("2. Start the Shizuku service") {
            StatusRow(
                label = "Service",
                value = if (ShizukuState.available) "running" else "not running",
                level = if (ShizukuState.available) StatusLevel.OK else StatusLevel.ERROR,
            )
            Text(
                "Developer options, enable Wireless debugging, then start Shizuku from inside " +
                    "the Shizuku app. Repeat this after every reboot: without root the service " +
                    "does not start by itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("3. Grant access") {
            StatusRow(
                label = "Shell access",
                value = if (ShizukuState.granted) "granted" else "not granted",
                level = if (ShizukuState.granted) StatusLevel.OK else StatusLevel.WARN,
            )
            Text(
                "Tap the button and confirm the dialog that Shizuku shows. No dialog means the " +
                    "service is not running yet, go back to step 2.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = ShizukuState.available && !ShizukuState.granted,
                onClick = { ShizukuState.requestPermission() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Grant access") }
        }

        SectionCard("4. No battery restrictions") {
            StatusRow(
                label = "Unrestricted",
                value = if (ignoringBattery) "yes" else "no",
                level = if (ignoringBattery) StatusLevel.OK else StatusLevel.WARN,
            )
            Text(
                "HyperOS freezes apps between runs. Allowing unrestricted battery keeps the " +
                    "periodic work alive. The app does not need to stay open for that.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    PowerHelper.requestIgnoreBatteryOptimizations(context)
                    refreshTick++
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow unrestricted battery") }
        }

        SectionCard("5. Autostart allowed") {
            Text(
                "In the Xiaomi security app, set Autostart to on for CN Fixer. That is what lets " +
                    "the recovery run after a reboot. This screen cannot read that value, so check " +
                    "it yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { PowerHelper.openAutostart(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open autostart settings") }
        }

        OutlinedButton(
            onClick = { refreshTick++ },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Refresh status") }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }

        Spacer(Modifier.height(8.dp))
    }
}

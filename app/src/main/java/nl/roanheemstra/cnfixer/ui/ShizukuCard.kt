package nl.roanheemstra.cnfixer.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import nl.roanheemstra.cnfixer.shizuku.ShellRunner
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

/**
 * Status card with the one action that was missing before: asking Shizuku
 * for access. Shizuku shows its own dialog, so the request only makes sense
 * while its service is running.
 */
@Composable
fun ShizukuCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ShizukuState.refresh() }

    val running = ShizukuState.available
    val granted = ShizukuState.granted
    val installed = ShizukuState.installed

    SectionCard("Shizuku", modifier) {
        StatusRow(
            label = "Service",
            value = if (running) "running" else "not running",
            level = if (running) StatusLevel.OK else StatusLevel.ERROR,
        )
        StatusRow(
            label = "Shell access",
            value = if (granted) "granted" else "not granted",
            level = if (granted) StatusLevel.OK else StatusLevel.WARN,
        )
        if (!running) {
            StatusRow(
                label = "App installed",
                value = if (installed) "yes" else "no",
                level = if (installed) StatusLevel.WARN else StatusLevel.ERROR,
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            granted -> Text(
                "Shizuku is ready. Commands run as the ADB shell user.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            running -> {
                Text(
                    "Shizuku is running but this app has no access yet. Tap Grant access " +
                        "and confirm the Shizuku dialog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { ShizukuState.requestPermission() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Grant access") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { ShellRunner.openShizuku(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open Shizuku") }
            }

            else -> {
                Text(
                    if (installed) {
                        "Shizuku is installed but its service is not running. Start it from the " +
                            "Shizuku app, then come back: the permission dialog appears by itself."
                    } else {
                        "Shizuku is not installed. Install it, start the service, then come back."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { ShellRunner.openShizuku(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (installed) "Open Shizuku" else "Get Shizuku") }
            }
        }
    }
}

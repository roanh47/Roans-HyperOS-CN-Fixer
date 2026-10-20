package nl.roanheemstra.cnfixer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

enum class FixId(val title: String, val summary: String) {
    FCM(
        title = "Google Play services protection",
        summary = "Re-applied every 15 minutes, because HyperOS resets it after a reboot",
    ),
    SCREENSHOTS(
        title = "Screenshot mover",
        summary = "Moves DCIM/Screenshots to Pictures/Screenshots every 15 minutes",
    ),
    CIRCLE_TO_SEARCH(
        title = "Circle to Search",
        summary = "Built in, trigger it from the app or from a Quick Settings tile",
    ),
}

@Composable
fun FixesScreen(
    onOpen: (FixId) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LaunchedEffect(Unit) { ShizukuState.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Fixes", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pick a fix to open it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!ShizukuState.granted) {
            SurfaceRow(
                title = if (ShizukuState.available) {
                    "Shizuku access missing"
                } else {
                    "Shizuku is not running"
                },
                summary = "Fixes run shell commands through Shizuku. Open Settings to start it and grant access.",
                highlighted = true,
                onClick = onOpenSettings,
            )
        }

        FixId.entries.forEach { fix ->
            SurfaceRow(
                title = fix.title,
                summary = fix.summary,
                onClick = { onOpen(fix) },
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SurfaceRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlighted) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "\u203A",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

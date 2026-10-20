package nl.roanheemstra.cnfixer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InfoScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Over deze app", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Wat hij doet") {
            Text(
                "Lost twee import-problemen van een Xiaomi China-ROM op: wegvallende " +
                    "FCM-notificaties en screenshots die in DCIM blijven staan. Daarnaast " +
                    "kan hij Circle to Search aanzetten via MiCTS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Wat hij NIET kan oplossen") {
            Text(
                "Android Auto — de system stubs ontbreken; een APK installeren is niet genoeg.\n\n" +
                    "Google Wallet / NFC betalen — Play Services herkent de Chinese SKU en zet " +
                    "TapAndPay uit. Geen app-oplossing.\n\n" +
                    "Power-knop naar Gemini — power-key events gaan naar het systeem; geen " +
                    "enkele gewone app kan die onderscheppen.\n\n" +
                    "LTE Band 20 — hardware. Ontbreekt op veel CN-modellen, geeft " +
                    "signaalzoeken en accudrain.\n\n" +
                    "eSIM — niet ondersteund op CN-modellen.\n\n" +
                    "Bootloader unlock — bij CN-modellen praktisch dicht.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Credits en licentie") {
            Text(
                "GPL-3.0. De FCM-mechanismen zijn publiek gedocumenteerd door " +
                    "dingwen07/hyperos-fcm-fix.\n\n" +
                    "Circle to Search via parallelcc/MiCTS (GPL-3.0) — wordt niet " +
                    "meegebundeld, alleen als aparte app aangeroepen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Versie") {
            Text(
                "0.1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package nl.roanheemstra.cnfixer.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import nl.roanheemstra.cnfixer.BuildConfig
import nl.roanheemstra.cnfixer.PowerHelper
import nl.roanheemstra.cnfixer.fcm.NotificationTest
import nl.roanheemstra.cnfixer.shizuku.ShizukuState

@Composable
fun SettingsScreen(onStartSetup: () -> Unit) {
    val context = LocalContext.current
    var ignoringBattery by remember { mutableStateOf(false) }
    var notificationPermission by remember { mutableStateOf(NotificationTest.hasPermission(context)) }
    var notificationLog by remember { mutableStateOf("") }
    var refreshTick by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermission = granted
        notificationLog = if (granted) "Notification permission granted." else "Notification permission denied."
    }

    LaunchedEffect(refreshTick) {
        ShizukuState.refresh()
        ignoringBattery = PowerHelper.isIgnoringBatteryOptimizations(context)
        notificationPermission = NotificationTest.hasPermission(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        ShizukuCard()

        SectionCard("Setup") {
            Text(
                "Replays the setup guide. Nothing you configured here is reset, so it is safe " +
                    "to use while debugging.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStartSetup, modifier = Modifier.fillMaxWidth()) {
                Text("Start setup")
            }
        }

        SectionCard("Notifications") {
            StatusRow(
                label = "Permission",
                value = if (notificationPermission) "granted" else "not granted",
                level = if (notificationPermission) StatusLevel.OK else StatusLevel.WARN,
            )
            Text(
                "This sends a local notification: it proves the channel, the permission and Do " +
                    "Not Disturb behave. It is not an FCM push. For a real push this app needs its " +
                    "own Firebase project with a service account, because a push can only be sent " +
                    "to a token that belongs to you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (notificationPermission) {
                OutlinedButton(
                    onClick = { notificationLog = NotificationTest.send(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send test notification") }
            } else {
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow notifications") }
            }
            LogBox(notificationLog)
        }

        SectionCard("Keeping the app alive") {
            StatusRow(
                label = "Unrestricted battery",
                value = if (ignoringBattery) "yes" else "no",
                level = if (ignoringBattery) StatusLevel.OK else StatusLevel.WARN,
            )
            Text(
                "The scheduled work runs through WorkManager, so the app does not need to stay " +
                    "open. HyperOS still freezes apps between runs unless battery is unrestricted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    PowerHelper.requestIgnoreBatteryOptimizations(context)
                    refreshTick++
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow unrestricted battery") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { PowerHelper.openAutostart(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Autostart settings") }
        }

        SectionCard("What this app does") {
            Text(
                "Fixes two import problems on a Xiaomi China ROM: dropped FCM notifications " +
                    "and screenshots that stay in DCIM. It also triggers Circle to Search itself, " +
                    "so there is no second app to install.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("What it cannot fix") {
            Text(
                "Android Auto: the system stubs are missing, installing an APK is not enough.\n\n" +
                    "Google Wallet and NFC payments: Play Services detects the Chinese SKU and " +
                    "turns TapAndPay off. No app-side workaround.\n\n" +
                    "Power button to Gemini: power-key events go to the system, no regular app " +
                    "can intercept them.\n\n" +
                    "LTE Band 20: hardware. Missing on many CN models, causes weak signal and " +
                    "battery drain.\n\n" +
                    "eSIM: not supported on CN models.\n\n" +
                    "Bootloader unlock: effectively closed on CN models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Credits and license") {
            Text(
                "GPL-3.0. The FCM mechanisms are publicly documented by dingwen07/hyperos-fcm-fix.\n\n" +
                    "The Circle to Search trigger follows the approach of parallelcc/MiCTS " +
                    "(GPL-3.0). No code from it is bundled: this app calls the same hidden voice " +
                    "interaction API on its own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Version") {
            Text(
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

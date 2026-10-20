package nl.roanheemstra.cnfixer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.roanheemstra.cnfixer.BuildConfig
import nl.roanheemstra.cnfixer.PowerHelper
import nl.roanheemstra.cnfixer.fcm.NotificationTest
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.shizuku.ShizukuState
import nl.roanheemstra.cnfixer.update.ApkInstaller
import nl.roanheemstra.cnfixer.update.UpdateChecker
import java.io.File

@Composable
fun SettingsScreen(onStartSetup: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SafStore(context) }

    var ignoringBattery by remember { mutableStateOf(false) }
    var notificationPermission by remember { mutableStateOf(NotificationTest.hasPermission(context)) }
    var notificationLog by remember { mutableStateOf("") }
    var refreshTick by remember { mutableStateOf(0) }

    val notifyScreenshots by store.notifyScreenshots.collectAsState(initial = true)
    val notifyGms by store.notifyGms.collectAsState(initial = true)

    var checking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var latest by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var readyApk by remember { mutableStateOf<File?>(null) }

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
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
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

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Screenshots", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Says when files were moved or when a move failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = notifyScreenshots,
                    onCheckedChange = { scope.launch { store.setNotifyScreenshots(it) } },
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Google Play services", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Warns when the notification protection breaks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = notifyGms,
                    onCheckedChange = { scope.launch { store.setNotifyGms(it) } },
                )
            }

            Spacer(Modifier.height(12.dp))
            LinkRow(
                title = "System notification settings",
                summary = "Opens the device page for this app",
                onClick = { openSystemNotificationSettings(context) },
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

        SectionCard("Updates") {
            StatusRow(
                label = "Installed",
                value = BuildConfig.VERSION_NAME,
                level = StatusLevel.OK,
            )

            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !checking && !downloading,
                onClick = {
                    checking = true
                    updateMessage = "Checking..."
                    latest = null
                    readyApk = null
                    scope.launch {
                        val release = withContext(Dispatchers.IO) { UpdateChecker.latest() }
                        checking = false
                        updateMessage = when {
                            release == null -> "Could not reach GitHub. Try again."
                            release.apkUrl == null -> "The latest release has no APK."
                            UpdateChecker.isNewer(release.version, BuildConfig.VERSION_NAME) -> {
                                latest = release
                                "Update available: ${release.version}"
                            }

                            else -> "You are on the latest version (${release.version})."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (checking) "Checking..." else "Check for updates") }

            val update = latest
            if (update != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${update.tag}  " + if (update.sizeBytes > 0) {
                        "${update.sizeBytes / (1024 * 1024)} MB"
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (update.notes.isNotBlank()) {
                    Text(
                        update.notes.take(600),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = !downloading,
                    onClick = {
                        val url = update.apkUrl ?: return@Button
                        if (!ApkInstaller.canInstall(context)) {
                            ApkInstaller.openInstallPermission(context)
                            updateMessage =
                                "Allow this app to install apps, then tap install again."
                            return@Button
                        }
                        downloading = true
                        progress = 0
                        updateMessage = "Downloading..."
                        scope.launch {
                            val file = ApkInstaller.download(context, url) { progress = it }
                            downloading = false
                            if (file == null) {
                                updateMessage = "Download failed."
                            } else {
                                readyApk = file
                                val opened = ApkInstaller.install(context, file)
                                updateMessage = if (opened) {
                                    "Installer opened. Confirm to update."
                                } else {
                                    "Could not open the installer."
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            downloading -> "Downloading $progress%"
                            readyApk != null -> "Install ${update.version}"
                            else -> "Download and install ${update.version}"
                        }
                    )
                }
            }

            LogBox(updateMessage)
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

@Composable
private fun LinkRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
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

private fun openSystemNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
}

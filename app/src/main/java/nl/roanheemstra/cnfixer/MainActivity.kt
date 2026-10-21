package nl.roanheemstra.cnfixer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.roanheemstra.cnfixer.fcm.FcmStatusService
import nl.roanheemstra.cnfixer.mover.MoverScheduler
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.shizuku.ShellRunner
import nl.roanheemstra.cnfixer.shizuku.ShizukuState
import nl.roanheemstra.cnfixer.ui.CnFixerTheme
import nl.roanheemstra.cnfixer.ui.FixHost
import nl.roanheemstra.cnfixer.ui.FixId
import nl.roanheemstra.cnfixer.ui.FixesScreen
import nl.roanheemstra.cnfixer.ui.SettingsScreen
import nl.roanheemstra.cnfixer.ui.SetupScreen
import nl.roanheemstra.cnfixer.worker.Scheduler
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    /** Compose state so the first-run guide can be shown without blocking launch. */
    private var showSetup by mutableStateOf(false)

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == ShellRunner.REQUEST_CODE) {
                ShizukuState.refresh()
            }
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        // Shizuku was started while this app is open: ask right away.
        ShizukuState.autoRequestOnce()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        ShizukuState.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        runCatching { Shizuku.addBinderReceivedListener(binderReceivedListener) }
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
        ShellRunner.init(this)
        ShizukuState.refresh()

        val store = SafStore(this)
        lifecycleScope.launch {
            // First launch: walk through the setup. Afterwards only on request.
            showSetup = !store.setupDone.first()
            runCatching { Scheduler.syncFromPrefs(this@MainActivity) }
            if (store.moverAuto.first()) {
                runCatching { MoverScheduler.schedule(this@MainActivity) }
            }
            if (store.statusNotification.first()) {
                runCatching { FcmStatusService.start(this@MainActivity) }
            }
        }

        setContent {
            CnFixerTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var openFixName by rememberSaveable { mutableStateOf<String?>(null) }
                val openFix = openFixName?.let { name ->
                    FixId.entries.firstOrNull { it.name == name }
                }

                BackHandler(enabled = openFix != null) { openFixName = null }

                val setupVisible = showSetup

                BackHandler(enabled = setupVisible) {
                    lifecycleScope.launch { store.setSetupDone(true) }
                    showSetup = false
                }

                Scaffold(
                    bottomBar = {
                        if (!setupVisible) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = tab == 0,
                                    onClick = {
                                        tab = 0
                                        openFixName = null
                                    },
                                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                                    label = { Text(stringResource(R.string.tab_fixes)) },
                                )
                                NavigationBarItem(
                                    selected = tab == 1,
                                    onClick = {
                                        tab = 1
                                        openFixName = null
                                    },
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    label = { Text(stringResource(R.string.tab_settings)) },
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when {
                            setupVisible -> SetupScreen(
                                onDone = {
                                    lifecycleScope.launch { store.setSetupDone(true) }
                                    showSetup = false
                                }
                            )

                            openFix != null -> FixHost(openFix) { openFixName = null }

                            tab == 0 -> FixesScreen(
                                onOpen = { openFixName = it.name },
                                onOpenSettings = { tab = 1 },
                            )

                            else -> SettingsScreen(onStartSetup = { showSetup = true })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Covers coming back from the Shizuku app: refresh, then ask once.
        ShizukuState.autoRequestOnce()
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
        super.onDestroy()
    }
}

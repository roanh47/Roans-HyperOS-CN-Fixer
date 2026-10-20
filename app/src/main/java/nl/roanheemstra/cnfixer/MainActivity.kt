package nl.roanheemstra.cnfixer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import nl.roanheemstra.cnfixer.shizuku.ShizukuState
import nl.roanheemstra.cnfixer.shizuku.ShellRunner
import nl.roanheemstra.cnfixer.ui.CnFixerTheme
import nl.roanheemstra.cnfixer.ui.FcmScreen
import nl.roanheemstra.cnfixer.ui.InfoScreen
import nl.roanheemstra.cnfixer.ui.MoverScreen
import nl.roanheemstra.cnfixer.ui.SearchScreen
import nl.roanheemstra.cnfixer.worker.Scheduler
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == nl.roanheemstra.cnfixer.shizuku.ShellRunner.REQUEST_CODE) {
                ShizukuState.refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        ShellRunner.init(this)
        ShizukuState.refresh()
        runCatching { Scheduler.ensureScheduled(this) }

        setContent {
            CnFixerTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_fcm)) },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_mover)) },
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_search)) },
                            )
                            NavigationBarItem(
                                selected = tab == 3,
                                onClick = { tab = 3 },
                                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_info)) },
                            )
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (tab) {
                            0 -> FcmScreen()
                            1 -> MoverScreen()
                            2 -> SearchScreen()
                            else -> InfoScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuState.refresh()
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        super.onDestroy()
    }
}

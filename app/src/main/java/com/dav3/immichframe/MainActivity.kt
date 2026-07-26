package com.dav3.immichframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dav3.immichframe.domain.system.isDefaultLauncher
import com.dav3.immichframe.domain.system.openLauncherSettings
import com.dav3.immichframe.ui.nav.ImmichNavHost
import com.dav3.immichframe.ui.settings.SettingsViewModel
import com.dav3.immichframe.ui.theme.ImmichFrameTheme
import com.dav3.immichframe.ui.update.UpdateDialog
import com.dav3.immichframe.ui.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImmichFrameTheme {
                val updateVm: UpdateViewModel = hiltViewModel()
                val updateState by updateVm.updateState.collectAsState()
                val dismissed by updateVm.updateDismissed.collectAsState()

                val settingsVm: SettingsViewModel = hiltViewModel()
                val settingsState by settingsVm.uiState.collectAsState()
                val s = settingsState.settings

                // ---- Launcher-monitor: detect if another app became the
                // default Home while launcher mode is enabled. ----
                var showLauncherLostDialog by remember { mutableStateOf(false) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(s.launcherMode) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && s.launcherMode) {
                            // Check on every resume (covers returning from the
                            // home-chooser, task switch, etc.)
                            showLauncherLostDialog = !isDefaultLauncher(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                ImmichNavHost()

                // Update check on startup (non-blocking, background download)
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    updateVm.checkForUpdate()
                }

                // Show update dialog when download is ready
                if (updateState.available && !updateState.downloading && updateState.downloadedApkPath != null && !dismissed) {
                    UpdateDialog(
                        state = updateState,
                        onInstall = { updateVm.installUpdate() },
                        onDismiss = { updateVm.dismissUpdate() },
                    )
                }

                // Launcher-lost dialog: another app is the default Home while
                // launcher mode is enabled.
                if (showLauncherLostDialog) {
                    AlertDialog(
                        onDismissRequest = { showLauncherLostDialog = false },
                        title = { Text(getString(R.string.launcher_lost_title)) },
                        text = { Text(getString(R.string.launcher_lost_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showLauncherLostDialog = false
                                openLauncherSettings(this@MainActivity)
                            }) {
                                Text(getString(R.string.set_as_launcher))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLauncherLostDialog = false }) {
                                Text(getString(android.R.string.cancel))
                            }
                        },
                    )
                }
            }
        }
    }
}

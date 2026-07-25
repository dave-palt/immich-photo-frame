package com.dav3.immichframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.dav3.immichframe.ui.nav.ImmichNavHost
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
            }
        }
    }
}

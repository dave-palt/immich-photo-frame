package com.dav3.immichframe.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.dav3.immichframe.data.update.UpdateState

@Composable
fun UpdateDialog(
    state: UpdateState,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Available") },
        text = {
            Text(
                "A new build has been downloaded and is ready to install.\n\n" +
                    "Version: ${state.newVersion.take(14)}…",
            )
        },
        confirmButton = {
            TextButton(onClick = onInstall) {
                Text("Install", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}

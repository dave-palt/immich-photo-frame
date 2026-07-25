package com.dav3.immichframe.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dav3.immichframe.R
import com.dav3.immichframe.data.update.UpdateState

@Composable
fun UpdateDialog(
    state: UpdateState,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available)) },
        text = {
            Text(
                stringResource(R.string.update_message, state.newVersion.take(14)),
            )
        },
        confirmButton = {
            TextButton(onClick = onInstall) {
                Text(stringResource(R.string.install), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.later))
            }
        },
    )
}

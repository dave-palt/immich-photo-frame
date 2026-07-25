package com.dav3.immichframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dav3.immichframe.data.local.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val START_ON_BOOT_KEY = stringPreferencesKey("start_on_boot")

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = context.appDataStore.data
                    .first()[START_ON_BOOT_KEY]?.toBoolean() ?: false

                if (enabled) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIntent?.let { context.startActivity(it) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

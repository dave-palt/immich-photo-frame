package com.dav3.immichframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dav3.immichframe.data.local.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val START_ON_BOOT_KEY = stringPreferencesKey("start_on_boot")
private val BOOT_VERIFIED_KEY = stringPreferencesKey("boot_verified")

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = context.appDataStore.data
                    .first()[START_ON_BOOT_KEY]?.toBoolean() ?: false

                if (enabled) {
                    context.appDataStore.edit { it[BOOT_VERIFIED_KEY] = "true" }

                    // Starting an Activity from a background BroadcastReceiver requires
                    // SYSTEM_ALERT_WINDOW on Android 10+ (Background Activity Launch
                    // restriction). If the permission isn't granted the launch is silently
                    // blocked by the OS, so skip the startActivity call rather than log a
                    // misleading BAL-denial warning. The Settings screen prompts the user
                    // to grant the permission when Start on Boot is enabled.
                    if (Settings.canDrawOverlays(context)) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        launchIntent?.let { context.startActivity(it) }
                    }
                } else {
                    context.appDataStore.edit { it[BOOT_VERIFIED_KEY] = "false" }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

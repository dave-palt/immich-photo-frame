package com.dav3.immichframe.domain.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings

/**
 * OEM-specific autostart permission helpers.
 *
 * Stock Android (Pixel, Motorola) and Samsung don't restrict boot launch.
 * Chinese OEMs (Xiaomi, Oppo, Vivo, Huawei, Honor, etc.) add an extra
 * autostart management layer that silently blocks [Intent.ACTION_BOOT_COMPLETED]
 * from reaching the app unless the user has explicitly granted permission.
 */
internal fun needsBootPermission(): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return manufacturer in setOf(
        "xiaomi", "redmi", "oppo", "oplus", "vivo", "iqoo",
        "honor", "huawei", "realme", "oneplus", "letv",
        "tecno", "infinix", "asus",
    )
}

/**
 * Returns true if the app holds the [android.Manifest.permission.SYSTEM_ALERT_WINDOW]
 * ("Display over other apps") permission.
 *
 * Since Android 10 (API 29), starting an Activity from a background component
 * (such as a BOOT_COMPLETED receiver) is blocked by the Background Activity
 * Launch (BAL) restriction unless the app holds SAW. See:
 * https://developer.android.com/guide/components/activities/secure-bal
 */
internal fun hasOverlayPermission(context: Context): Boolean = AndroidSettings.canDrawOverlays(context)

/**
 * Opens the system "Display over other apps" settings screen for this app.
 * Falls back to the generic app-details page if the intent fails.
 */
internal fun openOverlayPermissionSettings(context: Context) {
    try {
        val intent = Intent(
            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: Exception) {
            context.startActivity(
                Intent(AndroidSettings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}

/** All known autostart-setting components per OEM family. */
private fun autostartCandidates(manufacturer: String): List<ComponentName> {
    val oplus = listOf(
        // ColorOS 15 / Oplus (newest)
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
        // ColorOS 13–14
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        // ColorOS ≤12
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        // OnePlus (OxygenOS, pre-merge)
        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        // Realme (also BBK)
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
    )
    return when (manufacturer) {
        "xiaomi", "redmi" -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"),
        )
        "oppo", "oplus", "oneplus", "realme" -> oplus
        "vivo", "iqoo" -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        )
        "honor", "huawei" -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        "asus" -> listOf(
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        )
        "samsung" -> listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        else -> emptyList()
    }
}

/**
 * Deep-link to the device's autostart management screen, falling back to
 * the generic app-details page if no OEM-specific activity resolves.
 */
internal fun openBootPermissionSettings(context: Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val pm = context.packageManager

    // Try each candidate — use the first that actually resolves
    for (candidate in autostartCandidates(manufacturer)) {
        val resolved = pm.resolveActivity(
            Intent().apply { component = candidate },
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )
        if (resolved != null) {
            try {
                val intent = Intent().apply {
                    component = candidate
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Asus needs an extra to jump to the right fragment
                    if (manufacturer == "asus") {
                        putExtra("showFragment", "com.asus.mobilemanager.autostart.AutoStartActivity")
                    }
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // resolved but won't launch — try next candidate
            }
        }
    }

    // Fallback: app details page
    try {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: Exception) {
        context.startActivity(
            Intent(AndroidSettings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

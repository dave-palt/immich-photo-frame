package com.dav3.immichframe.domain.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.dav3.immichframe.BuildConfig
import android.provider.Settings as AndroidSettings

/**
 * Manages launcher-mode (Home replacement) for the app.
 *
 * When enabled, the app declares itself as a Home launcher via an
 * [activity-alias][android.content.pm.ActivityInfo] in the manifest. The
 * system then always launches the app on boot and when the Home button is
 * pressed — no BOOT_COMPLETED broadcast or autostart permission required.
 * This is the most reliable boot method on Chinese OEM ROMs (OPPO/Realme/
 * Xiaomi/etc.) that silently block boot broadcasts.
 */
private const val NAMESPACE = "com.dav3.immichframe"

private val launcherAliasComponent =
    ComponentName(
        // packageName = applicationId (e.g. com.dav3.immichframe or .debug)
        BuildConfig.APPLICATION_ID,
        // class = namespace + ".LauncherAlias" — the manifest declares
        // android:name=".LauncherAlias" which resolves relative to the
        // namespace (com.dav3.immichframe), NOT the applicationId suffix.
        "$NAMESPACE.LauncherAlias",
    )

/**
 * Enables or disables the launcher-mode activity-alias. Returns true if the
 * component state actually changed.
 */
internal fun setLauncherModeEnabled(context: Context, enabled: Boolean) {
    val pm = context.packageManager
    val newState =
        if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
    pm.setComponentEnabledSetting(
        launcherAliasComponent,
        newState,
        PackageManager.DONT_KILL_APP,
    )
}

/** Returns true if the launcher-mode activity-alias is currently enabled. */
internal fun isLauncherModeEnabled(context: Context): Boolean {
    val pm = context.packageManager
    val state = pm.getComponentEnabledSetting(launcherAliasComponent)
    return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
        state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
}

/** Returns true if this app is currently set as the system's default Home. */
internal fun isDefaultLauncher(context: Context): Boolean {
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val defaultLauncher =
        context.packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
    return defaultLauncher?.activityInfo?.packageName == context.packageName
}

/**
 * Opens the system Home / launcher settings screen
 * ([android.provider.Settings.ACTION_HOME_SETTINGS]). From there the user can
 * set a different default launcher or re-select this app.
 */
internal fun openLauncherSettings(context: Context) {
    val intent =
        Intent(AndroidSettings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback: generic settings
        context.startActivity(
            Intent(AndroidSettings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

/**
 * Launches another Home launcher (i.e. actually switches away from this app to
 * a different launcher so the user can access other apps). Queries all apps
 * that can handle the HOME intent, excludes this app, and:
 *  - if exactly one other launcher exists → launches it directly;
 *  - if several exist → shows a system chooser;
 *  - if none exist → falls back to [openLauncherSettings].
 */
internal fun openOtherLauncher(context: Context) {
    val pm = context.packageManager
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val others =
        pm.queryIntentActivities(homeIntent, 0)
            .filter { it.activityInfo.packageName != context.packageName }

    if (others.isEmpty()) {
        openLauncherSettings(context)
        return
    }

    if (others.size == 1) {
        val info = others[0].activityInfo
        val launch =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                setClassName(info.packageName, info.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(launch)
            return
        } catch (_: Exception) {
            // fall through to chooser
        }
    }

    // Multiple other launchers: show a chooser listing all Home apps.
    val chooser =
        Intent.createChooser(homeIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    try {
        context.startActivity(chooser)
    } catch (_: Exception) {
        openLauncherSettings(context)
    }
}

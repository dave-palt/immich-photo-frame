package com.dav3.immichframe.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.dav3.immichframe.BuildConfig
import com.dav3.immichframe.data.remote.GitHubApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateState(
    val available: Boolean = false,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadEtaSeconds: Long = 0L,
    val error: String? = null,
    val downloadedApkPath: File? = null,
    val newVersion: String = "",
    val releaseNotes: String = "",
)

@Singleton
class UpdateManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "UpdateManager"
    }
    private val json = Json { ignoreUnknownKeys = true }

    private val api: GitHubApi =
        Retrofit
            .Builder()
            .baseUrl("https://api.github.com/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val updateDir by lazy {
        File(context.cacheDir, "updates").apply { mkdirs() }
    }

    /**
     * Returns true if installed via Play Store (should hide self-update UI).
     */
    fun isInstalledFromPlayStore(): Boolean = try {
        val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.packageManager
                .getInstallSourceInfo(context.packageName)
                .installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager
                .getInstallerPackageName(context.packageName)
        }
        installer == "com.android.vending"
    } catch (_: Exception) {
        false
    }

    /**
     * Returns true if the user has granted "Install unknown apps" permission
     * (REQUEST_INSTALL_PACKAGES). If false, the installer intent will silently
     * fail with "App not installed".
     *
     * On pre-O (API < 26) there is no per-app install permission — the global
     * "Unknown sources" system setting controls sideloading, so we return true.
     */
    fun canRequestInstalls(): Boolean = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }

    /**
     * Check GitHub for a newer release. Downloads APK silently if found.
     */
    suspend fun checkForUpdate(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkForUpdate: starting (debug=${BuildConfig.DEBUG}, versionName=${BuildConfig.VERSION_NAME}, gitSha=${BuildConfig.GIT_SHA.take(8)})")

        if (isInstalledFromPlayStore()) {
            Log.d(TAG, "checkForUpdate: skipped — installed from Play Store")
            return@withContext false
        }

        _state.value = UpdateState(checking = true)

        try {
            // Debug builds use the dev channel (pre-releases); release builds use /releases/latest
            val release = if (BuildConfig.DEBUG) {
                Log.d(TAG, "checkForUpdate: DEBUG build — listing releases for dev-* tag")
                api.listReleases()
                    .filter { it.tagName.startsWith("dev-") }
                    .sortedByDescending { it.createdAt }
                    .firstOrNull()
                    .also { Log.d(TAG, "checkForUpdate: latest dev release = ${it?.tagName ?: "none"} (createdAt=${it?.createdAt ?: "n/a"})") }
                    ?: run {
                        Log.d(TAG, "checkForUpdate: no dev-* release found")
                        _state.value = UpdateState(available = false)
                        return@withContext false
                    }
            } else {
                Log.d(TAG, "checkForUpdate: RELEASE build — fetching /releases/latest")
                api.getLatestRelease().also {
                    Log.d(TAG, "checkForUpdate: latest release = ${it.tagName}")
                }
            }

            val latestSha = extractSha(release.tagName)
            if (latestSha == null) {
                // Not a dev-<sha> tag — try semver comparison for release builds
                // (e.g. tag "v0.2.0" vs installed "0.1.0")
                if (!isNewerVersion(release.tagName, BuildConfig.VERSION_NAME)) {
                    Log.d(TAG, "checkForUpdate: tag '${release.tagName}' is not newer than ${BuildConfig.VERSION_NAME} — no update detected")
                    _state.value = UpdateState(available = false)
                    return@withContext false
                }
            } else {
                // Dev channel: compare SHAs directly
                val currentSha = BuildConfig.GIT_SHA
                Log.d(TAG, "checkForUpdate: currentSha=${currentSha.take(8)}, latestSha=${latestSha.take(8)}")

                if (latestSha == currentSha) {
                    Log.d(TAG, "checkForUpdate: already up to date")
                    _state.value = UpdateState(available = false)
                    return@withContext false
                }
            }

            Log.d(TAG, "checkForUpdate: NEW version available! ${release.tagName}")

            // Find the APK asset
            val apkAsset = release.assets.find { it.name.endsWith(".apk") }
            if (apkAsset == null) {
                Log.w(TAG, "checkForUpdate: release ${release.tagName} has no .apk asset (assets: ${release.assets.map { it.name }})")
                _state.value = UpdateState(available = false)
                return@withContext false
            }

            Log.d(TAG, "checkForUpdate: found APK asset '${apkAsset.name}' (${apkAsset.size / 1024} KB)")

            // Download silently in background
            _state.value = _state.value.copy(
                checking = false,
                available = true,
                downloading = true,
                newVersion = release.tagName,
                releaseNotes = release.name ?: "New build available",
            )

            val apkFile = File(updateDir, apkAsset.name)
            Log.d(TAG, "checkForUpdate: downloading ${apkAsset.browserDownloadUrl} → ${apkFile.absolutePath}")
            downloadApk(apkAsset.browserDownloadUrl, apkFile, apkAsset.size)
            Log.d(TAG, "checkForUpdate: download complete (${apkFile.length() / 1024} KB)")

            _state.value = _state.value.copy(
                downloading = false,
                downloadedApkPath = apkFile,
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "checkForUpdate: failed", e)
            _state.value = UpdateState(available = false, error = e.message ?: "Update check failed")
            false
        }
    }

    /**
     * Launch the system installer intent for the downloaded APK.
     * If the user hasn't granted "Install unknown apps" permission, opens
     * the settings page for them to grant it first.
     */
    fun installUpdate() {
        val apkPath = _state.value.downloadedApkPath
        if (apkPath == null) {
            Log.w(TAG, "installUpdate: no downloaded APK in state")
            return
        }

        if (!canRequestInstalls()) {
            Log.w(TAG, "installUpdate: install permission not granted — opening settings")
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        Log.d(TAG, "installUpdate: launching installer for ${apkPath.absolutePath}")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkPath)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun extractSha(tagName: String): String? {
        // Tag format: dev-<full-sha>
        return tagName.removePrefix("dev-").takeIf { it.length == 40 }
    }

    /**
     * Parse a version string into a list of integers: "v0.2.0" → [0, 2, 0].
     * Non-numeric suffixes (e.g. "-dev", "-rc1") are ignored.
     */
    private fun parseSemver(version: String): List<Int> = version
        .removePrefix("v")
        .substringBefore("-") // drop pre-release suffix
        .split(".")
        .mapNotNull { it.toIntOrNull() }

    /**
     * Returns true if [remoteTag] represents a newer version than [localVersion].
     * Handles both "v0.2.0" (remote) and "0.1.0" / "0.1.0-dev" (local).
     */
    private fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val remote = parseSemver(remoteTag)
        val local = parseSemver(localVersion)
        if (remote.isEmpty() || local.isEmpty()) return false
        // Compare component-by-component; pad shorter with 0s so that
        // 0.2.0.1 > 0.2.0 rather than silently truncating via zip().
        val maxLen = maxOf(remote.size, local.size)
        for (i in 0 until maxLen) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    private fun downloadApk(url: String, dest: File, expectedSize: Long) {
        // Clean old APKs — but preserve a partial download of the *same* file
        // so we can resume via HTTP Range. We match by filename: if dest already
        // exists (even partially), keep it and resume from its current length.
        updateDir.listFiles()?.forEach { f ->
            if (f.name != dest.name) {
                Log.d(TAG, "downloadApk: deleting old file ${f.name}")
                f.delete()
            }
        }

        val existingLen = dest.length()
        val resuming = existingLen > 0 && existingLen < expectedSize
        if (resuming) {
            Log.d(TAG, "downloadApk: resuming from $existingLen / $expectedSize bytes")
        }

        Log.d(TAG, "downloadApk: fetching $url${if (resuming) " (Range: $existingLen-)" else ""}")
        val client = OkHttpClient()
        val request = Request.Builder().url(url).apply {
            if (resuming) header("Range", "bytes=$existingLen-")
        }.build()

        client.newCall(request).execute().use { response ->
            // 200 = full content (server ignored Range, start over)
            // 206 = partial content (resume successful)
            if (response.code != 200 && response.code != 206) {
                Log.e(TAG, "downloadApk: HTTP ${response.code}")
                error("Download failed: ${response.code}")
            }

            // If server ignored Range (returned 200), truncate existing file
            val appendMode = resuming && response.code == 206
            if (!appendMode && dest.exists()) {
                dest.delete()
            }

            val body = response.body ?: error("Empty response body")
            val totalBytes = if (appendMode) expectedSize else body.contentLength().let { if (it > 0) it else expectedSize }
            val startBytes = if (appendMode) existingLen else 0L

            body.byteStream().use { input ->
                java.io.FileOutputStream(dest, appendMode).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = startBytes
                    var lastUpdate = 0L
                    val startTime = System.currentTimeMillis()

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        // Throttle state updates to ~2/sec
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                            val elapsed = (now - startTime) / 1000.0
                            val speed = if (elapsed > 0) (downloaded - startBytes) / elapsed else 0.0
                            val remaining = if (speed > 0) ((totalBytes - downloaded) / speed).toLong() else 0
                            _state.value = _state.value.copy(
                                downloadProgress = progress.coerceIn(0f, 1f),
                                downloadEtaSeconds = remaining,
                            )
                        }
                    }
                }
            }
        }
    }
}

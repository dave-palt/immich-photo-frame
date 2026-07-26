package com.dav3.immichframe.data.update

import android.content.Context
import android.content.Intent
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
    val downloading: Boolean = false,
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
     * Check GitHub for a newer release. Downloads APK silently if found.
     */
    suspend fun checkForUpdate(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkForUpdate: starting (debug=${BuildConfig.DEBUG}, versionName=${BuildConfig.VERSION_NAME}, gitSha=${BuildConfig.GIT_SHA.take(8)})")

        if (isInstalledFromPlayStore()) {
            Log.d(TAG, "checkForUpdate: skipped — installed from Play Store")
            return@withContext false
        }

        try {
            // Debug builds use the dev channel (pre-releases); release builds use /releases/latest
            val release = if (BuildConfig.DEBUG) {
                Log.d(TAG, "checkForUpdate: DEBUG build — listing releases for dev-* tag")
                api.listReleases()
                    .firstOrNull { it.tagName.startsWith("dev-") }
                    .also { Log.d(TAG, "checkForUpdate: latest dev release = ${it?.tagName ?: "none"}") }
                    ?: run {
                        Log.d(TAG, "checkForUpdate: no dev-* release found")
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
                Log.d(TAG, "checkForUpdate: tag '${release.tagName}' is not a dev-<sha> tag — no update detected")
                _state.value = UpdateState(available = false)
                return@withContext false
            }

            val currentSha = BuildConfig.GIT_SHA
            Log.d(TAG, "checkForUpdate: currentSha=${currentSha.take(8)}, latestSha=${latestSha.take(8)}")

            if (latestSha == currentSha) {
                Log.d(TAG, "checkForUpdate: already up to date")
                _state.value = UpdateState(available = false)
                return@withContext false
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
                available = true,
                downloading = true,
                newVersion = release.tagName,
                releaseNotes = release.name ?: "New build available",
            )

            val apkFile = File(updateDir, apkAsset.name)
            Log.d(TAG, "checkForUpdate: downloading ${apkAsset.browserDownloadUrl} → ${apkFile.absolutePath}")
            downloadApk(apkAsset.browserDownloadUrl, apkFile)
            Log.d(TAG, "checkForUpdate: download complete (${apkFile.length() / 1024} KB)")

            _state.value = _state.value.copy(
                downloading = false,
                downloadedApkPath = apkFile,
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "checkForUpdate: failed", e)
            _state.value = UpdateState(available = false, downloading = false)
            false
        }
    }

    /**
     * Launch the system installer intent for the downloaded APK.
     */
    fun installUpdate() {
        val apkPath = _state.value.downloadedApkPath
        if (apkPath == null) {
            Log.w(TAG, "installUpdate: no downloaded APK in state")
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

    private fun downloadApk(url: String, dest: File) {
        // Clean old APKs
        updateDir.listFiles()?.forEach { it.delete() }

        Log.d(TAG, "downloadApk: fetching $url")
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "downloadApk: HTTP ${response.code}")
                error("Download failed: ${response.code}")
            }
            response.body?.byteStream()?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Empty response body")
        }
    }
}

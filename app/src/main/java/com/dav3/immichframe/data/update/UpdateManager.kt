package com.dav3.immichframe.data.update

import android.content.Context
import android.content.Intent
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
        if (isInstalledFromPlayStore()) return@withContext false

        try {
            // Debug builds use the dev channel (pre-releases); release builds use /releases/latest
            val release = if (BuildConfig.DEBUG) {
                api.listReleases()
                    .firstOrNull { it.tagName.startsWith("dev-") }
                    ?: return@withContext false
            } else {
                api.getLatestRelease()
            }
            val latestSha = extractSha(release.tagName) ?: return@withContext false
            val currentSha = BuildConfig.GIT_SHA

            if (latestSha == currentSha) {
                _state.value = UpdateState(available = false)
                return@withContext false
            }

            // Find the APK asset
            val apkAsset = release.assets.find { it.name.endsWith(".apk") }
            if (apkAsset == null) {
                _state.value = UpdateState(available = false)
                return@withContext false
            }

            // Download silently in background
            _state.value = _state.value.copy(
                available = true,
                downloading = true,
                newVersion = release.tagName,
                releaseNotes = release.name ?: "New build available",
            )

            val apkFile = File(updateDir, apkAsset.name)
            downloadApk(apkAsset.browserDownloadUrl, apkFile)

            _state.value = _state.value.copy(
                downloading = false,
                downloadedApkPath = apkFile,
            )
            true
        } catch (_: Exception) {
            _state.value = UpdateState(available = false, downloading = false)
            false
        }
    }

    /**
     * Launch the system installer intent for the downloaded APK.
     */
    fun installUpdate() {
        val apkPath = _state.value.downloadedApkPath ?: return
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

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed: ${response.code}")
            response.body?.byteStream()?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Empty response body")
        }
    }
}

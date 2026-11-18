package app.dumpkin.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val release: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

/**
 * Looks for a newer Dumpkin in the GitHub releases, downloads it, checks it, and hands it to Android's installer.
 *
 * Nothing here installs silently: Android always shows its own install prompt. The download is checked three ways
 * before that prompt: its size, the SHA-256 in the release notes (when there is one), and that it is the same app
 * signed with the same key as the one installed. A private repository needs a GitHub access token, which is stored
 * only on this phone and only ever sent to api.github.com.
 */
class UpdateManager(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = app.getSharedPreferences("dumpkin_update", Context.MODE_PRIVATE)
    private val dir = File(app.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val installedVersion: String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()

    val autoCheck = MutableStateFlow(prefs.getBoolean("auto", true))
    val hasToken = MutableStateFlow(!prefs.getString("token", null).isNullOrBlank())

    fun setAutoCheck(on: Boolean) {
        autoCheck.value = on
        prefs.edit().putBoolean("auto", on).apply()
    }

    fun setToken(token: String) {
        val t = token.trim()
        prefs.edit().apply { if (t.isEmpty()) remove("token") else putString("token", t) }.apply()
        hasToken.value = t.isNotEmpty()
    }

    private fun token(): String? = prefs.getString("token", null)?.takeIf { it.isNotBlank() }

    /** Called when the app starts: look once every 12 hours, quietly. */
    fun autoCheckIfDue() {
        if (!autoCheck.value) return
        if (System.currentTimeMillis() - prefs.getLong("last", 0L) < CHECK_EVERY_MS) return
        check(quiet = true)
    }

    /** Looks for a newer release. With [quiet], a failure leaves no error behind (used for the automatic check). */
    fun check(quiet: Boolean = false) {
        val now = _state.value
        if (now is UpdateState.Checking || now is UpdateState.Downloading) return
        if (now is UpdateState.Ready) return // already downloaded, nothing more to look for
        scope.launch {
            _state.value = UpdateState.Checking
            try {
                val body = fetchReleases()
                val releases = UpdateLogic.parseReleases(body)
                prefs.edit().putLong("last", System.currentTimeMillis()).apply()
                val newest = UpdateLogic.newest(releases, installedVersion)
                _state.value = if (newest != null) UpdateState.Available(newest) else UpdateState.UpToDate(System.currentTimeMillis())
            } catch (e: Exception) {
                _state.value = if (quiet) UpdateState.Idle else UpdateState.Failed(e.message ?: "Couldn't check for updates.")
            }
        }
    }

    private fun fetchReleases(): String {
        val t = token()
        val conn = open("https://api.github.com/repos/$REPO/releases?per_page=20", "application/vnd.github+json", t)
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    fun download(release: ReleaseInfo) {
        if (_state.value is UpdateState.Downloading) return
        scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val part = File(dir, "Dumpkin-${safe(release.version)}.apk.part")
            val target = File(dir, "Dumpkin-${safe(release.version)}.apk")
            try {
                val t = token()
                // A private repository can only be downloaded through the API with the token; a public one directly.
                val url = if (t != null && release.assetApiUrl.isNotBlank()) release.assetApiUrl else release.downloadUrl
                val conn = open(url, "application/octet-stream", t)
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                var last = 0f
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                if (p - last >= 0.02f) { last = p; _state.value = UpdateState.Downloading(release, p) }
                            }
                        }
                    }
                }
                verify(release, part, done, digest.digest().joinToString("") { "%02x".format(it) })
                if (!part.renameTo(target)) throw IOException("couldn't move the download into place")
                _state.value = UpdateState.Ready(release, target)
            } catch (e: Exception) {
                part.delete()
                _state.value = UpdateState.Failed("The download failed: ${e.message}", release)
            }
        }
    }

    /** Throws with a clear message unless the file really is this release of this app. */
    private fun verify(release: ReleaseInfo, file: File, size: Long, sha256: String) {
        if (release.size > 0 && size != release.size) {
            throw IOException("the file is $size bytes but the release lists ${release.size}")
        }
        release.sha256?.let { expected ->
            if (!expected.equals(sha256, ignoreCase = true)) throw IOException("its checksum doesn't match the release notes")
        }
        val pm = app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw IOException("it isn't a valid Android app")
        if (archive.packageName != app.packageName) throw IOException("it is a different app (${archive.packageName})")
        val installed = pm.getPackageInfo(app.packageName, flags)
        val a = signers(archive)
        val b = signers(installed)
        if (a.isEmpty() || a.intersect(b).isEmpty()) {
            throw IOException("it is signed with a different key than the installed app, so Android would refuse to update")
        }
    }

    private fun signers(info: PackageInfo): Set<String> {
        @Suppress("DEPRECATION")
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else info.signatures
        return sigs.orEmpty().map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    // ---- network: see UpdateNet for how the token is protected ---------------------------------------------------

    private val net = UpdateNet()
    private fun open(url: String, accept: String, token: String?) = net.open(url, accept, token)

    // ---- installing --------------------------------------------------------------------------------------------

    /** Android 8+ asks the user to allow installs from each app. */
    fun needsInstallPermission(): Boolean = Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun dismissError() {
        _state.update { if (it is UpdateState.Failed) UpdateState.Idle else it }
    }

    private fun safe(v: String) = v.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        /** Where releases are read from. */
        const val REPO = "colebolebole/Dumpkin"
        private const val CHECK_EVERY_MS = 12 * 60 * 60 * 1000L
    }
}

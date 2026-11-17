package app.dumpkin.engine

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class Channel(val label: String, val tag: String?, val blurb: String) {
    Stable("Stable", null, "Latest tagged release. Best choice for most people."),
    Canary("Canary", "canary", "Rolling build with the newest changes, not fully optimised."),
    Nightly("Nightly", "nightly", "Rolling, fully optimised build."),
}

data class InstalledEngine(
    val id: String,
    val label: String,
    val channel: String,
    val sizeBytes: Long,
    val file: File,
)

data class RemoteEngine(
    val channel: Channel,
    val tag: String,
    val label: String,
    val assetName: String,
    val url: String,
    val size: Long,
    val updated: String,
) {
    val id: String
        get() = if (channel == Channel.Stable) tag.sanitize()
        else "${channel.name.lowercase()}-" + updated.filter { it.isLetterOrDigit() }
}

data class EngineUiState(
    val installed: List<InstalledEngine> = emptyList(),
    val remote: List<RemoteEngine> = emptyList(),
    val checking: Boolean = false,
    val downloadingId: String? = null,
    val progress: Float = 0f,
    val message: String? = null,
)

private fun String.sanitize() = replace(Regex("[^A-Za-z0-9._-]"), "_")

/**
 * Downloads and stores builds of the Pumpkin server. Release assets are discovered through the GitHub API,
 * so the exact asset name (`pumpkin-aarch64-android`) is never hard-coded.
 */
class EngineManager(ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root = File(ctx.filesDir, "engine").apply { mkdirs() }

    private val _state = MutableStateFlow(EngineUiState(installed = scanInstalled()))
    val state: StateFlow<EngineUiState> = _state.asStateFlow()

    /** The Pumpkin Android build is arm64 only. */
    val supported: Boolean = Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")

    fun resolve(id: String?): InstalledEngine? {
        val list = _state.value.installed
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    fun checkRemote() {
        if (_state.value.checking) return
        scope.launch {
            _state.update { it.copy(checking = true, message = null) }
            val found = Channel.entries.mapNotNull { ch -> runCatching { fetch(ch) }.getOrNull() }
            _state.update {
                it.copy(
                    checking = false,
                    remote = found,
                    message = if (found.isEmpty()) "Couldn't find an Android build. Check your connection and try again." else null,
                )
            }
        }
    }

    fun download(r: RemoteEngine) {
        if (_state.value.downloadingId != null) return
        scope.launch {
            _state.update { it.copy(downloadingId = r.id, progress = 0f, message = null) }
            val dir = File(root, r.id).apply { mkdirs() }
            try {
                val part = File(dir, "pumpkin.part")
                val conn = open(r.url, "application/octet-stream")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
                var done = 0L
                var lastPublished = 0f
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                if (p - lastPublished >= 0.01f) {
                                    lastPublished = p
                                    _state.update { it.copy(progress = p) }
                                }
                            }
                        }
                    }
                }
                val magic = part.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
                require(magic.contentEquals(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))) {
                    "the downloaded file is not an executable"
                }
                val bin = File(dir, "pumpkin")
                bin.delete()
                check(part.renameTo(bin)) { "could not move the file into place" }
                bin.setExecutable(true, false)
                File(dir, "meta.json").writeText(
                    JSONObject().put("label", r.label).put("channel", r.channel.label).toString()
                )
                _state.update { it.copy(installed = scanInstalled(), downloadingId = null, progress = 1f) }
            } catch (e: Exception) {
                dir.deleteRecursively()
                _state.update { it.copy(downloadingId = null, message = "Download failed: ${e.message}") }
            }
        }
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
        _state.update { it.copy(installed = scanInstalled()) }
    }

    private fun fetch(ch: Channel): RemoteEngine? {
        val api = "https://api.github.com/repos/Pumpkin-MC/Pumpkin/releases"
        val url = if (ch.tag == null) "$api/latest" else "$api/tags/${ch.tag}"
        val conn = open(url, "application/vnd.github+json")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.getString("name")
            if (name.contains("android", ignoreCase = true) && !name.endsWith(".sig") && !name.endsWith(".sha256")) {
                val tag = json.getString("tag_name")
                val updated = a.optString("updated_at", json.optString("published_at"))
                return RemoteEngine(
                    channel = ch,
                    tag = tag,
                    label = if (ch == Channel.Stable) tag else "${ch.label} ${updated.take(10)}",
                    assetName = name,
                    url = a.getString("browser_download_url"),
                    size = a.optLong("size"),
                    updated = updated,
                )
            }
        }
        return null
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "Dumpkin-Android")
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        return c
    }

    private fun scanInstalled(): List<InstalledEngine> =
        root.listFiles { f -> f.isDirectory }.orEmpty()
            .filter { File(it, "pumpkin").isFile }
            .sortedByDescending { File(it, "pumpkin").lastModified() }
            .map { dir ->
                val meta = runCatching { JSONObject(File(dir, "meta.json").readText()) }.getOrNull()
                val bin = File(dir, "pumpkin")
                InstalledEngine(
                    id = dir.name,
                    label = meta?.optString("label").orEmpty().ifBlank { dir.name },
                    channel = meta?.optString("channel").orEmpty(),
                    sizeBytes = bin.length(),
                    file = bin,
                )
            }
}

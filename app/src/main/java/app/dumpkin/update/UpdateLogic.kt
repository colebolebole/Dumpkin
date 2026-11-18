package app.dumpkin.update

import org.json.JSONArray
import org.json.JSONException
import java.net.URI

/** A published Dumpkin release that carries an APK. */
data class ReleaseInfo(
    val tag: String,
    /** The tag without the leading `v`, e.g. `0.5.1-alpha`. */
    val version: String,
    val title: String,
    val notes: String,
    val assetName: String,
    /** GitHub API URL of the asset. Works with a token, so it is how private repositories are downloaded. */
    val assetApiUrl: String,
    /** Public download URL of the asset. */
    val downloadUrl: String,
    val size: Long,
    /** SHA-256 quoted in the release notes, lower-case, if there is one. */
    val sha256: String?,
    val publishedAt: String,
    val pageUrl: String,
)

/** The logic of the update checker that doesn't touch Android or the network, so it can be tested directly. */
object UpdateLogic {
    /** Numbers like `0.5.1` plus whatever follows the first dash, like `alpha`. */
    data class Version(val numbers: List<Int>, val suffix: String)

    private val VERSION = Regex("""^v?(\d+(?:\.\d+)*)(?:[-+](.+))?$""")
    private val SHA = Regex("""(?i)sha-?256[^0-9a-f\n]{0,24}([0-9a-f]{64})""")
    private val APK_NAME = Regex("""(?i)^dumpkin.*\.apk$""")

    fun parse(text: String): Version? {
        val m = VERSION.matchEntire(text.trim()) ?: return null
        return Version(m.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }, m.groupValues[2])
    }

    /** Orders versions: numbers first (missing ones count as 0), then a release beats a pre-release of the same number. */
    fun compare(a: Version, b: Version): Int {
        for (i in 0 until maxOf(a.numbers.size, b.numbers.size)) {
            val c = (a.numbers.getOrElse(i) { 0 }).compareTo(b.numbers.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        if (a.suffix.isEmpty() != b.suffix.isEmpty()) return if (a.suffix.isEmpty()) 1 else -1
        return a.suffix.compareTo(b.suffix)
    }

    /** True when [candidate] is a newer version than [installed]. Unparseable versions are never "newer". */
    fun isNewer(candidate: String, installed: String): Boolean {
        val c = parse(candidate) ?: return false
        val i = parse(installed) ?: return false
        return compare(c, i) > 0
    }

    /** Releases (newest first as GitHub lists them) that have an APK, skipping drafts. */
    fun parseReleases(json: String): List<ReleaseInfo> {
        val arr = try { JSONArray(json) } catch (_: JSONException) { return emptyList() }
        val out = ArrayList<ReleaseInfo>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.optBoolean("draft")) continue
            val tag = r.optString("tag_name")
            if (parse(tag) == null) continue
            val assets = r.optJSONArray("assets") ?: continue
            val apks = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            val asset = apks.firstOrNull { APK_NAME.matches(it.optString("name")) } ?: apks.firstOrNull() ?: continue
            val notes = r.optString("body")
            out += ReleaseInfo(
                tag = tag,
                version = tag.removePrefix("v"),
                title = r.optString("name").ifBlank { tag },
                notes = notes,
                assetName = asset.optString("name"),
                assetApiUrl = asset.optString("url"),
                downloadUrl = asset.optString("browser_download_url"),
                size = asset.optLong("size"),
                sha256 = sha256In(notes),
                publishedAt = r.optString("published_at"),
                pageUrl = r.optString("html_url"),
            )
        }
        return out
    }

    /** The newest release that is newer than [installed], or null. */
    fun newest(releases: List<ReleaseInfo>, installed: String): ReleaseInfo? =
        releases.filter { isNewer(it.version, installed) }
            .maxWithOrNull { a, b -> compare(parse(a.version)!!, parse(b.version)!!) }

    /** Pulls "SHA-256: `abc...`" out of release notes. */
    fun sha256In(notes: String): String? = SHA.find(notes)?.groupValues?.get(1)?.lowercase()

    /**
     * Only GitHub is ever contacted, over HTTPS. This also guards redirects: a download that is bounced to some other
     * host is refused, and the access token is never sent anywhere but api.github.com.
     */
    fun isTrustedUrl(url: String): Boolean {
        val uri = try { URI(url) } catch (_: Exception) { return false }
        if (uri.scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return host == "api.github.com" || host == "github.com" || host.endsWith(".githubusercontent.com")
    }

    fun sendsToken(url: String): Boolean = runCatching { URI(url).let { it.scheme == "https" && it.host.equals("api.github.com", true) } }.getOrDefault(false)

    /** A readable message for a failed request to GitHub. */
    fun describeHttp(code: Int, hadToken: Boolean): String = when {
        code == 404 && !hadToken ->
            "Couldn't read the releases (HTTP 404). If the repository is private, add a GitHub access token in the update settings."
        code == 404 -> "Couldn't find the releases (HTTP 404). Check that the access token can read this repository."
        code == 401 -> "GitHub didn't accept the access token (HTTP 401). Check it in the update settings."
        code == 403 || code == 429 -> "GitHub is limiting requests right now (HTTP $code). Try again in a while."
        else -> "GitHub answered with HTTP $code."
    }
}

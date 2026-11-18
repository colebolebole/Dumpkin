package app.dumpkin.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP for the updater, with two rules that protect the user's GitHub token: only trusted addresses are contacted
 * (also after a redirect), and the token is only attached to requests that [sendsToken] approves. Redirects are
 * followed by hand so each hop is checked. The predicates are injectable so tests can use local servers.
 */
internal class UpdateNet(
    private val trusted: (String) -> Boolean = UpdateLogic::isTrustedUrl,
    private val sendsToken: (String) -> Boolean = UpdateLogic::sendsToken,
) {
    fun open(url: String, accept: String, token: String?, hop: Int = 0): HttpURLConnection {
        if (!trusted(url)) throw IOException("refusing to contact an unexpected address")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "Dumpkin-Android")
        if (token != null && sendsToken(url)) c.setRequestProperty("Authorization", "Bearer $token")
        val code = try { c.responseCode } catch (e: IOException) { throw IOException("no connection to GitHub (${e.message})") }
        return when (code) {
            in 200..299 -> c
            301, 302, 303, 307, 308 -> {
                val location = c.getHeaderField("Location") ?: throw IOException("a redirect without a destination")
                c.disconnect()
                if (hop >= MAX_REDIRECTS) throw IOException("too many redirects")
                // The next hop is judged on its own: the token follows only if it is also an approved address.
                open(URL(URL(url), location).toString(), accept, token, hop + 1)
            }
            else -> {
                c.disconnect()
                throw IOException(UpdateLogic.describeHttp(code, token != null))
            }
        }
    }

    companion object {
        const val MAX_REDIRECTS = 5
    }
}

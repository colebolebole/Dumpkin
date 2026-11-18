package app.dumpkin.update

import app.dumpkin.web.HttpResponse
import app.dumpkin.web.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class UpdateTest {
    // ---- versions ----------------------------------------------------------------------------------------------

    @Test fun versionsAreComparedNumerically() {
        assertTrue(UpdateLogic.isNewer("0.5.1-alpha", "0.5.0-alpha"))
        assertTrue(UpdateLogic.isNewer("v0.10.0-alpha", "0.9.9-alpha")) // 10 > 9, not a text comparison
        assertTrue(UpdateLogic.isNewer("1.0", "0.9.9"))
        assertTrue(UpdateLogic.isNewer("0.5.0.1", "0.5.0"))
        assertFalse(UpdateLogic.isNewer("0.5.0-alpha", "0.5.0-alpha"))
        assertFalse(UpdateLogic.isNewer("0.4.9-alpha", "0.5.0-alpha"))
        assertFalse(UpdateLogic.isNewer("0.5", "0.5.0")) // missing parts count as zero
    }

    @Test fun aReleaseBeatsAPrereleaseOfTheSameNumber() {
        assertTrue(UpdateLogic.isNewer("0.6.0", "0.6.0-alpha"))
        assertFalse(UpdateLogic.isNewer("0.6.0-alpha", "0.6.0"))
        assertTrue(UpdateLogic.isNewer("0.6.0-beta", "0.6.0-alpha"))
    }

    @Test fun unreadableVersionsAreNeverNewer() {
        assertFalse(UpdateLogic.isNewer("latest", "0.5.0-alpha"))
        assertFalse(UpdateLogic.isNewer("0.6.0", ""))
        assertNull(UpdateLogic.parse("nightly-2026"))
        assertEquals(listOf(0, 5, 1), UpdateLogic.parse("v0.5.1-alpha")!!.numbers)
        assertEquals("alpha", UpdateLogic.parse("v0.5.1-alpha")!!.suffix)
    }

    // ---- release parsing ---------------------------------------------------------------------------------------

    private fun release(tag: String, apk: String? = "Dumpkin-${tag.removePrefix("v")}.apk", body: String = "", draft: Boolean = false) = """
        {"tag_name":"$tag","name":"Dumpkin $tag","draft":$draft,"prerelease":true,"body":${org.json.JSONObject.quote(body)},
         "published_at":"2026-11-18T10:00:00Z","html_url":"https://github.com/colebolebole/Dumpkin/releases/tag/$tag",
         "assets":[${if (apk == null) "" else """{"name":"$apk","size":1612277,"url":"https://api.github.com/repos/colebolebole/Dumpkin/releases/assets/42","browser_download_url":"https://github.com/colebolebole/Dumpkin/releases/download/$tag/$apk"}"""}]}
    """.trimIndent()

    @Test fun parsesReleasesAndPicksTheNewestNewerOne() {
        val sha = "248e1f92b966521fce89e75e3d0d82bfe1e6ae1b52943f7054d098448898f8d2"
        val json = "[" + listOf(
            release("v0.6.0-alpha", body = "**Install:** sideload it. SHA-256: `$sha`"),
            release("v0.5.1-alpha"),
            release("v0.7.0-alpha", draft = true), // drafts are not published
            release("v0.8.0-alpha", apk = null),   // nothing to install
            release("v0.5.0-alpha"),
        ).joinToString(",") + "]"
        val all = UpdateLogic.parseReleases(json)
        assertEquals(listOf("0.6.0-alpha", "0.5.1-alpha", "0.5.0-alpha"), all.map { it.version })

        val best = UpdateLogic.newest(all, "0.5.1-alpha")!!
        assertEquals("v0.6.0-alpha", best.tag)
        assertEquals("Dumpkin-0.6.0-alpha.apk", best.assetName)
        assertEquals(1612277L, best.size)
        assertEquals(sha, best.sha256)
        assertTrue(best.assetApiUrl.endsWith("/assets/42"))
        assertNull("nothing newer than the newest", UpdateLogic.newest(all, "0.6.0-alpha"))
        assertNull(UpdateLogic.newest(emptyList(), "0.1"))
    }

    @Test fun anOutOfOrderListStillYieldsTheHighestVersion() {
        val json = "[" + listOf(release("v0.5.2-alpha"), release("v0.9.0-alpha"), release("v0.6.0-alpha")).joinToString(",") + "]"
        assertEquals("0.9.0-alpha", UpdateLogic.newest(UpdateLogic.parseReleases(json), "0.5.0-alpha")!!.version)
    }

    @Test fun garbageFromGithubIsIgnored() {
        assertTrue(UpdateLogic.parseReleases("not json").isEmpty())
        assertTrue(UpdateLogic.parseReleases("""{"message":"Not Found"}""").isEmpty())
        assertTrue(UpdateLogic.parseReleases("""[1, "x", {"tag_name":"v1.0","assets":"nope"}, {"tag_name":"weird","assets":[]}]""").isEmpty())
    }

    @Test fun checksumIsFoundInTypicalReleaseNotes() {
        val sha = "9588dffb80b3c54dc94e18c5e19c91a5fac630bb6fe0615a03d4372b9850162f"
        assertEquals(sha, UpdateLogic.sha256In("**Install:** sideload. SHA-256: `$sha`\n\n### What's new"))
        assertEquals(sha, UpdateLogic.sha256In("sha256 $sha"))
        assertEquals(sha, UpdateLogic.sha256In("SHA-256: ${sha.uppercase()}"))
        assertNull(UpdateLogic.sha256In("no checksum here, just a 40 char hash 0123456789abcdef0123456789abcdef01234567"))
        assertNull(UpdateLogic.sha256In(""))
    }

    // ---- which addresses are trusted ---------------------------------------------------------------------------

    @Test fun onlyHttpsGithubAddressesAreTrusted() {
        for (ok in listOf(
            "https://api.github.com/repos/x/y/releases", "https://github.com/x/y/releases/download/v1/a.apk",
            "https://objects.githubusercontent.com/abc", "https://release-assets.githubusercontent.com/x",
        )) assertTrue(ok, UpdateLogic.isTrustedUrl(ok))
        for (bad in listOf(
            "http://api.github.com/x", "https://evil.example/api.github.com", "https://api.github.com.evil.example/x",
            "https://github.com@evil.example/x", "https://githubusercontent.com.evil.example/x", "https://evilgithub.com/x",
            "ftp://github.com/x", "file:///sdcard/x.apk", "javascript:alert(1)", "", "not a url", "https://notgithubusercontent.com/x",
        )) assertFalse(bad, UpdateLogic.isTrustedUrl(bad))
        assertTrue(UpdateLogic.sendsToken("https://api.github.com/repos/x/y/releases/assets/1"))
        assertFalse("the token is for the API only", UpdateLogic.sendsToken("https://github.com/x/y/releases/download/v1/a.apk"))
        assertFalse(UpdateLogic.sendsToken("https://objects.githubusercontent.com/abc"))
        assertFalse(UpdateLogic.sendsToken("https://api.github.com.evil.example/x"))
    }

    @Test fun httpErrorsExplainWhatToDo() {
        assertTrue(UpdateLogic.describeHttp(404, hadToken = false).contains("access token"))
        assertTrue(UpdateLogic.describeHttp(404, hadToken = true).contains("can read"))
        assertTrue(UpdateLogic.describeHttp(401, true).contains("token"))
        assertTrue(UpdateLogic.describeHttp(403, false).contains("limiting"))
        assertTrue(UpdateLogic.describeHttp(500, false).contains("500"))
    }

    // ---- redirects and the token, against local servers --------------------------------------------------------

    private class Seen(val path: String, val auth: String?)

    private val servers = mutableListOf<HttpServer>()
    private val seen = CopyOnWriteArrayList<Seen>()

    @After fun stopServers() = servers.forEach { it.stop() }

    private fun serve(handler: (path: String, auth: String?) -> HttpResponse): Int {
        val s = HttpServer(0, "127.0.0.1") { req ->
            seen += Seen(req.path, req.header("authorization"))
            handler(req.path, req.header("authorization"))
        }
        s.start(); servers += s
        return s.boundPort
    }

    private fun redirect(to: String) = HttpResponse(302, mapOf("Location" to to))
    private fun ok(text: String) = HttpResponse.bytes(200, "text/plain", text.toByteArray())

    @Test fun theTokenGoesOnlyToTheApiHostEvenThroughRedirects() {
        val cdn = serve { _, _ -> ok("the-apk-bytes") }
        val api = serve { path, _ -> if (path == "/asset") redirect("http://127.0.0.1:$cdn/blob") else ok("releases") }
        // Only the "API" server may get the token; both are "trusted" for contact.
        val net = UpdateNet(trusted = { true }, sendsToken = { it.contains(":$api/") })

        val conn = net.open("http://127.0.0.1:$api/asset", "application/octet-stream", token = "secret-token")
        assertEquals("the-apk-bytes", conn.inputStream.readBytes().decodeToString())
        val apiCall = seen.first { it.path == "/asset" }
        val cdnCall = seen.first { it.path == "/blob" }
        assertEquals("Bearer secret-token", apiCall.auth)
        assertNull("the token must not follow the redirect to another host", cdnCall.auth)
    }

    @Test fun withoutATokenNothingIsSent() {
        val api = serve { _, _ -> ok("ok") }
        UpdateNet(trusted = { true }, sendsToken = { true }).open("http://127.0.0.1:$api/x", "*/*", token = null).inputStream.readBytes()
        assertNull(seen.single().auth)
    }

    @Test fun aRedirectToAnUntrustedHostIsRefused() {
        val evil = serve { _, _ -> ok("evil") }
        val api = serve { _, _ -> redirect("http://127.0.0.1:$evil/steal") }
        val net = UpdateNet(trusted = { it.contains(":$api/") }, sendsToken = { true })
        try {
            net.open("http://127.0.0.1:$api/x", "*/*", token = "secret-token"); fail("should have been refused")
        } catch (e: IOException) {
            assertTrue(e.message, e.message!!.contains("unexpected address"))
        }
        assertTrue("the untrusted host must never be contacted", seen.none { it.path == "/steal" })
    }

    @Test fun redirectLoopsAndBadStatusesFailCleanly() {
        lateinit var self: String
        val loop = serve { _, _ -> redirect(self) }
        self = "http://127.0.0.1:$loop/again"
        try { UpdateNet(trusted = { true }, sendsToken = { false }).open(self, "*/*", null); fail() } catch (e: IOException) {
            assertTrue(e.message, e.message!!.contains("too many redirects"))
        }
        val notFound = serve { _, _ -> HttpResponse.error(404, "nope") }
        try { UpdateNet(trusted = { true }, sendsToken = { false }).open("http://127.0.0.1:$notFound/x", "*/*", null); fail() } catch (e: IOException) {
            assertTrue(e.message, e.message!!.contains("404"))
        }
        assertNotNull(seen)
    }
}

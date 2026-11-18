package app.dumpkin.web

import app.dumpkin.data.DEFAULT_BEDROCK_PORT
import app.dumpkin.data.ProfilePatch
import app.dumpkin.data.ProfileRules
import app.dumpkin.data.RuntimeState
import app.dumpkin.data.ServerProfile
import app.dumpkin.engine.EngineUiState
import app.dumpkin.files.FileError
import app.dumpkin.files.Listing
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI

private class ApiError(val status: Int, message: String) : Exception(message)

/**
 * Routes HTTP requests to the dashboard's static files and its JSON API.
 *
 * Security model: every API call except login needs a session cookie; every state-changing call additionally needs the
 * `X-Requested-With: Dumpkin` header (a cross-site page can't add it), a matching Origin if one is sent, and the Host
 * header must be an address or plain local name, which defeats DNS-rebinding attacks.
 */
class WebApi(
    private val backend: WebBackend,
    private val auth: WebAuth,
    /** Loads a bundled web file, e.g. "index.html". */
    private val assets: (String) -> ByteArray?,
) {
    fun handle(req: HttpRequest): HttpResponse {
        return try {
            if (!hostAllowed(req.header("host"))) throw ApiError(400, "Unexpected Host header.")
            route(req)
        } catch (e: ApiError) {
            HttpResponse.error(e.status, e.message ?: "Error")
        } catch (e: ProfilePatch.Invalid) {
            HttpResponse.error(400, e.message ?: "Invalid value.")
        }
    }

    private fun route(req: HttpRequest): HttpResponse {
        if (!req.path.startsWith("/api/") && req.path != "/api") return static(req)

        val segs = req.path.removePrefix("/api").trim('/').split('/').filter { it.isNotEmpty() }
        if (segs.firstOrNull() == "login") {
            requireMethod(req, "POST")
            requireSameSite(req)
            return login(req)
        }

        if (!auth.isValid(req.cookie(COOKIE))) throw ApiError(401, "Sign in required.")
        if (req.method != "GET" && req.method != "HEAD") requireSameSite(req)

        return when (segs.firstOrNull()) {
            "logout" -> { requireMethod(req, "POST"); logout(req) }
            "state" -> { requireMethod(req, "GET"); HttpResponse.json(200, state()) }
            "servers" -> servers(req, segs.drop(1))
            "engine" -> engine(req, segs.drop(1))
            else -> throw ApiError(404, "Not found.")
        }
    }

    // ---- security checks -------------------------------------------------------------------------------------------

    private fun hostAllowed(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val name = if (host.startsWith("[")) host.substringBefore(']') + "]" else host.substringBeforeLast(':', host)
        return name.startsWith("[") || // IPv6 literal
            name.equals("localhost", ignoreCase = true) ||
            Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(name) ||
            !name.contains('.') ||
            name.endsWith(".local", ignoreCase = true)
    }

    private fun requireSameSite(req: HttpRequest) {
        if (req.header("x-requested-with") != "Dumpkin") throw ApiError(403, "Missing request header.")
        val origin = req.header("origin")
        if (origin != null) {
            val authority = runCatching { URI(origin).authority }.getOrNull()
            if (authority == null || authority != req.header("host")) throw ApiError(403, "Cross-site request blocked.")
        }
    }

    private fun requireMethod(req: HttpRequest, vararg methods: String) {
        if (req.method !in methods) throw ApiError(405, "Method not allowed.")
    }

    // ---- static files ----------------------------------------------------------------------------------------------

    private fun static(req: HttpRequest): HttpResponse {
        requireMethod(req, "GET", "HEAD")
        val name = if (req.path == "/") "index.html" else req.path.removePrefix("/")
        val type = STATIC[name] ?: throw ApiError(404, "Not found.")
        val bytes = assets(name) ?: throw ApiError(404, "Not found.")
        return HttpResponse.bytes(200, type, bytes)
    }

    // ---- login / logout --------------------------------------------------------------------------------------------

    private fun login(req: HttpRequest): HttpResponse {
        val body = jsonBody(req)
        val password = body.optString("password", "")
        return when (val r = auth.login(req.remoteIp, password)) {
            is LoginResult.Ok -> HttpResponse.json(200, JSONObject().put("ok", true))
                .withHeader("Set-Cookie", "$COOKIE=${r.token}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${WebAuth.SESSION_MS / 1000}")
            is LoginResult.Wrong -> throw ApiError(401, "Wrong password.")
            is LoginResult.Locked -> HttpResponse.error(429, "Too many attempts. Try again in ${r.secondsLeft} seconds.")
                .withHeader("Retry-After", r.secondsLeft.toString())
        }
    }

    private fun logout(req: HttpRequest): HttpResponse {
        auth.logout(req.cookie(COOKIE))
        return HttpResponse.json(200, JSONObject().put("ok", true))
            .withHeader("Set-Cookie", "$COOKIE=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0")
    }

    // ---- state -----------------------------------------------------------------------------------------------------

    private fun state(): JSONObject {
        val dev = backend.device()
        return JSONObject()
            .put("now", System.currentTimeMillis())
            .put("app", JSONObject().put("name", "Dumpkin").put("version", backend.appVersion))
            .put(
                "device", JSONObject().put("totalMb", dev.totalMb).put("availMb", dev.availMb).put("cores", dev.cores)
                    .put("addresses", JSONArray(dev.addresses))
            )
            .put("engine", engineJson(backend.engine()))
            .put("servers", JSONArray().also { arr -> backend.profiles().forEach { arr.put(serverJson(it)) } })
    }

    private fun engineJson(e: EngineUiState): JSONObject = JSONObject()
        .put("supported", backend.engineSupported)
        .put("checking", e.checking)
        .put("downloadingId", e.downloadingId ?: JSONObject.NULL)
        .put("progress", e.progress.toDouble())
        .put("message", e.message ?: JSONObject.NULL)
        .put("installed", JSONArray().also { arr ->
            e.installed.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("label", it.label).put("channel", it.channel).put("sizeBytes", it.sizeBytes)
                )
            }
        })
        .put("available", JSONArray().also { arr ->
            e.remote.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("channel", it.channel.label).put("tag", it.tag).put("label", it.label)
                        .put("sizeBytes", it.size).put("updated", it.updated).put("blurb", it.channel.blurb)
                        .put("installed", e.installed.any { i -> i.id == it.id })
                )
            }
        })

    private fun serverJson(p: ServerProfile): JSONObject {
        val rt: RuntimeState = backend.runtime(p.id) ?: RuntimeState()
        val o = p.toJson()
        if (p.engineId == null) o.put("engineId", JSONObject.NULL)
        return o
            .put("status", rt.status.name.lowercase())
            .put("online", rt.online)
            .put("max", rt.max)
            .put("version", rt.version ?: JSONObject.NULL)
            .put("rssMb", rt.rssMb)
            .put("startedAt", rt.startedAt)
            .put("restarting", rt.restarting)
            .put("error", rt.error ?: JSONObject.NULL)
    }

    // ---- servers ---------------------------------------------------------------------------------------------------

    private fun servers(req: HttpRequest, segs: List<String>): HttpResponse {
        if (segs.isEmpty()) {
            return when (req.method) {
                "GET" -> HttpResponse.json(200, JSONArray().also { arr -> backend.profiles().forEach { arr.put(serverJson(it)) } })
                "POST" -> createServer(req)
                else -> throw ApiError(405, "Method not allowed.")
            }
        }
        val id = segs[0]
        val profile = backend.profiles().firstOrNull { it.id == id } ?: throw ApiError(404, "No such server.")
        val sub = segs.getOrNull(1)
        val maxSegs = when (sub) { "files", "config" -> 3; "backups" -> 4; else -> 2 }
        if (segs.size > maxSegs || (sub == "config" && segs.size == 3 && segs[2] != "reset")) throw ApiError(404, "Not found.")

        return when (sub) {
            null -> when (req.method) {
                "GET", "HEAD" -> HttpResponse.json(200, serverJson(profile))
                "PATCH" -> updateServer(req, profile)
                "DELETE" -> deleteServer(profile)
                else -> throw ApiError(405, "Method not allowed.")
            }
            "start" -> {
                requireMethod(req, "POST")
                backend.start(id)?.let { throw ApiError(409, it) }
                HttpResponse.json(200, serverJson(profile))
            }
            "stop" -> {
                requireMethod(req, "POST")
                backend.stop(id)
                HttpResponse.json(200, serverJson(profile))
            }
            "restart" -> {
                requireMethod(req, "POST")
                backend.restart(id)?.let { throw ApiError(409, it) }
                HttpResponse.json(200, serverJson(profile))
            }
            "command" -> {
                requireMethod(req, "POST")
                val command = jsonBody(req).optString("command", "").replace(Regex("[\\r\\n]+"), " ").trim().removePrefix("/")
                if (command.isEmpty()) throw ApiError(400, "Type a command first.")
                if (command.length > 500) throw ApiError(400, "That command is too long.")
                if (backend.runtime(id)?.isActive != true) throw ApiError(409, "The server isn't running.")
                backend.command(id, command)
                HttpResponse.json(200, JSONObject().put("ok", true))
            }
            "logs" -> {
                requireMethod(req, "GET")
                val cursor = req.query["after"]?.toLongOrNull() ?: -1L
                val slice = backend.logsSince(id, cursor) ?: throw ApiError(404, "No such server.")
                HttpResponse.json(
                    200,
                    JSONObject().put("cursor", slice.cursor).put("reset", slice.reset).put("lines", JSONArray(slice.lines))
                )
            }
            "config" -> config(req, segs, profile)
            "files" -> files(req, segs, profile)
            "backups" -> backups(req, segs, profile)
            else -> throw ApiError(404, "Not found.")
        }
    }

    private fun createServer(req: HttpRequest): HttpResponse {
        val body = jsonBody(req)
        val existing = backend.profiles()
        if (existing.size >= MAX_SERVERS) throw ApiError(409, "That's the most servers this phone can manage ($MAX_SERVERS).")
        if (body.optString("name", "").isBlank()) throw ApiError(400, "Give the server a name.")
        val claimed = ProfileRules.claimedPorts(existing)
        val port = ProfileRules.nextFreePort(claimed)
        // Each server needs its own Bedrock port; offer the next free one unless the request picks one.
        val bedrockPort = ProfileRules.nextFreePort(claimed + port, DEFAULT_BEDROCK_PORT)
        val profile = ProfilePatch.apply(ServerProfile(name = "New server", port = port, bedrockPort = bedrockPort), body)
        validateProfile(profile, existing)
        backend.create(profile)
        return HttpResponse.json(201, serverJson(profile))
    }

    private fun updateServer(req: HttpRequest, current: ServerProfile): HttpResponse {
        val updated = ProfilePatch.apply(current, jsonBody(req)).copy(id = current.id)
        validateProfile(updated, backend.profiles())
        backend.update(updated)
        val active = backend.runtime(current.id)?.isActive == true
        return HttpResponse.json(200, serverJson(updated).put("restartRequired", active && updated != current))
    }

    private fun deleteServer(profile: ServerProfile): HttpResponse {
        if (backend.runtime(profile.id)?.isActive == true) throw ApiError(409, "Stop the server before deleting it.")
        backend.delete(profile.id)
        return HttpResponse.json(200, JSONObject().put("ok", true))
    }

    private fun validateProfile(p: ServerProfile, all: List<ServerProfile>) {
        ProfileRules.validate(p, all)?.let { throw ApiError(400, it) }
        if (p.engineId != null && backend.engine().installed.none { it.id == p.engineId }) {
            throw ApiError(400, "That engine build isn't installed.")
        }
    }

    private fun config(req: HttpRequest, segs: List<String>, profile: ServerProfile): HttpResponse {
        val active = backend.runtime(profile.id)?.isActive == true
        if (segs.size == 3) { // /config/reset
            requireMethod(req, "POST")
            if (active) throw ApiError(409, "Stop the server before resetting its configuration.")
            backend.resetConfig(profile.id)
            return HttpResponse.json(200, JSONObject().put("text", backend.readConfig(profile.id)))
        }
        return when (req.method) {
            "GET" -> HttpResponse.json(200, JSONObject().put("text", backend.readConfig(profile.id)))
            "PUT" -> {
                val text = jsonBody(req).optString("text", null) ?: throw ApiError(400, "Send the file as {\"text\": ...}.")
                if (text.length > 60_000 || text.contains('\u0000')) throw ApiError(400, "That file isn't valid.")
                backend.writeConfig(profile.id, text)
                HttpResponse.json(200, JSONObject().put("text", backend.readConfig(profile.id)).put("restartRequired", active))
            }
            else -> throw ApiError(405, "Method not allowed.")
        }
    }

    // ---- files -----------------------------------------------------------------------------------------------------

    /**
     * Browse and edit a server's folder. Reading and downloading are always allowed; anything that changes files needs
     * the server stopped, because a running server rewrites its own files and deleting a world under it corrupts it.
     */
    private fun files(req: HttpRequest, segs: List<String>, profile: ServerProfile): HttpResponse {
        val fs = backend.files(profile.id) ?: throw ApiError(404, "No such server.")
        val id = profile.id
        fun requireStopped() {
            if (backend.runtime(id)?.isActive == true) throw ApiError(409, "Stop the server before changing its files.")
        }
        try {
            return when (segs.getOrNull(2)) {
                null -> when (req.method) {
                    "GET" -> HttpResponse.json(200, listingJson(fs.list(req.query["path"] ?: "")))
                    "DELETE" -> { requireStopped(); fs.delete(req.query["path"] ?: ""); okJson() }
                    else -> throw ApiError(405, "Method not allowed.")
                }
                "content" -> when (req.method) {
                    "GET" -> {
                        val t = fs.readText(req.query["path"] ?: "")
                        HttpResponse.json(200, JSONObject().put("path", t.path).put("text", t.text).put("size", t.size))
                    }
                    "PUT" -> {
                        requireStopped()
                        val body = jsonBody(req)
                        val text = body.optString("text", null) ?: throw ApiError(400, "Send the file as {\"path\": ..., \"text\": ...}.")
                        fs.writeText(body.optString("path", ""), text)
                        okJson()
                    }
                    else -> throw ApiError(405, "Method not allowed.")
                }
                "mkdir" -> { requireMethod(req, "POST"); requireStopped(); fs.mkdir(jsonBody(req).optString("path", "")); okJson() }
                "rename" -> {
                    requireMethod(req, "POST"); requireStopped()
                    val body = jsonBody(req)
                    fs.rename(body.optString("path", ""), body.optString("name", ""))
                    okJson()
                }
                "download" -> {
                    requireMethod(req, "GET")
                    val f = fs.file(req.query["path"] ?: "")
                    HttpResponse.download(f, f.name)
                }
                else -> throw ApiError(404, "Not found.")
            }
        } catch (e: FileError) {
            throw ApiError(e.status, e.message ?: "Something went wrong with that file.")
        }
    }

    private fun listingJson(l: Listing): JSONObject = JSONObject()
        .put("path", l.path)
        .put("truncated", l.truncated)
        .put("entries", JSONArray().also { arr ->
            l.entries.forEach {
                arr.put(JSONObject().put("name", it.name).put("path", it.path).put("dir", it.isDir).put("size", it.size).put("modified", it.modified))
            }
        })

    private fun okJson() = HttpResponse.json(200, JSONObject().put("ok", true))

    // ---- backups ---------------------------------------------------------------------------------------------------

    private fun backups(req: HttpRequest, segs: List<String>, profile: ServerProfile): HttpResponse {
        val center = backend.backups
        val id = profile.id
        return when (segs.size) {
            2 -> when (req.method) {
                "GET" -> HttpResponse.json(200, backupsJson(id))
                "POST" -> {
                    val label = jsonBody(req).optString("label", "").take(60)
                    center.startBackup(id, label)?.let { throw ApiError(409, it) }
                    HttpResponse.json(202, backupsJson(id))
                }
                else -> throw ApiError(405, "Method not allowed.")
            }
            3 -> {
                requireMethod(req, "DELETE")
                if (center.job(id)?.isRunning == true) throw ApiError(409, "A backup or restore is running. Wait for it to finish.")
                if (!center.delete(id, segs[2])) throw ApiError(404, "No such backup.")
                okJson()
            }
            else -> when (segs[3]) {
                "download" -> {
                    requireMethod(req, "GET")
                    val f = center.file(id, segs[2]) ?: throw ApiError(404, "No such backup.")
                    HttpResponse.download(f, "${profile.name.ifBlank { "server" }}-${f.name}")
                }
                "restore" -> {
                    requireMethod(req, "POST")
                    if (center.file(id, segs[2]) == null) throw ApiError(404, "No such backup.")
                    center.startRestore(id, segs[2])?.let { throw ApiError(409, it) }
                    HttpResponse.json(202, backupsJson(id))
                }
                else -> throw ApiError(404, "Not found.")
            }
        }
    }

    private fun backupsJson(id: String): JSONObject {
        val job = backend.backups.visibleJob(id)
        return JSONObject()
            .put("backups", JSONArray().also { arr ->
                backend.backups.list(id).forEach {
                    arr.put(JSONObject().put("name", it.name).put("size", it.size).put("createdAt", it.createdAt).put("label", it.label))
                }
            })
            .put("job", if (job == null) JSONObject.NULL else JSONObject()
                .put("kind", job.kind.name.lowercase()).put("name", job.backupName ?: JSONObject.NULL)
                .put("progress", job.progress.toDouble()).put("state", job.state.name.lowercase())
                .put("message", job.message ?: JSONObject.NULL))
    }

    // ---- engine ----------------------------------------------------------------------------------------------------

    private fun engine(req: HttpRequest, segs: List<String>): HttpResponse {
        when {
            segs.isEmpty() -> {
                requireMethod(req, "GET")
                return HttpResponse.json(200, engineJson(backend.engine()))
            }
            segs == listOf("refresh") -> {
                requireMethod(req, "POST")
                backend.engineRefresh()
                return HttpResponse.json(200, JSONObject().put("ok", true))
            }
            segs == listOf("download") -> {
                requireMethod(req, "POST")
                val id = jsonBody(req).optString("id", "")
                if (!backend.engine().remote.any { it.id == id }) throw ApiError(404, "Unknown build. Refresh the list first.")
                if (!backend.engineDownload(id)) throw ApiError(409, "A download is already running.")
                return HttpResponse.json(202, JSONObject().put("ok", true))
            }
            segs.size == 1 -> {
                requireMethod(req, "DELETE")
                if (backend.profiles().any { backend.runtime(it.id)?.isActive == true }) {
                    throw ApiError(409, "Stop all servers before removing an engine build.")
                }
                if (!backend.engineDelete(segs[0])) throw ApiError(404, "No such build.")
                return HttpResponse.json(200, JSONObject().put("ok", true))
            }
            else -> throw ApiError(404, "Not found.")
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private fun jsonBody(req: HttpRequest): JSONObject {
        if (req.body.isEmpty()) return JSONObject()
        return try {
            JSONObject(req.bodyText())
        } catch (_: JSONException) {
            throw ApiError(400, "Send a JSON body.")
        }
    }

    companion object {
        const val COOKIE = "dk_session"
        const val MAX_SERVERS = 20

        private val STATIC = mapOf(
            "index.html" to "text/html; charset=utf-8",
            "app.js" to "text/javascript; charset=utf-8",
            "style.css" to "text/css; charset=utf-8",
            "logo.svg" to "image/svg+xml",
        )
    }
}

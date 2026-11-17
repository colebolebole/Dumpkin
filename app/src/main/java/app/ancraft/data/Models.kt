package app.ancraft.data

import org.json.JSONObject
import java.util.UUID

enum class GameMode(val label: String) {
    Survival("Survival"), Creative("Creative"), Adventure("Adventure"), Spectator("Spectator")
}

enum class Difficulty(val label: String) {
    Peaceful("Peaceful"), Easy("Easy"), Normal("Normal"), Hard("Hard")
}

enum class MemoryAction(val label: String) {
    Warn("Warn only"), Restart("Restart server")
}

/** Everything AnCraft remembers about one hosted server. The world itself lives in the server folder. */
data class ServerProfile(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val port: Int = 25565,
    val motd: String = "A Pumpkin server on Android",
    val maxPlayers: Int = 20,
    val viewDistance: Int = 8,
    val simulationDistance: Int = 6,
    val gameMode: GameMode = GameMode.Survival,
    val difficulty: Difficulty = Difficulty.Normal,
    val onlineMode: Boolean = true,
    val bedrock: Boolean = false,
    /** Installed engine build to use; null = newest installed. */
    val engineId: String? = null,
    /** Soft RAM cap in MB, enforced by watching the process's resident memory. 0 = no limit. */
    val memoryLimitMb: Int = 0,
    val memoryAction: MemoryAction = MemoryAction.Warn,
    /** Worker threads for the async runtime / thread pool. 0 = let Pumpkin decide. */
    val cpuThreads: Int = 0,
    val autoRestart: Boolean = false,
    /** Skip the server icon in the server-list response: smaller, faster pings over Wi-Fi. */
    val lightPing: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("port", port)
        put("motd", motd)
        put("maxPlayers", maxPlayers)
        put("viewDistance", viewDistance)
        put("simulationDistance", simulationDistance)
        put("gameMode", gameMode.name)
        put("difficulty", difficulty.name)
        put("onlineMode", onlineMode)
        put("bedrock", bedrock)
        put("engineId", engineId ?: "")
        put("memoryLimitMb", memoryLimitMb)
        put("memoryAction", memoryAction.name)
        put("cpuThreads", cpuThreads)
        put("autoRestart", autoRestart)
        put("lightPing", lightPing)
    }

    companion object {
        fun fromJson(o: JSONObject) = ServerProfile(
            id = o.getString("id"),
            name = o.optString("name", "Server"),
            port = o.optInt("port", 25565),
            motd = o.optString("motd", "A Pumpkin server on Android"),
            maxPlayers = o.optInt("maxPlayers", 20),
            viewDistance = o.optInt("viewDistance", 8),
            simulationDistance = o.optInt("simulationDistance", 6),
            gameMode = GameMode.entries.firstOrNull { it.name == o.optString("gameMode") } ?: GameMode.Survival,
            difficulty = Difficulty.entries.firstOrNull { it.name == o.optString("difficulty") } ?: Difficulty.Normal,
            onlineMode = o.optBoolean("onlineMode", true),
            bedrock = o.optBoolean("bedrock", false),
            engineId = o.optString("engineId").ifBlank { null },
            memoryLimitMb = o.optInt("memoryLimitMb", 0),
            memoryAction = MemoryAction.entries.firstOrNull { it.name == o.optString("memoryAction") } ?: MemoryAction.Warn,
            cpuThreads = o.optInt("cpuThreads", 0),
            autoRestart = o.optBoolean("autoRestart", false),
            lightPing = o.optBoolean("lightPing", true),
        )
    }
}

enum class Status { Stopped, Starting, Running, Stopping, Crashed }

data class RuntimeState(
    val status: Status = Status.Stopped,
    val online: Int = 0,
    val max: Int = 0,
    val version: String? = null,
    /** Resident memory of the server process in MB (0 when not running). */
    val rssMb: Int = 0,
    val startedAt: Long = 0L,
    val error: String? = null,
) {
    val isActive get() = status == Status.Starting || status == Status.Running || status == Status.Stopping
}

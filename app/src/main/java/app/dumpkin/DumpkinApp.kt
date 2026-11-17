package app.dumpkin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.dumpkin.engine.EngineManager
import app.dumpkin.server.ServerManager
import app.dumpkin.web.AppBackend
import app.dumpkin.web.WebController
import kotlinx.coroutines.flow.MutableStateFlow

class DumpkinApp : Application() {
    lateinit var engines: EngineManager
        private set
    lateinit var manager: ServerManager
        private set
    lateinit var web: WebController
        private set

    private val prefs by lazy { getSharedPreferences("dumpkin", Context.MODE_PRIVATE) }

    /** Keep the display on while a server is running (best ping: Android's low-latency Wi-Fi mode needs the screen on). */
    val keepScreenOn by lazy { MutableStateFlow(prefs.getBoolean("keepScreenOn", false)) }

    fun setKeepScreenOn(value: Boolean) {
        keepScreenOn.value = value
        prefs.edit().putBoolean("keepScreenOn", value).apply()
    }

    override fun onCreate() {
        super.onCreate()
        engines = EngineManager(this)
        manager = ServerManager(this, engines)
        web = WebController(
            app = this,
            backend = AppBackend(this, manager, engines),
            assets = { name -> runCatching { assets.open("web/$name").use { it.readBytes() } }.getOrNull() },
            reservedPorts = { manager.profiles.value.map { it.port }.toSet() },
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Running servers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Dumpkin is hosting a Pumpkin server or its web dashboard"
            }
        )
        // If the dashboard was left on, bring it back up with the app.
        web.startIfEnabled()
    }

    companion object {
        const val CHANNEL_ID = "servers"
    }
}

val Context.dumpkin: DumpkinApp get() = applicationContext as DumpkinApp

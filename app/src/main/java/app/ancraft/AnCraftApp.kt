package app.ancraft

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.ancraft.engine.EngineManager
import app.ancraft.server.ServerManager
import kotlinx.coroutines.flow.MutableStateFlow

class AnCraftApp : Application() {
    lateinit var engines: EngineManager
        private set
    lateinit var manager: ServerManager
        private set

    private val prefs by lazy { getSharedPreferences("ancraft", Context.MODE_PRIVATE) }

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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Running servers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while AnCraft is hosting a Pumpkin server"
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "servers"
    }
}

val Context.ancraft: AnCraftApp get() = applicationContext as AnCraftApp

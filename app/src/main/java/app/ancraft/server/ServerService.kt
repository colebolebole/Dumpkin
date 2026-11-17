package app.ancraft.server

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import app.ancraft.AnCraftApp
import app.ancraft.MainActivity
import app.ancraft.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the process, CPU and Wi-Fi awake while at least one server is active. */
class ServerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification(emptyMap()))
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ancraft:servers").apply { acquire() }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Wi-Fi power saving is the biggest source of 100ms+ ping spikes. HIGH_PERF is a no-op on Android 14+,
        // so use the low-latency mode on Android 10+ (it is honoured while the screen is on).
        @Suppress("DEPRECATION")
        val wifiMode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wm.createWifiLock(wifiMode, "ancraft:wifi").apply { acquire() }

        val manager = (application as AnCraftApp).manager
        scope.launch {
            manager.active.collect { active ->
                if (active.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                } else {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIF_ID, buildNotification(active))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) (application as AnCraftApp).manager.stopAll()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun buildNotification(active: Map<String, String>): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP_ALL), PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (active.size) {
            0 -> "AnCraft is getting ready"
            1 -> "${active.values.first()} is hosting"
            else -> "${active.size} servers are hosting"
        }
        return NotificationCompat.Builder(this, AnCraftApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle(title)
            .setContentText(active.values.joinToString(", ").ifEmpty { "Starting up…" })
            .setContentIntent(open)
            .addAction(0, "Stop all", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIF_ID = 42
        const val ACTION_STOP_ALL = "app.ancraft.STOP_ALL"
    }
}

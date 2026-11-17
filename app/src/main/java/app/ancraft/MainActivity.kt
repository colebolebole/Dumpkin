package app.ancraft

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.ancraft.ui.AnCraftTheme
import app.ancraft.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = ancraft
        setContent {
            val keepOn by app.keepScreenOn.collectAsState()
            val active by app.manager.active.collectAsState()
            val hold = keepOn && active.isNotEmpty()
            DisposableEffect(hold) {
                if (hold) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            AnCraftTheme {
                AppRoot()
            }
        }
    }
}

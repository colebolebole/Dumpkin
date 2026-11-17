package app.dumpkin

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.dumpkin.ui.DumpkinTheme
import app.dumpkin.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = dumpkin
        setContent {
            val keepOn by app.keepScreenOn.collectAsState()
            val active by app.manager.active.collectAsState()
            val hold = keepOn && active.isNotEmpty()
            DisposableEffect(hold) {
                if (hold) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            DumpkinTheme {
                AppRoot()
            }
        }
    }
}

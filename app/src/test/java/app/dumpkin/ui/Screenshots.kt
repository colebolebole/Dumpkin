package app.dumpkin.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.dumpkin.DumpkinApp
import app.dumpkin.data.CpuCluster
import app.dumpkin.data.DeviceSnapshot
import app.dumpkin.data.GameMode
import app.dumpkin.data.RuntimeState
import app.dumpkin.data.ServerProfile
import app.dumpkin.data.Status
import app.dumpkin.data.Thermal
import app.dumpkin.data.WifiBand
import app.dumpkin.engine.Channel
import app.dumpkin.engine.EngineUiState
import app.dumpkin.engine.InstalledEngine
import app.dumpkin.engine.RemoteEngine
import app.dumpkin.server.ServerManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real app screens with sample data to PNG files for the README. Opt in, because it is slow and
 * rewrites files in the repo:
 *
 *     DUMPKIN_SCREENSHOTS=1 ./gradlew testDebugUnitTest --tests '*Screenshots*'
 *
 * Files go to `docs/screenshots` (override with DUMPKIN_SCREENSHOT_DIR). Material You colours are switched off so
 * the screens show Dumpkin's own pumpkin palette instead of whatever wallpaper the machine happens to have.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = DumpkinApp::class, qualifiers = "w412dp-h892dp-night-xhdpi")
class Screenshots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getenv("DUMPKIN_SCREENSHOT_DIR") ?: "../docs/screenshots")

    private val phone = DeviceSnapshot(
        model = "Pixel 8", soc = "Google Tensor G3", cores = 9, abi = "arm64-v8a",
        clusters = listOf(CpuCluster(1, 2_910_000), CpuCluster(4, 2_370_000), CpuCluster(4, 1_700_000)),
        androidVersion = "Android 15 (API 35)", batteryPercent = 80, charging = true, thermal = Thermal.Cool,
        totalMb = 7_270, availMb = 1_950, address = "192.168.1.205", wifi = WifiBand.Ghz5,
        batteryUnrestricted = false, notificationsAllowed = true,
    )

    @Before fun optIn() = assumeTrue("set DUMPKIN_SCREENSHOTS=1 to render screenshots", System.getenv("DUMPKIN_SCREENSHOTS") == "1")

    private fun app() = ApplicationProvider.getApplicationContext<DumpkinApp>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> setField(target: Any, name: String, value: Any?) {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        f.set(target, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(target: Any, name: String): T {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(target) as T
    }

    private fun seed() {
        val ctx = app()
        ServerManager.addressSource = { listOf("192.168.1.205") }
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(am).setMemoryInfo(ActivityManager.MemoryInfo().apply { totalMem = 7_270L * 1_048_576; availMem = 1_950L * 1_048_576 })

        // Engines: a stable build installed, and the usual list "available on GitHub".
        val stable = RemoteEngine(Channel.Stable, "0.2.2+26.3-26.60", "0.2.2", "pumpkin-aarch64-android", "https://example.invalid/p", 31_400_000, "2026-11-08T10:00:00Z")
        val canary = RemoteEngine(Channel.Canary, "canary", "Canary 2026-11-16", "pumpkin-aarch64-android", "https://example.invalid/p", 30_900_000, "2026-11-16T03:00:00Z")
        val nightly = RemoteEngine(Channel.Nightly, "nightly", "Nightly 2026-11-16", "pumpkin-aarch64-android", "https://example.invalid/p", 28_700_000, "2026-11-16T04:00:00Z")
        setField<Any>(ctx.engines, "supported", true)
        getField<MutableStateFlow<EngineUiState>>(ctx.engines, "_state").value = EngineUiState(
            installed = listOf(InstalledEngine(stable.id, "0.2.2 (Stable)", "Stable", 31_400_000, File("pumpkin"))),
            remote = listOf(stable, canary, nightly),
        )

        val mgr = ctx.manager
        val smp = ServerProfile(id = "smp", name = "Survival SMP", port = 25565, motd = "Welcome to the SMP", maxPlayers = 20, memoryLimitMb = 2048)
        val creative = ServerProfile(id = "cre", name = "Creative Build", port = 25566, gameMode = GameMode.Creative, bedrock = true, bedrockPort = 19132)
        val hard = ServerProfile(id = "hrd", name = "Hardcore Test", port = 25567, bedrock = true, bedrockPort = 19133)
        listOf(smp, creative, hard).forEach(mgr::create)

        val now = System.currentTimeMillis()
        mgr.runtime("smp")!!.apply {
            state.value = RuntimeState(Status.Running, online = 3, max = 20, version = "1.21.10", rssMb = 612, startedAt = now - 2 * 3_600_000L - 14 * 60_000L)
            logs.value = listOf(
                "10:04:11 INFO  Starting Pumpkin on 0.0.0.0:25565",
                "10:04:12 INFO  Loading world \"world\"",
                "10:04:13 INFO  Done (1.84s)! For help, type /help",
                "10:21:40 INFO  Steve joined the game",
                "10:22:05 INFO  <Steve> anyone up for a nether run?",
                "10:22:31 INFO  Alex joined the game",
                "10:22:44 INFO  <Alex> yes! grabbing my pickaxe",
                "10:35:02 INFO  Jamie joined the game",
                "10:41:19 INFO  <Jamie> the new chunks load so fast on this phone",
                "10:41:50 INFO  <Steve> pumpkin is flying",
            )
        }
        mgr.runtime("cre")!!.state.value =
            RuntimeState(Status.Running, online = 0, max = 20, version = "1.21.10", rssMb = 388, startedAt = now - 38 * 60_000L)
        getField<MutableStateFlow<Map<String, String>>>(mgr, "_active").value = mapOf("smp" to smp.name, "cre" to creative.name)
    }

    /** Compose tests wait for animations to finish, but Dumpkin's status blob spins forever, so drive the clock by hand. */
    private fun settle(ms: Long = 1_200) {
        repeat((ms / 100).toInt()) {
            rule.mainClock.advanceTimeBy(100)
            rule.waitForIdle() // runs the posted main-thread work (recomposition, results from background reads)
            Thread.sleep(25) // real time for background work (device readings) to finish
        }
    }

    private fun click(node: SemanticsNodeInteraction) {
        node.performClick()
        settle()
    }

    /** Bottom navigation labels collide with screen titles ("Engine"), so pick the lowest match on screen. */
    private fun nav(label: String) {
        val all = rule.onAllNodesWithText(label)
        val nodes = all.fetchSemanticsNodes()
        val lowest = nodes.indices.maxByOrNull { nodes[it].boundsInRoot.top }!!
        click(all[lowest])
    }

    /**
     * Draws the activity's window into a bitmap. (Compose's own captureToImage waits for a hardware frame that
     * Robolectric never delivers, but drawing the view hierarchy directly renders the real Compose output.)
     */
    private fun shot(name: String) {
        settle(600)
        rule.waitForIdle()
        outDir.mkdirs()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Progress log for diagnosing hangs (test stdout is only shown when a run finishes). */
    private fun step(name: String) {
        System.getenv("DUMPKIN_STEP_LOG")?.let { File(it).appendText("$name\n") }
    }

    @Test fun renderAllScreens() {
        seed()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDeviceSnapshotReader provides { phone }) {
                DumpkinTheme(dynamicColor = false) {
                    // Stand-in for the system bars the test window doesn't have, so nothing sits under the top edge.
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = 32.dp),
                    ) { AppRoot() }
                }
            }
        }
        settle()

        shot("servers")

        nav("Engine")
        shot("engine")

        nav("Tuning")
        settle(2_500)
        shot("tuning")

        // The checks sit below the device card: scroll to them and open the first one.
        rule.onNode(hasScrollAction()).performScrollToIndex(2)
        settle()
        click(rule.onNodeWithText("Battery optimisation"))
        shot("tuning-checks")

        nav("Servers")
        click(rule.onNodeWithText("Survival SMP"))
        shot("console")

        click(rule.onNodeWithText("Settings"))
        shot("settings")

        // A server with Bedrock on: scroll the settings down to its Bedrock port.
        step("back")
        click(rule.onNodeWithContentDescription("Back"))
        step("open creative")
        click(rule.onNodeWithText("Creative Build"))
        step("settings tab")
        click(rule.onNodeWithText("Settings"))
        step("scroll")
        for (i in 0 until 10) {
            val field = rule.onAllNodesWithText("Bedrock port").fetchSemanticsNodes().firstOrNull()
            if (field != null && field.boundsInRoot.top > 250f && field.boundsInRoot.bottom < 1500f) break
            rule.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
            settle(400)
        }
        step("shot")
        shot("bedrock")
        step("done")
    }
}

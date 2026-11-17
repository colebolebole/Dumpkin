package app.dumpkin.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.dumpkin.engine.Channel
import app.dumpkin.engine.EngineUiState
import app.dumpkin.engine.InstalledEngine
import app.dumpkin.engine.RemoteEngine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Smoke tests: the Engine tab must compose for every state the downloader can produce. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w420dp-h3000dp")
class EngineContentTest {
    @get:Rule val rule = createComposeRule()

    private fun remote(ch: Channel, tag: String, updated: String) =
        RemoteEngine(ch, tag, if (ch == Channel.Stable) tag else "${ch.label} ${updated.take(10)}",
            "pumpkin-aarch64-android", "https://example.invalid/pumpkin", 31_000_000, updated)

    private val stable = remote(Channel.Stable, "0.2.2+26.3-26.60", "2026-11-08T10:00:00Z")
    private val canary = remote(Channel.Canary, "canary", "2026-11-16T03:00:00Z")
    private val nightly = remote(Channel.Nightly, "nightly", "2026-11-16T04:00:00Z")

    private fun show(state: EngineUiState, supported: Boolean = true) = rule.setContent {
        DumpkinTheme {
            EngineContent(state, supported, serversActive = false, onCheck = {}, onDownload = {}, onDelete = {})
        }
    }

    @Test fun firstOpenWhileChecking() {
        show(EngineUiState(checking = true))
        rule.onNodeWithText("Installed").assertExists()
    }

    /** The reported crash: a build that is both installed and listed as available shared a LazyColumn key. */
    @Test fun installedBuildAlsoListedAsAvailable() {
        val installed = InstalledEngine(stable.id, stable.label, "Stable", 31_000_000, File("pumpkin"))
        show(EngineUiState(installed = listOf(installed), remote = listOf(stable, canary, nightly)))
        rule.onNodeWithText("Available on GitHub").assertExists()
    }

    @Test fun downloadingAndErrorBanner() {
        show(EngineUiState(remote = listOf(stable, canary, nightly), downloadingId = stable.id, progress = 0.4f,
            message = "Download failed: HTTP 404"), supported = false)
        rule.onNodeWithText("Unsupported CPU").assertExists()
    }
}

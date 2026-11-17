package app.dumpkin.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.dumpkin.data.CpuCluster
import app.dumpkin.data.DeviceSnapshot
import app.dumpkin.data.Thermal
import app.dumpkin.data.TuningAction
import app.dumpkin.data.WifiBand
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Smoke tests: the Device tuning tab must compose for healthy and unhealthy phones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w420dp-h3000dp")
class TuningContentTest {
    @get:Rule val rule = createComposeRule()

    private val phone = DeviceSnapshot(
        model = "OnePlus CPH2415", soc = "Snapdragon 8+ Gen 1 (SM8475)", cores = 8, abi = "arm64-v8a",
        clusters = listOf(CpuCluster(1, 3_187_200), CpuCluster(3, 2_745_600), CpuCluster(4, 1_996_800)),
        androidVersion = "Android 15 (API 35)", batteryPercent = 80, charging = true, thermal = Thermal.Cool,
        totalMb = 7270, availMb = 1950, address = "192.168.1.205", wifi = WifiBand.Ghz5,
        batteryUnrestricted = true, notificationsAllowed = true,
    )

    private fun show(s: DeviceSnapshot?, onAction: (TuningAction) -> Unit = {}) = rule.setContent {
        DumpkinTheme { TuningContent(s, keepScreenOn = false, onKeepScreenOn = {}, onAction = onAction) }
    }

    @Test fun loadingBeforeTheFirstReading() {
        show(null)
        rule.onNodeWithText("Device tuning").assertExists()
    }

    @Test fun healthyPhoneShowsItsSpecsAndNoWarnings() {
        show(phone)
        rule.onNodeWithText("This phone is ready to host").assertExists()
        rule.onNodeWithText("Snapdragon 8+ Gen 1 (SM8475)").assertExists()
        rule.onNodeWithText("1x 3.2 GHz + 3x 2.7 GHz + 4x 2.0 GHz").assertExists()
    }

    @Test fun problemsAreCountedAndOpenToAnAction() {
        var asked: TuningAction? = null
        show(phone.copy(batteryUnrestricted = false, wifi = WifiBand.Ghz24)) { asked = it }
        rule.onNodeWithText("2 settings could keep your server from running smoothly. Tap a card for details.").assertExists()
        rule.onNodeWithText("Battery optimisation").performClick()
        rule.onNodeWithText("Allow").performClick()
        assertEquals(TuningAction.RequestBattery, asked)
    }
}

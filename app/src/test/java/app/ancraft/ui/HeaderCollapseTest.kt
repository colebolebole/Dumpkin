package app.ancraft.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w420dp-h800dp")
class HeaderCollapseTest {
    @get:Rule val rule = createComposeRule()

    private fun scroll(c: HeaderCollapse, dy: Float) =
        c.connection.onPreScroll(Offset(0f, dy), NestedScrollSource.UserInput)

    @Test fun scrollingDownCollapsesTheHeaderFirstAndPassesTheRest() {
        val c = HeaderCollapse().also { it.heightPx = 100 }
        assertEquals(-60f, scroll(c, -60f).y, 0.001f)      // header takes it all
        assertEquals(-40f, scroll(c, -70f).y, 0.001f)      // only 40px left to collapse, 30px reaches the list
        assertEquals(-100f, c.offset, 0.001f)
        assertEquals(0f, scroll(c, -50f).y, 0.001f)        // fully hidden: list scrolls freely
    }

    @Test fun scrollingUpBringsTheHeaderBackImmediately() {
        val c = HeaderCollapse().also { it.heightPx = 100; it.offset = -100f }
        assertEquals(30f, scroll(c, 30f).y, 0.001f)
        assertEquals(-70f, c.offset, 0.001f)
        assertEquals(70f, scroll(c, 200f).y, 0.001f)       // clamps at fully shown, rest goes to the list
        assertEquals(0f, c.offset, 0.001f)
    }

    @Test fun disabledLeavesTheHeaderAlone() {
        val c = HeaderCollapse().also { it.heightPx = 100 }
        c.enabled = false
        assertEquals(0f, scroll(c, -80f).y, 0.001f)
        assertEquals(0f, c.offset, 0.001f)
    }

    @Test fun disablingRestoresTheHeader() {
        val c = HeaderCollapse().also { it.heightPx = 100; it.offset = -100f }
        c.enabled = false
        assertEquals(0f, c.offset, 0.001f)
    }

    /** End to end: a real swipe up rolls the header away, a swipe down brings it back. */
    @Test fun swipingTheContentRollsTheHeaderUpAndBack() {
        val c = HeaderCollapse()
        rule.setContent {
            Column(Modifier.fillMaxSize().nestedScroll(c.connection)) {
                Box(Modifier.testTag("header").collapsing(c)) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                Column(Modifier.testTag("list").weight(1f).verticalScroll(rememberScrollState())) {
                    repeat(40) { Box(Modifier.fillMaxWidth().height(60.dp)) }
                }
            }
        }
        rule.onNodeWithTag("header").assertHeightIsEqualTo(100.dp)
        rule.onNodeWithTag("list").performTouchInput { swipeUp() }
        rule.onNodeWithTag("header").assertHeightIsEqualTo(0.dp)
        rule.onNodeWithTag("list").performTouchInput { swipeDown() }
        rule.onNodeWithTag("header").assertHeightIsEqualTo(100.dp)
    }
}

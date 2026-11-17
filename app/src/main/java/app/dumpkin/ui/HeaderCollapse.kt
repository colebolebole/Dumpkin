package app.dumpkin.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * "Enter always" collapsing header: scrolling content down rolls the header up out of view, and scrolling back up
 * brings it straight back, before the content itself starts scrolling.
 *
 * Attach [connection] with `Modifier.nestedScroll` on a parent of the scrolling content and put [collapsing] on the header.
 */
class HeaderCollapse {
    /** Measured height of the header, set by [collapsing]. */
    var heightPx: Int = 0
        internal set

    /** 0 = fully shown, -heightPx = fully hidden. */
    var offset by mutableFloatStateOf(0f)

    /** When false the header is left alone (e.g. on the console tab, or while the keyboard is open). */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) offset = 0f
        }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || heightPx <= 0) return Offset.Zero
            val target = (offset + available.y).coerceIn(-heightPx.toFloat(), 0f)
            val used = target - offset
            offset = target
            return Offset(0f, used)
        }
    }
}

/** Shrinks the layout height as [HeaderCollapse.offset] goes negative, sliding the content up with it. */
fun Modifier.collapsing(state: HeaderCollapse): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        state.heightPx = placeable.height
        val shift = state.offset.coerceIn(-placeable.height.toFloat(), 0f).roundToInt()
        layout(placeable.width, (placeable.height + shift).coerceAtLeast(0)) {
            placeable.placeRelative(0, shift)
        }
    }

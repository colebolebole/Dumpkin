package app.dumpkin.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import app.dumpkin.data.RuntimeState
import app.dumpkin.data.Status

fun statusLabel(s: Status) = when (s) {
    Status.Stopped -> "Offline"
    Status.Starting -> "Starting…"
    Status.Running -> "Online"
    Status.Stopping -> "Stopping…"
    Status.Crashed -> "Crashed"
}

/** What to show for a server: a restart in progress reads as one "starting" state, not stopping, offline, starting. */
val RuntimeState.displayStatus: Status get() = if (restarting) Status.Starting else status

fun statusLabel(st: RuntimeState) = if (st.restarting) "Restarting…" else statusLabel(st.status)

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%dh %02dm".format(h, m) else if (m > 0) "%dm %02ds".format(m, sec) else "${sec}s"
}


fun formatMb(mb: Int): String = if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "$mb MB"

fun formatBytes(b: Long): String = when {
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

/** A morphing, slowly rotating cookie that doubles as the status indicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusBlob(status: Status, size: Dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        Status.Running -> cs.primaryContainer to cs.onPrimaryContainer
        Status.Starting, Status.Stopping -> cs.tertiaryContainer to cs.onTertiaryContainer
        Status.Crashed -> cs.errorContainer to cs.onErrorContainer
        Status.Stopped -> cs.surfaceContainerHighest to cs.onSurfaceVariant
    }
    val spinning = status == Status.Running || status == Status.Starting || status == Status.Stopping
    val rotation = if (spinning) {
        rememberInfiniteTransition(label = "blob").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(if (status == Status.Running) 28_000 else 5_000, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "rotation",
        ).value
    } else 0f
    val shape = when (status) {
        Status.Running -> MaterialShapes.Cookie9Sided.toShape()
        Status.Starting, Status.Stopping -> MaterialShapes.SoftBurst.toShape()
        Status.Crashed -> MaterialShapes.Pentagon.toShape()
        Status.Stopped -> MaterialShapes.Cookie4Sided.toShape()
    }
    val icon = when (status) {
        Status.Running -> Icons.Rounded.Cloud
        Status.Stopped -> Icons.Rounded.CloudOff
        Status.Crashed -> Icons.Rounded.Error
        else -> Icons.Rounded.PowerSettingsNew
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { rotationZ = rotation }
                .clip(shape)
                .background(bg)
        )
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.42f))
    }
}

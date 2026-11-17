package app.dumpkin.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.dumpkin.data.DeviceSnapshot
import app.dumpkin.data.DeviceTuning
import app.dumpkin.data.Thermal
import app.dumpkin.data.TuningAction
import app.dumpkin.data.TuningItem
import app.dumpkin.data.readDeviceSnapshot
import app.dumpkin.dumpkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How the tab reads the phone. Tests (and README screenshots) swap in a fixed snapshot. */
val LocalDeviceSnapshotReader = staticCompositionLocalOf<(Context) -> DeviceSnapshot> { ::readDeviceSnapshot }

/** The "Device tuning" tab: what this phone can do, and the settings that decide how well it hosts. */
@Composable
fun TuningScreen() {
    val ctx = LocalContext.current
    val app = ctx.dumpkin
    val keepOn by app.keepScreenOn.collectAsState()
    var snapshot by remember { mutableStateOf<DeviceSnapshot?>(null) }
    val reader = LocalDeviceSnapshotReader.current

    // Refresh while the app is on screen: memory, battery and heat change, and permissions flip when the user
    // comes back from a system settings page.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                snapshot = withContext(Dispatchers.IO) { runCatching { reader(ctx) }.getOrNull() } ?: snapshot
                delay(2_000)
            }
        }
    }

    TuningContent(
        snapshot = snapshot,
        keepScreenOn = keepOn,
        onKeepScreenOn = { app.setKeepScreenOn(it) },
        onAction = { openSystemScreen(ctx, it) },
    )
}

private fun openSystemScreen(ctx: Context, action: TuningAction) {
    val pkg = Uri.parse("package:${ctx.packageName}")
    val intent = when (action) {
        TuningAction.RequestBattery -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        TuningAction.BatterySettings -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        TuningAction.NotificationSettings ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        TuningAction.WifiSettings -> Intent(Settings.ACTION_WIFI_SETTINGS)
        TuningAction.DeveloperOptions -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        TuningAction.AppInfo -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        TuningAction.None -> return
    }
    // Some phones hide developer options or a particular page; failing quietly beats crashing.
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Stateless so tests can render it with any phone and any set of problems. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TuningContent(
    snapshot: DeviceSnapshot?,
    keepScreenOn: Boolean,
    onKeepScreenOn: (Boolean) -> Unit,
    onAction: (TuningAction) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Device tuning") },
                subtitle = { Text("Get the most out of this phone") },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        if (snapshot == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
            return@Scaffold
        }
        val items = remember(snapshot) { DeviceTuning.items(snapshot) }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "banner") { Banner(items.count { !it.ok }) }
            item(key = "device") { DeviceCard(snapshot) }
            item(key = "h-checks") { SectionHeader("Checks") }
            items(items.size, key = { "check:${items[it].id}" }) { i -> TuningCard(items[i], onAction) }
            item(key = "h-hosting") { SectionHeader("Hosting") }
            item(key = "hosting") { HostingCard(keepScreenOn, onKeepScreenOn) }
            item(key = "h-system") { SectionHeader("Developer options & system") }
            item(key = "system") { SystemShortcuts(onAction) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun Banner(problems: Int) {
    val cs = MaterialTheme.colorScheme
    val good = problems == 0
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (good) cs.primaryContainer else cs.tertiaryContainer,
            contentColor = if (good) cs.onPrimaryContainer else cs.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (good) Icons.Rounded.CheckCircle else Icons.Rounded.Lightbulb, null)
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    if (good) "This phone is ready to host" else "Get the most out of this phone",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (good) "Nothing is likely to hold your servers back right now."
                    else "$problems ${if (problems == 1) "setting" else "settings"} could keep your server from running smoothly. Tap a card for details.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceCard(s: DeviceSnapshot) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).background(cs.secondaryContainer, MaterialShapes.Cookie6Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PhoneAndroid, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.size(16.dp))
                Column {
                    Text("This device", style = MaterialTheme.typography.headlineSmall)
                    Text(s.model, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Memory, null, tint = cs.primary, modifier = Modifier.size(40.dp).padding(top = 4.dp))
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(s.soc, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOf("${s.cores} cores", s.abi).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    if (s.clusters.isNotEmpty()) {
                        Text(
                            DeviceTuning.formatClusters(s.clusters),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(Icons.Rounded.Android, s.androidVersion)
                if (s.batteryPercent >= 0) {
                    Chip(
                        if (s.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                        "${s.batteryPercent}%" + if (s.charging) " · charging" else "",
                    )
                }
                s.thermal?.let { Chip(Icons.Rounded.Thermostat, it.label, warn = it == Thermal.Hot) }
                s.address?.let { Chip(Icons.Rounded.Wifi, it) }
            }

            MemorySection(s)
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, warn: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (warn) cs.errorContainer else cs.surfaceContainerHighest,
        contentColor = if (warn) cs.onErrorContainer else cs.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun MemorySection(s: DeviceSnapshot) {
    val cs = MaterialTheme.colorScheme
    val advice = remember(s.totalMb) { DeviceTuning.memoryAdvice(s.totalMb) }
    val used = (s.totalMb - s.availMb).coerceIn(0, s.totalMb)
    // Weights must be positive, so keep a sliver of each segment.
    val usedWeight = (used.toFloat() / s.totalMb.coerceAtLeast(1)).coerceIn(0.02f, 0.98f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Memory", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(usedWeight).height(14.dp).background(cs.primary, CircleShape))
            Box(Modifier.weight(1f - usedWeight).height(14.dp).background(cs.surfaceContainerHighest, CircleShape))
        }
        Text(
            "${formatMb(used)} in use · ${formatMb(s.availMb)} free · ${formatMb(s.totalMb)} total",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Servers can use up to ${formatMb(advice.maxMb)} (${advice.maxPercent}%). Above ${formatMb(advice.cautionAboveMb)} " +
                "is not recommended; ${formatMb(advice.comfortableMb)} and below is comfortable.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        s.address?.let {
            Text("Network address: $it", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TuningCard(item: TuningItem, onAction: (TuningAction) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    Card(
        onClick = { open = !open },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (item.ok) cs.surfaceContainer else cs.tertiaryContainer.copy(alpha = 0.55f),
        ),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = if (item.ok) "OK" else "Needs attention",
                    tint = if (item.ok) cs.primary else cs.tertiary,
                )
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            if (open) {
                Text(item.detail, style = MaterialTheme.typography.bodyMedium)
                if (item.action != TuningAction.None) {
                    Button(onClick = { onAction(item.action) }, shapes = ButtonDefaults.shapes()) { Text(item.actionLabel) }
                }
            }
        }
    }
}

@Composable
private fun HostingCard(keepScreenOn: Boolean, onKeepScreenOn: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep screen on while hosting", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Android only gives apps its low-latency Wi-Fi mode while the screen is on, so this gives players the lowest ping. Costs battery; plug the phone in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(16.dp))
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOn)
            }
            Text(
                "Tips: use 5 GHz Wi-Fi, keep the phone cool, and lower view distance in a server's settings. Nightly builds are fully optimised; Canary skips link-time optimisation and runs slower.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SystemShortcuts(onAction: (TuningAction) -> Unit) {
    val rows = listOf(
        Triple(Icons.Rounded.BatteryChargingFull, "Unrestricted apps list" to "Every app allowed to ignore battery limits", TuningAction.BatterySettings),
        Triple(Icons.Rounded.Wifi, "Wi-Fi settings" to "Switch networks or bands", TuningAction.WifiSettings),
        Triple(Icons.Rounded.Code, "Developer options" to "Only on phones where it's switched on", TuningAction.DeveloperOptions),
        Triple(Icons.Rounded.Info, "Dumpkin app info" to "Permissions, storage and notifications", TuningAction.AppInfo),
    )
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            rows.forEach { (icon, text, action) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onAction(action) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text.first, style = MaterialTheme.typography.bodyLarge)
                        Text(text.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

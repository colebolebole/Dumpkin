package app.ancraft.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ancraft.ancraft
import app.ancraft.engine.EngineUiState
import app.ancraft.engine.InstalledEngine
import app.ancraft.engine.RemoteEngine

@Composable
fun EngineScreen() {
    val app = LocalContext.current.ancraft
    val state by app.engines.state.collectAsState()
    val active by app.manager.active.collectAsState()
    val keepOn by app.keepScreenOn.collectAsState()

    LaunchedEffect(Unit) { if (state.remote.isEmpty()) app.engines.checkRemote() }

    EngineContent(
        state = state,
        supported = app.engines.supported,
        serversActive = active.isNotEmpty(),
        keepScreenOn = keepOn,
        onCheck = { app.engines.checkRemote() },
        onDownload = { app.engines.download(it) },
        onDelete = { app.engines.delete(it) },
        onKeepScreenOn = { app.setKeepScreenOn(it) },
    )
}

/** Stateless so it can be rendered in tests with any combination of installed/available builds. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EngineContent(
    state: EngineUiState,
    supported: Boolean,
    serversActive: Boolean,
    keepScreenOn: Boolean,
    onCheck: () -> Unit,
    onDownload: (RemoteEngine) -> Unit,
    onDelete: (String) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmDelete by remember { mutableStateOf<InstalledEngine?>(null) }
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(ctx)) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Engine") },
                subtitle = { Text("Pumpkin server builds") },
                actions = {
                    if (state.checking) ContainedLoadingIndicator(Modifier.size(40.dp).padding(end = 8.dp))
                    else IconButton(onClick = onCheck) { Icon(Icons.Rounded.Refresh, "Check for updates") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!supported) {
                item {
                    InfoCard(
                        "Unsupported CPU",
                        "The Pumpkin Android build is arm64 only. Emulators and x86 devices can't run it.",
                        error = true,
                    )
                }
            }
            state.message?.let { msg -> item { InfoCard("Heads up", msg, error = true) } }

            item { Header("Installed") }
            if (state.installed.isEmpty()) {
                item { Text("Nothing installed yet. Download a build below.", style = MaterialTheme.typography.bodyMedium) }
            }
            // Keys must be unique across the whole LazyColumn: an installed build and its "available"
            // twin share the same id, so each list gets its own prefix.
            items(state.installed, key = { "installed:${it.id}" }) { e ->
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(e.channel, formatBytes(e.sizeBytes)).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { confirmDelete = e }, enabled = !serversActive) {
                            Icon(Icons.Rounded.Delete, "Delete ${e.label}")
                        }
                    }
                }
            }

            item { Header("Available on GitHub") }
            items(state.remote, key = { "remote:${it.id}" }) { r ->
                RemoteCard(
                    r = r,
                    installed = state.installed.any { it.id == r.id },
                    downloading = state.downloadingId == r.id,
                    progress = state.progress,
                    busy = state.downloadingId != null,
                    enabled = supported,
                    onDownload = { onDownload(r) },
                )
            }
            if (state.remote.isEmpty() && !state.checking && state.message == null) {
                item { Text("Tap refresh to look for builds.", style = MaterialTheme.typography.bodyMedium) }
            }

            item { Header("Keep servers alive") }
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Android may freeze background apps to save battery. Allowing AnCraft to ignore battery optimisation stops your server from dropping out when the screen is off.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (batteryOk) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null)
                                Spacer(Modifier.size(8.dp))
                                Text("Unrestricted — you're all set", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(
                                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                Uri.parse("package:${ctx.packageName}"),
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                    batteryOk = isIgnoringBattery(ctx)
                                },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Icon(Icons.Rounded.BatteryChargingFull, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Allow")
                            }
                        }
                    }
                }
            }

            item { Header("Ping and performance") }
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Keep screen on while hosting", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Android only gives apps its low-latency Wi-Fi mode while the screen is on, so this gives players the lowest ping. Costs battery; plug the phone in.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.size(16.dp))
                            Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOn)
                        }
                        Text(
                            "Tips: use 5 GHz Wi-Fi, keep the phone cool, and lower view distance in a server's settings. Nightly builds are fully optimised; Canary skips link-time optimisation and runs slower.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { Header("About") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "AnCraft is an unofficial host for Pumpkin, a Minecraft server written in Rust (GPL-3.0). " +
                            "Pumpkin is still under heavy development: not every vanilla mechanic exists yet and worlds can break between versions, so keep backups. " +
                            "Not affiliated with Mojang or Microsoft.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://pumpkinmc.org/"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("pumpkinmc.org") }
                }
            }
        }
    }

    confirmDelete?.let { e ->
        ConfirmDialog(
            title = "Delete ${e.label}?",
            body = "Servers using this build will fall back to the newest installed one.",
            confirm = "Delete",
            onDismiss = { confirmDelete = null },
            onConfirm = { onDelete(e.id); confirmDelete = null },
        )
    }
}

private fun isIgnoringBattery(ctx: android.content.Context): Boolean =
    (ctx.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(ctx.packageName)

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun InfoCard(title: String, body: String, error: Boolean) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoteCard(
    r: RemoteEngine,
    installed: Boolean,
    downloading: Boolean,
    progress: Float,
    busy: Boolean,
    enabled: Boolean,
    onDownload: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${r.channel.label} · ${r.tag}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(formatBytes(r.size), r.updated.take(10)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    installed -> Icon(Icons.Rounded.Check, "Installed", tint = MaterialTheme.colorScheme.primary)
                    else -> Button(
                        onClick = onDownload,
                        enabled = enabled && !busy,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Get")
                    }
                }
            }
            Text(r.channel.blurb, style = MaterialTheme.typography.bodySmall)
            if (downloading) {
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

package app.ancraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ancraft.ancraft
import app.ancraft.data.GameMode
import app.ancraft.data.ServerProfile
import app.ancraft.data.Status
import app.ancraft.server.ServerManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit, onOpenEngine: () -> Unit, snack: (String) -> Unit) {
    val app = LocalContext.current.ancraft
    val profiles by app.manager.profiles.collectAsState()
    val active by app.manager.active.collectAsState()
    val engine by app.engines.state.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showCreate by remember { mutableStateOf(false) }
    val ips = remember(active) { ServerManager.localAddresses() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("AnCraft") },
                subtitle = {
                    Text(
                        when {
                            active.isNotEmpty() -> "${active.size} online" + (ips.firstOrNull()?.let { " · $it" } ?: "")
                            else -> "Host Pumpkin servers from your phone"
                        }
                    )
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New server") },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (engine.installed.isEmpty()) {
                item {
                    EngineBanner(supported = app.engines.supported, onClick = onOpenEngine)
                }
            }
            if (profiles.isEmpty()) {
                item { EmptyState() }
            }
            items(profiles, key = { it.id }) { p ->
                val rt = remember(p.id) { app.manager.runtime(p.id) }
                if (rt != null) {
                    ServerCard(
                        profile = p,
                        rt = rt,
                        onOpen = { onOpen(p.id) },
                        onToggle = {
                            if (rt.state.value.isActive) app.manager.stop(p.id)
                            else app.manager.start(p.id)?.let(snack)
                        },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateServerSheet(
            takenPorts = profiles.map { it.port }.toSet(),
            onDismiss = { showCreate = false },
            onCreate = {
                app.manager.create(it)
                showCreate = false
                onOpen(it.id)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EngineBanner(supported: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (supported) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.errorContainer
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (supported) "Get the Pumpkin engine" else "This device can't run Pumpkin",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                if (supported) "Servers need the Pumpkin server binary. It's a one-time download from the official GitHub releases."
                else "The Android build of Pumpkin is arm64 only, and this device reports a different CPU.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (supported) {
                Button(onClick = onClick, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Open Engine")
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusBlob(Status.Stopped, 120.dp)
        Text("No servers yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Create one and your phone becomes a Minecraft host.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerCard(
    profile: ServerProfile,
    rt: app.ancraft.server.ServerRuntime,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val st by rt.state.collectAsState()
    val running = st.status == Status.Running
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (running) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusBlob(st.status, 64.dp)
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    statusLabel(st.status) + if (running) " · ${st.online}/${st.max} players" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Port ${profile.port} · ${profile.gameMode.label} · ${profile.difficulty.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (st.status) {
                Status.Starting, Status.Stopping -> ContainedLoadingIndicator(Modifier.size(56.dp))
                else -> FilledIconButton(
                    onClick = onToggle,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        if (st.isActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (st.isActive) "Stop" else "Start",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CreateServerSheet(
    takenPorts: Set<Int>,
    onDismiss: () -> Unit,
    onCreate: (ServerProfile) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("My Pumpkin Server") }
    var portText by remember {
        mutableStateOf(generateSequence(25565) { it + 1 }.first { it !in takenPorts }.toString())
    }
    var mode by remember { mutableStateOf(GameMode.Survival) }
    val port = portText.toIntOrNull()
    val portError = when {
        port == null || port !in 1024..65535 -> "Use a port between 1024 and 65535"
        port in takenPorts -> "Another server already uses this port"
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("New server", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                label = { Text("Port") },
                isError = portError != null,
                supportingText = { Text(portError ?: "Players connect to your-ip:$portText") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Game mode", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameMode.entries.forEach { m ->
                    ToggleButton(
                        checked = mode == m,
                        onCheckedChange = { mode = m },
                        shapes = ToggleButtonDefaults.shapes(),
                    ) { Text(m.label) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onCreate(ServerProfile(name = name.trim(), port = port!!, gameMode = mode))
                },
                enabled = name.isNotBlank() && portError == null,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) { Text("Create server") }
        }
    }
}

package app.dumpkin.ui

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
import androidx.compose.material3.Switch
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
import app.dumpkin.dumpkin
import app.dumpkin.data.DEFAULT_BEDROCK_PORT
import app.dumpkin.data.GameMode
import app.dumpkin.data.ProfileRules
import app.dumpkin.data.ServerProfile
import app.dumpkin.data.Status
import app.dumpkin.server.ServerManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit, onOpenEngine: () -> Unit, snack: (String) -> Unit) {
    val app = LocalContext.current.dumpkin
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
                title = { Text("Dumpkin") },
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
            existing = profiles,
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
    rt: app.dumpkin.server.ServerRuntime,
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
    existing: List<ServerProfile>,
    onDismiss: () -> Unit,
    onCreate: (ServerProfile) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val claimed = remember(existing) { ProfileRules.claimedPorts(existing) }
    var name by remember { mutableStateOf("My Pumpkin Server") }
    var portText by remember { mutableStateOf(ProfileRules.nextFreePort(claimed).toString()) }
    var mode by remember { mutableStateOf(GameMode.Survival) }
    var bedrock by remember { mutableStateOf(false) }
    // Each server needs its own Bedrock port, so suggest the next one nobody uses.
    var bedrockText by remember {
        mutableStateOf(ProfileRules.nextFreePort(claimed + (portText.toIntOrNull() ?: 0), DEFAULT_BEDROCK_PORT).toString())
    }
    val port = portText.toIntOrNull()
    val bedrockPort = bedrockText.toIntOrNull()
    // Validate through the same rules the settings screen and web API use.
    val candidate = ServerProfile(
        id = "new", name = name.trim(), port = port ?: -1, bedrock = bedrock, bedrockPort = bedrockPort ?: -1,
    )
    val portError = ProfileRules.javaPortError(candidate, existing)
    val bedrockError = ProfileRules.bedrockPortError(candidate, existing)

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bedrock support", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Experimental. Lets Bedrock players join on their own port.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = bedrock, onCheckedChange = { bedrock = it })
            }
            if (bedrock) {
                OutlinedTextField(
                    value = bedrockText,
                    onValueChange = { bedrockText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Bedrock port") },
                    isError = bedrockError != null,
                    supportingText = { Text(bedrockError ?: "Bedrock players connect to your-ip:$bedrockText") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onCreate(
                        ServerProfile(
                            name = name.trim(), port = port!!, gameMode = mode,
                            bedrock = bedrock, bedrockPort = bedrockPort ?: DEFAULT_BEDROCK_PORT,
                        ),
                    )
                },
                enabled = name.isNotBlank() && portError == null && bedrockError == null,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) { Text("Create server") }
        }
    }
}

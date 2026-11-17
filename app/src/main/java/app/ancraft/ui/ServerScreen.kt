package app.ancraft.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ancraft.ancraft
import app.ancraft.data.Compression
import app.ancraft.data.Difficulty
import app.ancraft.data.DistancePreset
import app.ancraft.data.GameMode
import app.ancraft.data.MemoryAction
import app.ancraft.data.ServerProfile
import app.ancraft.data.Status
import app.ancraft.server.ServerManager
import app.ancraft.server.ServerRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun ServerScreen(id: String, onBack: () -> Unit) {
    val app = LocalContext.current.ancraft
    val profiles by app.manager.profiles.collectAsState()
    val profile = profiles.firstOrNull { it.id == id }
    val rt = remember(id, profile == null) { app.manager.runtime(id) }
    if (profile == null || rt == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val st by rt.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val show: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    // Typing a command: the console takes over the whole screen. Opening the keyboard hides everything else and
    // closing it brings it all back. Typing in a settings field only hides the status card.
    val imeVisible = WindowInsets.isImeVisible
    val fullConsole = imeVisible && tab == 0

    // Settings tab: the status card rolls up as you scroll down and returns as soon as you scroll up.
    val collapse = remember { HeaderCollapse() }
    collapse.enabled = tab == 1 && !imeVisible

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AnimatedVisibility(
                visible = !fullConsole,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                TopAppBar(
                    title = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                )
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                // With the top bar hidden nothing else pads for the status bar.
                .then(if (fullConsole) Modifier.statusBarsPadding() else Modifier)
                .imePadding()
                .padding(horizontal = 16.dp)
                .nestedScroll(collapse.connection),
        ) {
            // Spacing lives inside each item (not spacedBy) so hidden items leave no stray gaps.
            AnimatedVisibility(visible = !imeVisible) {
                Box(Modifier.collapsing(collapse).padding(bottom = 12.dp)) { HeroCard(profile, rt, show) }
            }
            AnimatedVisibility(visible = !fullConsole) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupGap),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    listOf("Console", "Settings").forEachIndexed { i, label ->
                        ToggleButton(
                            checked = tab == i,
                            onCheckedChange = { tab = i },
                            modifier = Modifier.weight(1f),
                            shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                            else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        ) { Text(label) }
                    }
                }
            }

            when (tab) {
                0 -> ConsolePane(rt, st.isActive, Modifier.weight(1f))
                else -> SettingsPane(profile, rt, show, onDeleted = onBack, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val ButtonGroupGap = 2.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(profile: ServerProfile, rt: ServerRuntime, snack: (String) -> Unit) {
    val app = LocalContext.current.ancraft
    val st by rt.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val ip = remember(st.status) { ServerManager.localAddresses().firstOrNull() ?: "this-phone" }
    val address = "$ip:${profile.port}"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.status) {
        while (st.status == Status.Running) {
            now = System.currentTimeMillis(); delay(1000)
        }
    }
    val container = when (st.status) {
        Status.Running -> MaterialTheme.colorScheme.primaryContainer
        Status.Crashed -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusBlob(st.status, 72.dp)
                Column(Modifier.weight(1f)) {
                    Text(statusLabel(st.status), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        when {
                            st.status == Status.Running ->
                                "${st.online}/${st.max} players · up ${formatDuration(now - st.startedAt)}"
                            st.status == Status.Crashed -> st.error ?: "The server stopped unexpectedly"
                            else -> "Players join at"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    address,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(address))
                    snack("Copied $address")
                }) { Icon(Icons.Rounded.ContentCopy, "Copy address") }
            }
            if (st.version?.isNotBlank() == true && st.status == Status.Running) {
                Text("Reports as ${st.version}", style = MaterialTheme.typography.labelMedium)
            }
            if (st.isActive && st.rssMb > 0) {
                val limit = profile.memoryLimitMb
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text("Memory", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (limit > 0) "${formatMb(st.rssMb)} / ${formatMb(limit)}" else formatMb(st.rssMb),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (limit > 0) {
                        LinearWavyProgressIndicator(
                            progress = { (st.rssMb.toFloat() / limit).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            when (st.status) {
                Status.Starting, Status.Stopping -> Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) { ContainedLoadingIndicator(Modifier.size(56.dp)) }
                else -> Button(
                    onClick = {
                        if (st.isActive) app.manager.stop(profile.id)
                        else app.manager.start(profile.id)?.let(snack)
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    colors = if (st.isActive) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ) else ButtonDefaults.buttonColors(),
                ) {
                    Icon(
                        if (st.isActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        null,
                        Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (st.isActive) "Stop server" else "Start server")
                }
            }
        }
    }
}

@Composable
private fun ConsolePane(rt: ServerRuntime, active: Boolean, modifier: Modifier = Modifier) {
    val app = LocalContext.current.ancraft
    val logs by rt.logs.collectAsState()
    var input by remember { mutableStateOf("") }
    val send = {
        if (input.isNotBlank()) {
            app.manager.command(rt.profile.id, input)
            input = ""
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.weight(1f).heightIn(min = 120.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            if (logs.isEmpty()) {
                Text(
                    "Server output will show up here.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Reversed list anchored to the bottom: newest line stays in view without manual scrolling.
                LazyColumn(
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    items(logs.asReversed()) { line -> LogLine(line) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("help", "list").forEach { cmd ->
                AssistChip(
                    onClick = { app.manager.command(rt.profile.id, cmd) },
                    label = { Text(cmd) },
                    enabled = active,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { rt.clearLogs() }) { Icon(Icons.Rounded.DeleteSweep, "Clear console") }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            enabled = active,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            placeholder = { Text(if (active) "Type a command (no slash)" else "Start the server to send commands") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            trailingIcon = {
                IconButton(onClick = { send() }, enabled = active && input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send")
                }
            },
        )
    }
}

@Composable
private fun LogLine(line: String) {
    val cs = MaterialTheme.colorScheme
    val color = when {
        line.startsWith(">") -> cs.primary
        line.startsWith("✖") || line.contains("ERROR", true) || line.contains("panicked", true) -> cs.error
        line.contains("WARN", true) -> cs.tertiary
        line.startsWith("▶") || line.startsWith("■") -> cs.secondary
        else -> cs.onSurface
    }
    Text(line, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsPane(
    profile: ServerProfile,
    rt: ServerRuntime,
    snack: (String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.ancraft
    val engines by app.engines.state.collectAsState()
    val all by app.manager.profiles.collectAsState()
    var baseline by remember(profile.id) { mutableStateOf(app.manager.pullConfig(profile)) }
    var draft by remember(profile.id) { mutableStateOf(baseline) }
    var portText by remember(profile.id) { mutableStateOf(baseline.port.toString()) }
    var maxText by remember(profile.id) { mutableStateOf(baseline.maxPlayers.toString()) }
    var showRaw by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val port = portText.toIntOrNull()
    val maxPlayers = maxText.toIntOrNull()
    val portError = when {
        port == null || port !in 1024..65535 -> "Use a port between 1024 and 65535"
        all.any { it.id != profile.id && it.port == port } -> "Another server already uses this port"
        else -> null
    }
    val maxError = if (maxPlayers == null || maxPlayers !in 1..1000) "1 to 1000" else null
    val candidate = draft.copy(port = port ?: -1, maxPlayers = maxPlayers ?: -1)
    val valid = portError == null && maxError == null && draft.name.isNotBlank()
    val dirty = candidate != baseline

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle("General")
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it.take(40)) },
            label = { Text("Server name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.motd,
            onValueChange = { draft = draft.copy(motd = it.take(120)) },
            label = { Text("Message of the day") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                label = { Text("Port") },
                isError = portError != null,
                supportingText = { portError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it.filter(Char::isDigit).take(4) },
                label = { Text("Max players") },
                isError = maxError != null,
                supportingText = { maxError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        SectionTitle("Gameplay")
        Text("Game mode", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameMode.entries.forEach { m ->
                ToggleButton(
                    checked = draft.gameMode == m,
                    onCheckedChange = { draft = draft.copy(gameMode = m) },
                    shapes = ToggleButtonDefaults.shapes(),
                ) { Text(m.label) }
            }
        }
        Text("Difficulty", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { d ->
                ToggleButton(
                    checked = draft.difficulty == d,
                    onCheckedChange = { draft = draft.copy(difficulty = d) },
                    shapes = ToggleButtonDefaults.shapes(),
                ) { Text(d.label) }
            }
        }

        SectionTitle("Performance")
        Text(
            "Phones are small servers. Lower distances keep things smooth.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Quick pick", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DistancePreset.entries.forEach { preset ->
                ToggleButton(
                    checked = draft.viewDistance == preset.view && draft.simulationDistance == preset.simulation,
                    onCheckedChange = { draft = draft.copy(viewDistance = preset.view, simulationDistance = preset.simulation) },
                    shapes = ToggleButtonDefaults.shapes(),
                ) { Text(preset.label) }
            }
        }
        Text(
            "Joining sends every chunk in view distance, so fewer chunks means a much faster join.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SliderRow("View distance", draft.viewDistance, 2..24) { draft = draft.copy(viewDistance = it) }
        SliderRow("Simulation distance", draft.simulationDistance, 2..16) { draft = draft.copy(simulationDistance = it) }

        SectionTitle("Resources")
        val ctx = LocalContext.current
        val mem = remember { deviceMemory(ctx) }
        val cores = remember { Runtime.getRuntime().availableProcessors() }
        val maxLimit = (((mem.totalMb * 0.75).toInt()) / 128 * 128).coerceAtLeast(512)
        Text(
            "This phone has ${formatMb(mem.totalMb)} of RAM, ${formatMb(mem.availMb)} free right now.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            "Limit memory",
            "Pumpkin is native code with no heap setting, so AnCraft watches the server's real RAM use and reacts when it goes over.",
            draft.memoryLimitMb > 0,
        ) { on -> draft = draft.copy(memoryLimitMb = if (on) 1024.coerceAtMost(maxLimit) else 0) }
        if (draft.memoryLimitMb > 0) {
            val shown = draft.memoryLimitMb.coerceIn(256, maxLimit)
            Column {
                Row {
                    Text("Memory limit", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(formatMb(shown), style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    value = shown.toFloat(),
                    onValueChange = { draft = draft.copy(memoryLimitMb = (it / 128).toInt() * 128) },
                    valueRange = 256f..maxLimit.toFloat(),
                    steps = ((maxLimit - 256) / 128 - 1).coerceAtLeast(0),
                )
            }
            Text("When the limit is exceeded", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryAction.entries.forEach { a ->
                    ToggleButton(
                        checked = draft.memoryAction == a,
                        onCheckedChange = { draft = draft.copy(memoryAction = a) },
                        shapes = ToggleButtonDefaults.shapes(),
                    ) { Text(a.label) }
                }
            }
        }
        Text("CPU threads", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleButton(
                checked = draft.cpuThreads == 0,
                onCheckedChange = { draft = draft.copy(cpuThreads = 0) },
                shapes = ToggleButtonDefaults.shapes(),
            ) { Text("Auto") }
            (1..cores).forEach { n ->
                ToggleButton(
                    checked = draft.cpuThreads == n,
                    onCheckedChange = { draft = draft.copy(cpuThreads = n) },
                    shapes = ToggleButtonDefaults.shapes(),
                ) { Text("$n") }
            }
        }
        SwitchRow(
            "Restart after a crash",
            "Tries again up to ${ServerRuntime.MAX_AUTO_RESTARTS} times within 5 minutes.",
            draft.autoRestart,
        ) { draft = draft.copy(autoRestart = it) }

        SectionTitle("Network")
        Text("Packet compression", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Compression.entries.forEach { c ->
                ToggleButton(
                    checked = draft.compression == c,
                    onCheckedChange = { draft = draft.copy(compression = c) },
                    shapes = ToggleButtonDefaults.shapes(),
                ) { Text(c.label) }
            }
        }
        Text(
            draft.compression.hint + ". Compressing chunks is the main CPU cost when someone joins.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            "Online mode",
            "Verify players with Microsoft accounts. Each join waits on a Mojang lookup; offline mode skips it but lets anyone use any name.",
            draft.onlineMode,
        ) { draft = draft.copy(onlineMode = it) }
        SwitchRow(
            "Fetch skins and capes",
            "Turn off to trim work during joins. Players may show default skins.",
            draft.fetchSkins,
        ) { draft = draft.copy(fetchSkins = it) }
        SwitchRow(
            "Lightweight server-list ping",
            "Leaves the server icon out of the multiplayer list response so it loads and pings faster.",
            draft.lightPing,
        ) { draft = draft.copy(lightPing = it) }
        SwitchRow(
            "Bedrock support (experimental)",
            "Also listen on UDP 19132 so Bedrock players can join. Only one server can use it at a time.",
            draft.bedrock,
        ) { draft = draft.copy(bedrock = it) }

        SectionTitle("Engine")
        if (engines.installed.isEmpty()) {
            Text("No engine installed yet — see the Engine tab.", style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleButton(
                    checked = draft.engineId == null,
                    onCheckedChange = { draft = draft.copy(engineId = null) },
                    shapes = ToggleButtonDefaults.shapes(),
                ) { Text("Newest") }
                engines.installed.forEach { e ->
                    ToggleButton(
                        checked = draft.engineId == e.id,
                        onCheckedChange = { draft = draft.copy(engineId = e.id) },
                        shapes = ToggleButtonDefaults.shapes(),
                    ) { Text(e.label) }
                }
            }
        }

        Button(
            onClick = {
                app.manager.update(candidate)
                baseline = candidate
                snack(if (rt.state.value.isActive) "Saved. Restart the server to apply." else "Saved")
            },
            enabled = dirty && valid,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
        ) { Text("Save changes") }

        HorizontalDivider()
        SectionTitle("Advanced")
        OutlinedButton(
            onClick = { showRaw = true },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Edit, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Edit pumpkin.toml")
        }
        OutlinedButton(
            onClick = { showReset = true },
            enabled = !rt.state.value.isActive,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset configuration") }
        Button(
            onClick = { showDelete = true },
            enabled = !rt.state.value.isActive,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Delete, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Delete server and world")
        }
        Spacer(Modifier.size(24.dp))
    }

    if (showRaw) {
        var text by remember { mutableStateOf(app.manager.readRawConfig(profile.id)) }
        AlertDialog(
            onDismissRequest = { showRaw = false },
            title = { Text("pumpkin.toml") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 420.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    placeholder = { Text("The file is created the first time the server starts.") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    app.manager.writeRawConfig(profile.id, text)
                    val pulled = app.manager.pullConfig(profile)
                    baseline = pulled; draft = pulled
                    portText = pulled.port.toString(); maxText = pulled.maxPlayers.toString()
                    app.manager.update(pulled)
                    showRaw = false
                    snack("Configuration saved")
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRaw = false }) { Text("Cancel") } },
        )
    }
    if (showReset) {
        ConfirmDialog(
            title = "Reset configuration?",
            body = "pumpkin.toml is rewritten from the settings above. Your world is not touched.",
            confirm = "Reset",
            onDismiss = { showReset = false },
            onConfirm = {
                app.manager.resetConfig(candidate.takeIf { valid } ?: baseline)
                showReset = false
                snack("Configuration reset")
            },
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Delete \"${profile.name}\"?",
            body = "The server, its world and all of its files will be permanently removed from this phone. This can't be undone.",
            confirm = "Delete",
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                app.manager.delete(profile.id)
                onDeleted()
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("$value chunks", style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

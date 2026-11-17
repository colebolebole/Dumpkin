package app.dumpkin.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.dumpkin.data.Status
import app.dumpkin.dumpkin
import app.dumpkin.server.ServerManager
import androidx.compose.foundation.lazy.items as lazyItems

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WebScreen(snack: (String) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.dumpkin
    val web = app.web
    val st by web.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var password by remember { mutableStateOf(web.password()) }
    var reveal by remember { mutableStateOf(false) }
    var portText by remember(st.port) { mutableStateOf(st.port.toString()) }
    var confirmRegenerate by remember { mutableStateOf(false) }
    var customPassword by remember { mutableStateOf<String?>(null) }

    val urls = remember(st.running, st.port) { ServerManager.localAddresses().map { "http://$it:${st.port}" } }
    val portValue = portText.toIntOrNull()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Dashboard") },
                subtitle = { Text("Manage servers from any browser") },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            st.error != null -> MaterialTheme.colorScheme.errorContainer
                            st.running -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatusBlob(
                            when {
                                st.error != null -> Status.Crashed
                                st.running -> Status.Running
                                else -> Status.Stopped
                            },
                            64.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    st.error != null -> "Couldn't start"
                                    st.running -> "Dashboard is online"
                                    else -> "Dashboard is off"
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                st.error ?: if (st.running) "Open it from a browser on the same Wi-Fi." else "Turn it on to create and manage servers from a computer.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(checked = st.enabled, onCheckedChange = { web.setEnabled(it) })
                    }
                }
            }

            if (st.running) {
                item { SectionLabel("Open it at") }
                lazyItems(urls, key = { it }) { url ->
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(url, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                            IconButton(onClick = { clipboard.setText(AnnotatedString(url)); snack("Copied $url") }) {
                                Icon(Icons.Rounded.ContentCopy, "Copy address")
                            }
                        }
                    }
                }
                if (urls.isEmpty()) {
                    item {
                        Text(
                            "No network address found. Connect to Wi-Fi, then reopen this tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:${st.port}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Open on this phone")
                    }
                }
            }

            item { SectionLabel("Password") }
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (reveal) password else "•".repeat(minOf(password.length, 14)),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace,
                            )
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "Hide password" else "Show password")
                            }
                            IconButton(onClick = { clipboard.setText(AnnotatedString(password)); snack("Password copied") }) {
                                Icon(Icons.Rounded.ContentCopy, "Copy password")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { confirmRegenerate = true }, shapes = ButtonDefaults.shapes()) { Text("New password") }
                            OutlinedButton(onClick = { customPassword = "" }, shapes = ButtonDefaults.shapes()) { Text("Set your own") }
                        }
                    }
                }
            }

            item { SectionLabel("Port") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                        label = { Text("Dashboard port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { web.setPort(portValue ?: -1)?.let(snack) ?: snack("Port changed to $portValue") },
                        enabled = portValue != null && portValue != st.port,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Apply") }
                }
            }

            if (st.running) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (st.sessions) {
                                0 -> "No browsers signed in"
                                1 -> "1 browser signed in"
                                else -> "${st.sessions} browsers signed in"
                            },
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(onClick = { web.signOutAll(); snack("Signed everyone out") }, enabled = st.sessions > 0) {
                            Text("Sign out all")
                        }
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Keep it on your home network", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "The dashboard uses plain HTTP, so anyone on the same Wi-Fi who learns the password can start, stop and delete your servers. " +
                                "Don't forward its port to the internet. While it is on, Dumpkin stays running in the background to answer requests, which uses some battery.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    if (confirmRegenerate) {
        ConfirmDialog(
            title = "Create a new password?",
            body = "Every browser that's signed in will be signed out.",
            confirm = "New password",
            onDismiss = { confirmRegenerate = false },
            onConfirm = {
                password = web.regeneratePassword()
                reveal = true
                confirmRegenerate = false
                snack("New password created")
            },
        )
    }

    customPassword?.let { value ->
        val problem = when {
            value.isEmpty() -> null
            value.length < 8 -> "Use at least 8 characters."
            value.length > 64 -> "Use at most 64 characters."
            value.any { it.isWhitespace() } -> "No spaces, please."
            else -> null
        }
        AlertDialog(
            onDismissRequest = { customPassword = null },
            title = { Text("Set your own password") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { customPassword = it },
                    label = { Text("New password") },
                    isError = problem != null,
                    supportingText = { Text(problem ?: "8 to 64 characters. Everyone is signed out when it changes.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = value.isNotEmpty() && problem == null,
                    onClick = {
                        val error = web.setPassword(value)
                        if (error == null) {
                            password = value
                            reveal = true
                            customPassword = null
                            snack("Password changed")
                        } else snack(error)
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { customPassword = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

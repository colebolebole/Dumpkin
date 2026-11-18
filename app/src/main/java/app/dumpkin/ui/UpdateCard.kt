package app.dumpkin.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.dumpkin.dumpkin
import app.dumpkin.update.ReleaseInfo
import app.dumpkin.update.UpdateManager
import app.dumpkin.update.UpdateState
import java.text.DateFormat
import java.util.Date

/** Checks GitHub for a newer Dumpkin, downloads it, and hands it to Android's installer. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.dumpkin
    val updates = app.updates
    val state by updates.state.collectAsState()
    val auto by updates.autoCheck.collectAsState()
    val hasToken by updates.hasToken.collectAsState()
    val active by app.manager.active.collectAsState()
    var tokenDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("App updates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Dumpkin ${updates.installedVersion}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val s = state) {
                UpdateState.Idle -> {
                    Text("Look for a newer version in the Dumpkin releases on GitHub.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { updates.check() }, shapes = ButtonDefaults.shapes()) { Text("Check for updates") }
                }
                UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    ContainedLoadingIndicator(Modifier.size(40.dp))
                    Spacer(Modifier.size(12.dp))
                    Text("Checking…", style = MaterialTheme.typography.bodyMedium)
                }
                is UpdateState.UpToDate -> {
                    Text(
                        "You're up to date. Last checked " +
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.checkedAt)) + ".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { updates.check() }, shapes = ButtonDefaults.shapes()) { Text("Check again") }
                }
                is UpdateState.Available -> {
                    ReleaseSummary(s.release)
                    Button(onClick = { updates.download(s.release) }, shapes = ButtonDefaults.shapes()) {
                        Text("Download ${formatBytes(s.release.size)}")
                    }
                }
                is UpdateState.Downloading -> {
                    Text("Downloading ${s.release.version}… ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Ready -> {
                    ReleaseSummary(s.release)
                    Text(
                        "Downloaded and checked: it is the same app, signed with the same key.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (active.isNotEmpty()) {
                        Text(
                            "Installing restarts Dumpkin, which stops your running servers (${active.values.joinToString()}). " +
                                "Stop them first if you want a clean shutdown.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (updates.needsInstallPermission()) {
                        Text(
                            "Android needs your OK before Dumpkin can install updates. Allow it on the next screen, then come back and tap Install.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = { start(ctx, updates.installPermissionIntent()) }, shapes = ButtonDefaults.shapes()) {
                            Text("Allow installing updates")
                        }
                    } else {
                        Button(onClick = { start(ctx, updates.installIntent(s.file)) }, shapes = ButtonDefaults.shapes()) {
                            Text("Install ${s.release.version}")
                        }
                    }
                }
                is UpdateState.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { s.release?.let { updates.download(it) } ?: updates.check() },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Try again") }
                        TextButton(onClick = { updates.dismissError() }) { Text("Dismiss") }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check automatically", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Looks once every 12 hours when Dumpkin opens. Updates are never installed without your OK.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(16.dp))
                Switch(checked = auto, onCheckedChange = { updates.setAutoCheck(it) })
            }
            Row(
                Modifier.fillMaxWidth().clickable { tokenDialog = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("GitHub access token", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (hasToken) "Set. Needed only while the repository is private." else "Not set. Needed only while the repository is private.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { tokenDialog = true }) { Text(if (hasToken) "Change" else "Add") }
            }
        }
    }

    if (tokenDialog) TokenDialog(hasToken, onDismiss = { tokenDialog = false }) { updates.setToken(it); tokenDialog = false; updates.dismissError() }
}

private fun start(ctx: Context, intent: android.content.Intent) {
    runCatching { ctx.startActivity(intent) }
}

@Composable
private fun ReleaseSummary(r: ReleaseInfo) {
    var expanded by remember(r.tag) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Dumpkin ${r.version} is available", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        val notes = releaseNotesPreview(r.notes)
        if (notes.isNotBlank()) {
            Text(
                notes, style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            Text(
                if (expanded) "Show less" else "Show more",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

/** Release notes are Markdown; keep it readable as plain text. */
internal fun releaseNotesPreview(notes: String): String =
    notes.lines()
        .map { it.trim().removePrefix("###").removePrefix("##").removePrefix("#").trim() }
        .map { it.replace("**", "").replace("`", "") }
        .filterNot { it.startsWith("[!") || it.startsWith("> [!") }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

@Composable
private fun TokenDialog(hasToken: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub access token") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "While the Dumpkin repository is private, GitHub only shows its releases to someone signed in. " +
                        "Create a fine-grained personal access token with read-only access to \"Contents\" for the repository, and paste it here. " +
                        "It is stored only on this phone and sent only to api.github.com. Leave this empty once the repository is public.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it.trim() },
                    label = { Text(if (hasToken) "New token (leave empty to remove)" else "Token") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(if (text.isEmpty() && hasToken) "Remove" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A slim banner for the Servers tab when an update is waiting. */
@Composable
fun UpdateBanner(onClick: () -> Unit) {
    val updates = LocalContext.current.dumpkin.updates
    val state by updates.state.collectAsState()
    val version = when (val s = state) {
        is UpdateState.Available -> s.release.version
        is UpdateState.Ready -> s.release.version
        else -> return
    }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Dumpkin $version is available", style = MaterialTheme.typography.titleSmall)
                Text("Tap to see what's new and update.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

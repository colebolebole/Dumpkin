package app.dumpkin.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dumpkin.backup.BackupCenter
import app.dumpkin.backup.BackupInfo
import app.dumpkin.backup.BackupJob
import app.dumpkin.data.ServerProfile
import app.dumpkin.dumpkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Back up, restore, export and delete a server's backups. Restoring needs the server stopped. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupsPane(profile: ServerProfile, running: Boolean, snack: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val center: BackupCenter = ctx.dumpkin.manager.backups
    val scope = rememberCoroutineScope()
    val jobs by center.jobs.collectAsState()
    val job = jobs[profile.id]
    val busy = job?.isRunning == true

    var reload by remember { mutableIntStateOf(0) }
    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var labelDialog by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf<BackupInfo?>(null) }
    var deleting by remember { mutableStateOf<BackupInfo?>(null) }
    var exporting by remember { mutableStateOf<File?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val source = exporting
        exporting = null
        if (uri != null && source != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: error("no destination")
                    }.isSuccess
                }
                snack(if (ok) "Backup saved" else "Couldn't save the backup")
            }
        }
    }

    // Refresh the list whenever a job changes state or the user changes something.
    LaunchedEffect(profile.id, job?.state, job?.updatedAt, reload) {
        backups = withContext(Dispatchers.IO) { center.list(profile.id) }
    }
    // Say how a job ended, once, if it just finished while this tab was open.
    LaunchedEffect(job?.updatedAt, job?.state) {
        val j = job ?: return@LaunchedEffect
        if (!j.isRunning && System.currentTimeMillis() - j.updatedAt < 4_000) {
            snack(j.message ?: if (j.state == BackupJob.State.Done) "Done" else "That didn't work")
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "A backup is a zip of the whole server folder: world, settings and files. Save one to keep it off the phone. " +
                        "Restoring replaces the server's files and keeps a \"before-restore\" copy of what was there.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (running) {
                    Text(
                        "This server is running. For a copy that is guaranteed consistent, stop it first; backing up while it runs can catch the world mid-save.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { labelDialog = true }, enabled = !busy, shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Backup, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Back up now")
                }
            }
        }

        if (job != null && (job.isRunning || job.state == BackupJob.State.Failed)) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (job.isRunning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (job.isRunning) {
                        Text(
                            (if (job.kind == BackupJob.Kind.Restore) "Restoring… " else "Backing up… ") + "${(job.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        LinearWavyProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(job.message ?: "That didn't work.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { center.dismiss(profile.id) }) { Text("Dismiss") }
                    }
                }
            }
        }

        if (backups.isEmpty()) {
            Text("No backups yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f, fill = true)) {
            items(backups, key = { it.name }) { b ->
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Backup, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            b.label.replace('-', ' ').ifBlank { "Backup" },
                            style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                        )
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.createdAt)) + " · " + formatBytes(b.size),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { restoring = b }, enabled = !running && !busy) { Icon(Icons.Rounded.Restore, "Restore ${b.name}") }
                    IconButton(onClick = {
                        center.file(profile.id, b.name)?.let { f ->
                            exporting = f
                            exportLauncher.launch("${profile.name.ifBlank { "server" }}-${b.name}")
                        }
                    }) { Icon(Icons.Rounded.Download, "Save ${b.name} to your files") }
                    IconButton(onClick = { deleting = b }, enabled = !busy) { Icon(Icons.Rounded.Delete, "Delete ${b.name}") }
                }
            }
        }
    }

    if (labelDialog) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { labelDialog = false },
            title = { Text("Back up ${profile.name}") },
            text = {
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(60) },
                    label = { Text("Label (optional)") }, placeholder = { Text("before the update") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    labelDialog = false
                    center.startBackup(profile.id, label)?.let(snack)
                }) { Text("Back up") }
            },
            dismissButton = { TextButton(onClick = { labelDialog = false }) { Text("Cancel") } },
        )
    }

    restoring?.let { b ->
        ConfirmDialog(
            title = "Restore this backup?",
            body = "${profile.name}'s files will be replaced with the backup from " +
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.createdAt)) +
                ". What is there now is saved as a \"before-restore\" backup first.",
            confirm = "Restore",
            onDismiss = { restoring = null },
            onConfirm = { restoring = null; center.startRestore(profile.id, b.name)?.let(snack) },
        )
    }

    deleting?.let { b ->
        ConfirmDialog(
            title = "Delete this backup?",
            body = "The backup from " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.createdAt)) +
                " (${formatBytes(b.size)}) will be permanently removed.",
            confirm = "Delete",
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                if (!center.delete(profile.id, b.name)) snack("That backup was already gone")
                reload++
            },
        )
    }
}

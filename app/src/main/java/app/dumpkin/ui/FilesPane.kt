package app.dumpkin.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dumpkin.data.ServerProfile
import app.dumpkin.dumpkin
import app.dumpkin.files.FileEntry
import app.dumpkin.files.FileError
import app.dumpkin.files.Listing
import app.dumpkin.files.ServerFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Browse a server's folder, open and edit text files, and make folders. Changes need the server stopped. */
@Composable
fun FilesPane(profile: ServerProfile, running: Boolean, snack: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.dumpkin
    val files = remember(profile.id) { app.manager.files(profile.id) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var path by rememberSaveable(profile.id) { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var listing by remember { mutableStateOf<Listing?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    var editing by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<NameRequest?>(null) }
    var deleting by remember { mutableStateOf<FileEntry?>(null) }
    var exporting by remember { mutableStateOf<File?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val source = exporting
        exporting = null
        if (uri != null && source != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { copyTo(ctx, source, uri) }.isSuccess }
                snack(if (ok) "Saved a copy of ${source.name}" else "Couldn't save that file")
            }
        }
    }

    LaunchedEffect(path, reload, files) {
        val f = files ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { runCatching { f.list(path) } }
        result.onSuccess { listing = it; problem = null }
            .onFailure {
                problem = (it as? FileError)?.message ?: "Couldn't open that folder."
                if (path.isNotEmpty() && (it as? FileError)?.status == 404) path = ""
            }
    }

    /** Runs a file operation off the main thread and refreshes the list, reporting errors as a message. */
    fun change(done: String? = null, op: (ServerFiles) -> Unit) {
        val f = files ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { op(f) } }
            r.onFailure { snack((it as? FileError)?.message ?: "That didn't work: ${it.message}") }
            if (r.isSuccess && done != null) snack(done)
            reload++
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { path = path.substringBeforeLast('/', "") }, enabled = path.isNotEmpty()) {
                Icon(Icons.Rounded.ArrowUpward, "Up one folder")
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Crumb("Server folder") { path = "" }
                val parts = if (path.isEmpty()) emptyList() else path.split('/')
                parts.forEachIndexed { i, p ->
                    Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Crumb(p) { path = parts.take(i + 1).joinToString("/") }
                }
            }
            IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, "Refresh") }
            FilledTonalButton(onClick = { naming = NameRequest.NewFolder }, enabled = !running) {
                Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("New")
            }
        }
        if (running) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = MaterialTheme.shapes.large) {
                Text(
                    "Stop the server to rename, edit or delete files. You can still look at them and save copies.",
                    Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        val entries = listing?.entries.orEmpty()
        if (listing != null && entries.isEmpty() && problem == null) {
            Text("This folder is empty.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f, fill = true)) {
            items(entries, key = { it.path }) { e ->
                FileRow(
                    e, running,
                    onOpen = { if (e.isDir) path = e.path else editing = e.path },
                    onRename = { naming = NameRequest.Rename(e) },
                    onDelete = { deleting = e },
                    onSaveCopy = if (e.isDir || files == null) null else ({
                        exporting = files.file(e.path)
                        exportLauncher.launch(e.name)
                    }),
                )
            }
            if (listing?.truncated == true) {
                item { Text("Only the first ${entries.size} items are shown.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    when (val n = naming) {
        NameRequest.NewFolder -> NameDialog("New folder", "Folder name", "", "Create", { naming = null }) { name ->
            naming = null
            change("Created $name") { it.mkdir(if (path.isEmpty()) name else "$path/$name") }
        }
        is NameRequest.Rename -> NameDialog("Rename", "New name", n.entry.name, "Rename", { naming = null }) { name ->
            naming = null
            if (name != n.entry.name) change("Renamed to $name") { it.rename(n.entry.path, name) }
        }
        null -> Unit
    }

    deleting?.let { e ->
        ConfirmDialog(
            title = "Delete ${e.name}?",
            body = if (e.isDir) "The folder and everything in it will be permanently removed. This can't be undone."
            else "The file will be permanently removed. This can't be undone.",
            confirm = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { deleting = null; change("Deleted ${e.name}") { it.delete(e.path) } },
        )
    }

    editing?.let { filePath ->
        if (files != null) {
            FileEditor(files, filePath, readOnly = running, snack = snack, onClose = { editing = null; reload++ })
        }
    }
}

private sealed interface NameRequest {
    data object NewFolder : NameRequest
    data class Rename(val entry: FileEntry) : NameRequest
}

private fun copyTo(ctx: Context, source: File, uri: Uri) {
    ctx.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
        ?: error("couldn't open the destination")
}

@Composable
private fun Crumb(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
    )
}

@Composable
private fun FileRow(
    e: FileEntry,
    running: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSaveCopy: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (e.isDir) Icons.Rounded.Folder else Icons.Rounded.Description, null,
            tint = if (e.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(
                if (e.isDir) null else formatBytes(e.size),
                if (e.modified > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(e.modified)) else null,
            ).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Actions for ${e.name}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                onSaveCopy?.let {
                    DropdownMenuItem(
                        text = { Text("Save a copy…") }, leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = { menu = false; it() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    enabled = !running, onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    enabled = !running, onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(ServerFiles.MAX_NAME) },
                label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onDone(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Full-screen editor for a small text file. Read-only while the server runs. */
@Composable
private fun FileEditor(files: ServerFiles, path: String, readOnly: Boolean, snack: (String) -> Unit, onClose: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var original by remember(path) { mutableStateOf<String?>(null) }
    var text by remember(path) { mutableStateOf("") }
    var error by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        val r = withContext(Dispatchers.IO) { runCatching { files.readText(path) } }
        r.onSuccess { original = it.text; text = it.text }
            .onFailure { error = (it as? FileError)?.message ?: "Couldn't open that file." }
    }
    val dirty = original != null && text != original

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
                    Text(path, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!readOnly && original != null) {
                        TextButton(
                            enabled = dirty,
                            onClick = {
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { runCatching { files.writeText(path, text) } }
                                    r.onSuccess { original = text; snack("Saved ${path.substringAfterLast('/')}") }
                                        .onFailure { snack((it as? FileError)?.message ?: "Couldn't save the file.") }
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Save")
                        }
                    }
                }
                when {
                    error != null -> Text(error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                    original == null -> Text("Opening…", Modifier.padding(16.dp))
                    else -> {
                        if (readOnly) {
                            Text(
                                "The server is running, so this file is read-only.",
                                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = text, onValueChange = { text = it }, readOnly = readOnly,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
                        )
                    }
                }
            }
        }
    }
}

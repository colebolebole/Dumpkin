package app.dumpkin.backup

import app.dumpkin.files.ServerFiles
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupError(message: String) : Exception(message)

data class BackupInfo(
    /** File name, for example `20261118-103015-before-update.zip`. Doubles as the backup's id. */
    val name: String,
    val size: Long,
    val createdAt: Long,
    /** Text after the timestamp in the name, or empty. */
    val label: String,
)

/**
 * Zip backups of server folders, kept under `<root>/<serverId>/`.
 *
 * A backup is a plain zip of the server folder plus a small `dumpkin-backup.json` marker, so it can be opened with any
 * zip tool. Restoring first takes a safety backup of what is there now, unpacks into a temporary folder (rejecting any
 * entry that would land outside it), and only then swaps the folders, so a failed restore never destroys a server.
 */
class BackupStore(private val root: File) {
    private fun dirFor(serverId: String): File {
        if (!SAFE_ID.matches(serverId)) throw BackupError("Unknown server.")
        return File(root, serverId)
    }

    fun list(serverId: String): List<BackupInfo> =
        dirFor(serverId).listFiles { f -> f.isFile && NAME.matches(f.name) }.orEmpty()
            .map { info(it) }
            .sortedWith(compareByDescending<BackupInfo> { it.createdAt }.thenByDescending { it.name })

    /** The backup file, or null when the name isn't a valid backup name or there is no such backup. */
    fun file(serverId: String, name: String): File? {
        if (!NAME.matches(name)) return null
        return File(dirFor(serverId), name).takeIf { it.isFile }
    }

    fun delete(serverId: String, name: String): Boolean = file(serverId, name)?.delete() == true

    fun deleteAll(serverId: String) {
        if (SAFE_ID.matches(serverId)) ServerFiles.deleteTree(File(root, serverId).toPath())
    }

    private fun info(f: File): BackupInfo {
        val stem = f.name.removeSuffix(".zip")
        val label = stem.drop(STAMP_LENGTH).trimStart('-').substringBefore('_')
        return BackupInfo(f.name, f.length(), f.lastModified(), label)
    }

    /**
     * Zips [serverDir]. Runs on the calling thread; [progress] gets 0..1 as bytes are written.
     * @throws BackupError with a message that is fine to show the user.
     */
    fun create(
        serverId: String,
        serverDir: File,
        label: String,
        meta: JSONObject = JSONObject(),
        progress: (Float) -> Unit = {},
    ): BackupInfo {
        val dir = dirFor(serverId).apply { mkdirs() }
        if (!serverDir.isDirectory) throw BackupError("The server folder doesn't exist.")
        val total = folderSize(serverDir)
        // Compressed output is usually much smaller, but a world full of already-compressed data is not, so be safe.
        val free = dir.usableSpace
        if (free in 1 until total) {
            throw BackupError("Not enough free space: this server is about ${mb(total)} and the phone has ${mb(free)} free.")
        }

        val name = uniqueName(dir, label)
        val part = File(dir, "$name.part")
        try {
            ZipOutputStream(part.outputStream().buffered()).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED) // phones are slow; worlds compress fine even at level 1
                val marker = JSONObject(meta.toString()).put("format", FORMAT).put("created", System.currentTimeMillis())
                zip.putNextEntry(ZipEntry(MARKER))
                zip.write(marker.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                addTree(zip, serverDir.toPath(), total, progress)
            }
            val out = File(dir, name)
            if (!part.renameTo(out)) throw BackupError("Couldn't finish writing the backup.")
            progress(1f)
            return info(out)
        } catch (e: BackupError) {
            part.delete(); throw e
        } catch (e: IOException) {
            part.delete(); throw BackupError("Couldn't write the backup: ${e.message}")
        }
    }

    private fun addTree(zip: ZipOutputStream, base: Path, total: Long, progress: (Float) -> Unit) {
        var done = 0L
        var lastReported = 0f
        val buf = ByteArray(64 * 1024)
        // Not following links: a symlink inside a server folder must not drag outside files into a backup.
        Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != base) {
                    zip.putNextEntry(ZipEntry(entryName(base, dir) + "/"))
                    zip.closeEntry()
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isRegularFile) return FileVisitResult.CONTINUE
                if (file.fileName.toString().endsWith(".dkpart")) return FileVisitResult.CONTINUE
                try {
                    Files.newInputStream(file).use { input ->
                        zip.putNextEntry(ZipEntry(entryName(base, file)).apply { time = attrs.lastModifiedTime().toMillis() })
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            zip.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done.toFloat() / total).coerceIn(0f, 0.99f)
                                if (p - lastReported >= 0.01f) { lastReported = p; progress(p) }
                            }
                        }
                        zip.closeEntry()
                    }
                } catch (_: NoSuchFileException) {
                    // Deleted while we were reading (a running server's temp file): fine to leave out.
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun entryName(base: Path, p: Path) = base.relativize(p).toString().replace(File.separatorChar, '/')

    /**
     * Replaces the contents of [serverDir] with the backup. The caller must make sure the server is stopped.
     * [progress] covers the safety backup (first 40%) and the unpacking.
     */
    fun restore(serverId: String, name: String, serverDir: File, meta: JSONObject = JSONObject(), progress: (Float) -> Unit = {}) {
        val backup = file(serverId, name) ?: throw BackupError("That backup no longer exists.")
        val parent = serverDir.absoluteFile.parentFile ?: throw BackupError("The server folder isn't valid.")

        ZipFile(backup).use { zip ->
            val marker = zip.getEntry(MARKER) ?: throw BackupError("That file isn't a Dumpkin backup.")
            val markerJson = runCatching { JSONObject(zip.getInputStream(marker).bufferedReader().readText()) }.getOrNull()
            if (markerJson?.optInt("format", 0) != FORMAT) throw BackupError("That backup was made by a newer Dumpkin and can't be restored here.")

            // Check every entry before touching anything.
            val tmp = File(parent, serverDir.name + ".restoring")
            ServerFiles.deleteTree(tmp.toPath())
            val tmpCanon = tmp.apply { mkdirs() }.canonicalFile
            var uncompressed = 0L
            val names = zip.entries().toList()
            for (e in names) {
                if (e.name == MARKER) continue
                targetFor(tmpCanon, e.name) // throws on traversal
                uncompressed += e.size.coerceAtLeast(0)
            }
            if (tmp.usableSpace in 1 until uncompressed) {
                ServerFiles.deleteTree(tmp.toPath())
                throw BackupError("Not enough free space to unpack this backup (needs about ${mb(uncompressed)}).")
            }

            // Safety net: keep what is there now.
            try {
                if (serverDir.isDirectory) {
                    create(serverId, serverDir, "before-restore", meta) { progress(it * 0.4f) }
                }
            } catch (e: BackupError) {
                ServerFiles.deleteTree(tmp.toPath())
                throw BackupError("Couldn't make a safety backup first, so nothing was changed. ${e.message}")
            }

            try {
                val buf = ByteArray(64 * 1024)
                var done = 0L
                for (e in names) {
                    if (e.name == MARKER) continue
                    val target = targetFor(tmpCanon, e.name)
                    if (e.isDirectory) { target.mkdirs(); continue }
                    target.parentFile?.mkdirs()
                    zip.getInputStream(e).use { input ->
                        target.outputStream().use { out ->
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (uncompressed > 0) progress(0.4f + 0.55f * (done.toFloat() / uncompressed).coerceIn(0f, 1f))
                            }
                        }
                    }
                    if (e.time > 0) target.setLastModified(e.time)
                }
                ServerFiles.deleteTree(serverDir.toPath())
                Files.move(tmp.toPath(), serverDir.toPath())
                progress(1f)
            } catch (e: IOException) {
                ServerFiles.deleteTree(tmp.toPath())
                throw BackupError("Couldn't unpack the backup: ${e.message}. Your previous files are in the \"before-restore\" backup.")
            }
        }
    }

    /** Where a zip entry would be written, refusing anything that could escape [base] ("zip slip"). */
    private fun targetFor(base: File, entryName: String): File {
        if (entryName.isEmpty() || entryName.startsWith("/") || entryName.contains('\\') || entryName.contains('\u0000') ||
            entryName.split('/').any { it == ".." } || Regex("^[A-Za-z]:").containsMatchIn(entryName)
        ) throw BackupError("That backup contains an unsafe path (\"${entryName.take(60)}\") and was not restored.")
        val target = File(base, entryName).canonicalFile
        if (target.path != base.path && !target.path.startsWith(base.path + File.separator)) {
            throw BackupError("That backup contains an unsafe path and was not restored.")
        }
        return target
    }

    private fun uniqueName(dir: File, label: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date())
        val slug = slug(label)
        val base = if (slug.isEmpty()) stamp else "$stamp-$slug"
        var name = "$base.zip"
        var n = 2
        while (File(dir, name).exists()) { name = "${base}_$n.zip"; n++ }
        return name
    }

    private fun folderSize(dir: File): Long {
        var total = 0L
        Files.walkFileTree(dir.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) total += attrs.size()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return total
    }

    private fun mb(bytes: Long) = if (bytes >= 1_073_741_824L) "%.1f GB".format(Locale.US, bytes / 1_073_741_824.0) else "${bytes / 1_048_576} MB"

    companion object {
        const val MARKER = "dumpkin-backup.json"
        const val FORMAT = 1
        private const val STAMP_LENGTH = 15 // yyyyMMdd-HHmmss
        private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")
        private val NAME = Regex("""\d{8}-\d{6}(-[a-z0-9-]{1,24})?(_\d{1,3})?\.zip""")

        /** Lower-case letters, digits and dashes only, so a label can never change where a file is written. */
        fun slug(label: String): String =
            label.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")
                .replace(Regex("-+"), "-").trim('-').take(24).trim('-')
    }
}

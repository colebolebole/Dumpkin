package app.dumpkin.files

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/** A problem with a file request. [status] is an HTTP-style code the web layer passes straight through. */
class FileError(val status: Int, message: String) : Exception(message)

data class FileEntry(
    val name: String,
    /** Path relative to the server folder, always with `/` separators. */
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)

data class Listing(val path: String, val entries: List<FileEntry>, val truncated: Boolean)

data class TextFile(val path: String, val text: String, val size: Long)

/**
 * File access confined to one server's folder, shared by the app and the web dashboard.
 *
 * Every path is relative to the folder and is checked twice: first its segments (no `..`, no separators, no control
 * characters), then its canonical location, which also catches symlinks that point outside. Deleting never follows
 * symlinks. This class does not decide *when* changes are allowed (the callers refuse them while a server runs).
 */
class ServerFiles(root: File) {
    private val rootDir: File = root.canonicalFile
    private val rootPath: String = rootDir.path

    /** Turns a request path like `world/region` (or `/`) into a file inside the server folder. */
    fun resolve(rel: String): File {
        val segments = segments(rel)
        var f = rootDir
        for (s in segments) f = File(f, s)
        val canon = try {
            f.canonicalFile
        } catch (_: IOException) {
            throw FileError(400, "That path isn't valid.")
        }
        if (canon.path != rootPath && !canon.path.startsWith(rootPath + File.separator)) {
            throw FileError(403, "That path is outside the server folder.")
        }
        return f
    }

    private fun segments(rel: String): List<String> {
        if (rel.contains('\u0000') || rel.contains('\\')) throw FileError(400, "That path isn't valid.")
        val parts = rel.split('/').filter { it.isNotEmpty() }
        for (p in parts) {
            if (p == "." || p == ".." || p.length > MAX_NAME || p.any { it.isISOControl() }) {
                throw FileError(400, "That path isn't valid.")
            }
        }
        return parts
    }

    private fun relative(f: File): String {
        val full = f.absoluteFile.path
        val base = rootDir.absoluteFile.path
        return full.removePrefix(base).replace(File.separatorChar, '/').trim('/')
    }

    private fun isRoot(f: File) = relative(f).isEmpty()

    fun list(rel: String): Listing {
        val dir = resolve(rel)
        if (!dir.isDirectory) throw FileError(404, "That folder doesn't exist.")
        val all = dir.listFiles().orEmpty()
        val entries = all
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .take(MAX_ENTRIES)
            .map { FileEntry(it.name, relative(it), it.isDirectory, if (it.isFile) it.length() else 0L, it.lastModified()) }
        return Listing(relative(dir), entries, truncated = all.size > MAX_ENTRIES)
    }

    /** Reads a small UTF-8 text file; binary or very large files are refused with an explanation. */
    fun readText(rel: String): TextFile {
        val f = resolve(rel)
        if (!f.isFile) throw FileError(404, "That file doesn't exist.")
        val size = f.length()
        if (size > MAX_TEXT_BYTES) throw FileError(413, "This file is ${size / 1024} KB, too large to open here (limit ${MAX_TEXT_BYTES / 1024} KB).")
        val bytes = f.readBytes()
        if (bytes.take(4096).any { it == 0.toByte() }) throw FileError(415, "This looks like a binary file, so it can't be shown as text.")
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw FileError(415, "This file isn't UTF-8 text, so it can't be shown here.")
        }
        return TextFile(relative(f), text, size)
    }

    /** Replaces (or creates) a text file. The write is atomic, so a crash can't leave half a file. */
    fun writeText(rel: String, text: String) {
        val f = resolve(rel)
        if (isRoot(f)) throw FileError(400, "Choose a file to save.")
        if (f.exists() && !f.isFile) throw FileError(409, "That's a folder, not a file.")
        val parent = f.parentFile
        if (parent == null || !parent.isDirectory) throw FileError(404, "The folder for that file doesn't exist.")
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_TEXT_BYTES) throw FileError(413, "That's too large to save here (limit ${MAX_TEXT_BYTES / 1024} KB).")
        val tmp = File(parent, ".${f.name}.dkpart")
        try {
            tmp.writeBytes(bytes)
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            tmp.delete()
            throw FileError(500, "Couldn't save the file: ${e.message}")
        }
    }

    fun mkdir(rel: String) {
        val f = resolve(rel)
        if (isRoot(f)) throw FileError(400, "Give the new folder a name.")
        if (f.exists()) throw FileError(409, "Something with that name already exists.")
        val parent = f.parentFile
        if (parent == null || !parent.isDirectory) throw FileError(404, "The parent folder doesn't exist.")
        if (!f.mkdir()) throw FileError(500, "Couldn't create the folder.")
    }

    fun rename(rel: String, newName: String) {
        val f = resolve(rel)
        if (isRoot(f)) throw FileError(400, "The server folder itself can't be renamed.")
        if (!f.exists()) throw FileError(404, "That doesn't exist.")
        checkName(newName)
        val target = File(f.parentFile, newName)
        if (target.exists()) throw FileError(409, "Something named \"$newName\" is already there.")
        try {
            Files.move(f.toPath(), target.toPath())
        } catch (e: IOException) {
            throw FileError(500, "Couldn't rename it: ${e.message}")
        }
    }

    fun delete(rel: String) {
        val f = resolve(rel)
        if (isRoot(f)) throw FileError(400, "The server folder itself can't be deleted here.")
        if (!f.exists() && !Files.isSymbolicLink(f.toPath())) throw FileError(404, "That doesn't exist.")
        try {
            deleteTree(f.toPath())
        } catch (e: IOException) {
            throw FileError(500, "Couldn't delete it: ${e.message}")
        }
    }

    /** A regular file inside the folder, for downloading. */
    fun file(rel: String): File {
        val f = resolve(rel)
        if (!f.isFile) throw FileError(404, "That file doesn't exist.")
        return f
    }

    private fun checkName(name: String) {
        if (name.isEmpty() || name.length > MAX_NAME || name == "." || name == ".." ||
            name.contains('/') || name.contains('\\') || name.any { it.isISOControl() }
        ) throw FileError(400, "That name isn't allowed.")
    }

    companion object {
        const val MAX_TEXT_BYTES = 256 * 1024
        const val MAX_ENTRIES = 2000
        const val MAX_NAME = 128

        /** Deletes a file or folder without ever following a symlink out of the tree. */
        fun deleteTree(path: Path) {
            if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file); return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir); return FileVisitResult.CONTINUE
                }
            })
        }
    }
}

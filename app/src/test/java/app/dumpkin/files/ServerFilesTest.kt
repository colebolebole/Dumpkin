package app.dumpkin.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ServerFilesTest {
    private lateinit var base: File
    private lateinit var root: File
    private lateinit var files: ServerFiles

    @Before fun setUp() {
        base = Files.createTempDirectory("dk-files").toFile()
        root = File(base, "server").apply { mkdirs() }
        File(root, "pumpkin.toml").writeText("motd = \"hi\"\n")
        File(root, "world/region").mkdirs()
        File(root, "world/level.dat").writeBytes(byteArrayOf(0x1f, 0x8b.toByte(), 0, 0, 5))
        File(root, "Notes.txt").writeText("héllo")
        File(base, "secret.txt").writeText("outside the server folder")
        files = ServerFiles(root)
    }

    private fun status(block: () -> Unit): Int {
        try { block() } catch (e: FileError) { return e.status }
        fail("expected a FileError"); return 0
    }

    @Test fun listsFoldersFirstThenFilesCaseInsensitively() {
        val l = files.list("")
        assertEquals(listOf("world", "Notes.txt", "pumpkin.toml"), l.entries.map { it.name })
        assertEquals("", l.path)
        val sub = files.list("/world/")
        assertEquals("world", sub.path)
        assertEquals(listOf("region", "level.dat"), sub.entries.map { it.name })
        assertEquals("world/level.dat", sub.entries.last().path)
        assertEquals(5L, sub.entries.last().size)
    }

    @Test fun pathsCannotEscapeTheServerFolder() {
        for (bad in listOf("..", "../secret.txt", "world/../../secret.txt", "world/../..", "./../secret.txt", "a\\..\\b", "x\u0000y")) {
            assertEquals(bad, 400, status { files.readText(bad) })
        }
        assertEquals(404, status { files.readText("does-not-exist.txt") })
        // Absolute-looking paths are just relative to the server folder.
        assertEquals(404, status { files.readText("/etc/passwd") })
        assertTrue(files.resolve("/etc/passwd").path.startsWith(root.canonicalPath))
    }

    @Test fun symlinksPointingOutsideAreRefused() {
        val link = File(root, "sneaky")
        try {
            Files.createSymbolicLink(link.toPath(), File(base, "secret.txt").toPath())
        } catch (e: Exception) {
            assumeTrue("symlinks aren't available here: ${e.message}", false)
        }
        assertEquals(403, status { files.readText("sneaky") })
        assertEquals(403, status { files.writeText("sneaky", "overwrite") })
        assertEquals("outside the server folder", File(base, "secret.txt").readText())

        // Deleting the link removes only the link, never what it points at.
        files.delete("sneaky")
        assertFalse(link.exists())
        assertTrue(File(base, "secret.txt").exists())
    }

    @Test fun deletingNeverFollowsALinkedFolder() {
        val outside = File(base, "outside-dir").apply { mkdirs(); File(this, "keep.txt").writeText("keep") }
        val link = File(root, "world/linked")
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
        } catch (e: Exception) {
            assumeTrue("symlinks aren't available here: ${e.message}", false)
        }
        files.delete("world")
        assertFalse(File(root, "world").exists())
        assertTrue("the folder behind the link must survive", File(outside, "keep.txt").exists())
    }

    @Test fun readsTextAndRefusesBinaryAndHugeFiles() {
        assertEquals("héllo", files.readText("Notes.txt").text)
        assertEquals(415, status { files.readText("world/level.dat") })
        File(root, "big.txt").writeText("x".repeat(ServerFiles.MAX_TEXT_BYTES + 1))
        assertEquals(413, status { files.readText("big.txt") })
        File(root, "latin1.txt").writeBytes(byteArrayOf(0x68, 0xe9.toByte(), 0x6c)) // not valid UTF-8
        assertEquals(415, status { files.readText("latin1.txt") })
        assertEquals(404, status { files.readText("world") }) // a folder is not a file
    }

    @Test fun writesAreAtomicAndCanCreateNewFiles() {
        files.writeText("pumpkin.toml", "motd = \"changed\"\n")
        assertEquals("motd = \"changed\"\n", File(root, "pumpkin.toml").readText())
        files.writeText("world/new.json", "{}")
        assertEquals("{}", File(root, "world/new.json").readText())
        assertTrue("no temp files left behind", root.walkTopDown().none { it.name.endsWith(".dkpart") })
        assertEquals(404, status { files.writeText("missing-folder/x.txt", "x") })
        assertEquals(409, status { files.writeText("world", "x") })
        assertEquals(400, status { files.writeText("", "x") })
        assertEquals(413, status { files.writeText("big.txt", "x".repeat(ServerFiles.MAX_TEXT_BYTES + 1)) })
    }

    @Test fun makesRenamesAndDeletes() {
        files.mkdir("world/backups")
        assertTrue(File(root, "world/backups").isDirectory)
        assertEquals(409, status { files.mkdir("world/backups") })
        assertEquals(404, status { files.mkdir("nope/inner") })

        files.rename("Notes.txt", "readme.txt")
        assertTrue(File(root, "readme.txt").exists() && !File(root, "Notes.txt").exists())
        assertEquals(409, status { files.rename("readme.txt", "pumpkin.toml") })
        for (bad in listOf("a/b", "..", "", "x\\y", "c\u0001")) assertEquals(bad, 400, status { files.rename("readme.txt", bad) })

        files.delete("world")
        assertFalse(File(root, "world").exists())
        assertEquals(404, status { files.delete("world") })
    }

    @Test fun theServerFolderItselfIsProtected() {
        for (p in listOf("", "/", ".", "world/..")) {
            val s = status { files.delete(p) }
            assertTrue("$p -> $s", s == 400)
        }
        assertEquals(400, status { files.rename("", "x") })
        assertEquals(400, status { files.mkdir("") })
        assertTrue(root.exists() && File(root, "pumpkin.toml").exists())
    }

    @Test fun downloadsOnlyGiveRegularFiles() {
        assertEquals("pumpkin.toml", files.file("pumpkin.toml").name)
        assertEquals(404, status { files.file("world") })
        assertEquals(400, status { files.file("../secret.txt") })
    }
}

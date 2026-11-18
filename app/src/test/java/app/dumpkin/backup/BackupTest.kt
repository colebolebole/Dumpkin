package app.dumpkin.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupTest {
    private lateinit var base: File
    private lateinit var serverDir: File
    private lateinit var store: BackupStore

    @Before fun setUp() {
        base = Files.createTempDirectory("dk-backup").toFile()
        serverDir = File(base, "servers/abc").apply { mkdirs() }
        File(serverDir, "pumpkin.toml").writeText("motd = \"original\"\n")
        File(serverDir, "world/region").mkdirs()
        File(serverDir, "world/region/r.0.0.mca").writeBytes(ByteArray(5000) { (it % 251).toByte() })
        File(serverDir, "world/empty-dir").mkdirs()
        store = BackupStore(File(base, "backups"))
    }

    private fun message(block: () -> Unit): String {
        try { block() } catch (e: BackupError) { return e.message.orEmpty() }
        fail("expected a BackupError"); return ""
    }

    /** Writes a zip with the Dumpkin marker plus the given entries. */
    private fun fakeBackup(name: String, vararg entries: Pair<String, String>, marker: Boolean = true): File {
        val dir = File(base, "backups/abc").apply { mkdirs() }
        val f = File(dir, name)
        ZipOutputStream(f.outputStream()).use { z ->
            if (marker) { z.putNextEntry(ZipEntry(BackupStore.MARKER)); z.write("""{"format":1}""".toByteArray()); z.closeEntry() }
            for ((n, content) in entries) { z.putNextEntry(ZipEntry(n)); z.write(content.toByteArray()); z.closeEntry() }
        }
        return f
    }

    @Test fun backsUpAWorldAndRestoresIt() {
        val seen = mutableListOf<Float>()
        val info = store.create("abc", serverDir, "My Big Update!", JSONObject().put("serverName", "SMP")) { seen += it }
        assertTrue(info.name, Regex("""\d{8}-\d{6}-my-big-update\.zip""").matches(info.name))
        assertEquals("my-big-update", info.label)
        assertTrue(info.size > 0)
        assertEquals(1f, seen.last(), 0f)
        assertTrue("progress only goes forward", seen.zipWithNext().all { (a, b) -> b >= a })

        ZipFile(File(base, "backups/abc/${info.name}")).use { z ->
            val names = z.entries().toList().map { it.name }
            assertEquals(BackupStore.MARKER, names.first())
            assertTrue(names.containsAll(listOf("pumpkin.toml", "world/region/r.0.0.mca", "world/empty-dir/")))
            assertTrue(JSONObject(z.getInputStream(z.getEntry(BackupStore.MARKER)).reader().readText()).getString("serverName") == "SMP")
        }

        // Wreck the server, then bring it back.
        File(serverDir, "world/region/r.0.0.mca").writeBytes(byteArrayOf(1, 2, 3))
        File(serverDir, "pumpkin.toml").delete()
        File(serverDir, "junk.txt").writeText("added after the backup")
        store.restore("abc", info.name, serverDir)

        assertEquals("motd = \"original\"\n", File(serverDir, "pumpkin.toml").readText())
        assertEquals(5000, File(serverDir, "world/region/r.0.0.mca").length())
        assertTrue(File(serverDir, "world/empty-dir").isDirectory)
        assertFalse("files added after the backup are gone", File(serverDir, "junk.txt").exists())
        assertFalse("the marker is not part of the server", File(serverDir, BackupStore.MARKER).exists())
        assertFalse("no temp folder left", File(base, "servers/abc.restoring").exists())
    }

    @Test fun restoringKeepsASafetyBackupOfWhatWasThere() {
        val info = store.create("abc", serverDir, "")
        File(serverDir, "junk.txt").writeText("precious new work")
        store.restore("abc", info.name, serverDir)
        val safety = store.list("abc").firstOrNull { it.label == "before-restore" }
        assertNotNull("a before-restore backup must exist", safety)
        ZipFile(File(base, "backups/abc/${safety!!.name}")).use { z -> assertNotNull(z.getEntry("junk.txt")) }
    }

    @Test fun maliciousPathsAreRejectedBeforeAnythingIsTouched() {
        val outside = File(base, "servers/evil.txt")
        for (evil in listOf("../evil.txt", "../../evil.txt", "world/../../evil.txt", "/abs/evil.txt", "C:/evil.txt", "a\\..\\evil.txt")) {
            fakeBackup("20260101-000000.zip", evil to "pwned")
            val msg = message { store.restore("abc", "20260101-000000.zip", serverDir) }
            assertTrue("$evil -> $msg", msg.contains("unsafe path"))
            assertFalse(outside.exists())
            assertEquals("server untouched", "motd = \"original\"\n", File(serverDir, "pumpkin.toml").readText())
            assertTrue("no safety backup is made for a rejected file", store.list("abc").all { it.label != "before-restore" })
        }
    }

    @Test fun onlyDumpkinBackupsCanBeRestored() {
        fakeBackup("20260101-000001.zip", "pumpkin.toml" to "x", marker = false)
        assertTrue(message { store.restore("abc", "20260101-000001.zip", serverDir) }.contains("isn't a Dumpkin backup"))
        File(base, "backups/abc/20260101-000002.zip").writeText("this is not a zip at all")
        try { store.restore("abc", "20260101-000002.zip", serverDir); fail() } catch (_: Exception) { /* corrupt zip */ }
        assertEquals("motd = \"original\"\n", File(serverDir, "pumpkin.toml").readText())
    }

    @Test fun backupNamesCannotReachOtherFiles() {
        store.create("abc", serverDir, "")
        File(base, "backups/secret.zip").writeText("x")
        for (bad in listOf("../secret.zip", "..\\secret.zip", "20260101-000000.zip/../../secret.zip", "pumpkin.toml", "secret.zip")) {
            assertNull(bad, store.file("abc", bad))
            assertFalse(bad, store.delete("abc", bad))
        }
        assertTrue(File(base, "backups/secret.zip").exists())
        assertNull(runCatching { store.list("../servers") }.getOrNull()) // unsafe server ids are refused
    }

    @Test fun sameSecondBackupsGetDistinctNames() {
        val names = (1..4).map { store.create("abc", serverDir, "x").name }
        assertEquals(4, names.toSet().size)
        assertEquals(4, store.list("abc").size)
    }

    @Test fun labelsAreReducedToSafeSlugs() {
        assertEquals("a-b-c", BackupStore.slug("  A / B \\ C  "))
        assertEquals("", BackupStore.slug("../.."))
        assertEquals("caf", BackupStore.slug("café"))
        assertTrue(BackupStore.slug("x".repeat(100)).length <= 24)
    }

    @Test fun deleteAllRemovesAServersBackups() {
        store.create("abc", serverDir, "")
        store.deleteAll("abc")
        assertTrue(store.list("abc").isEmpty())
    }

    // ---- BackupCenter: the background jobs both UIs watch ------------------------------------------------------

    private fun center(active: () -> Boolean = { false }) = BackupCenter(
        store, CoroutineScope(SupervisorJob() + Dispatchers.IO),
        serverDir = { id -> serverDir.takeIf { id == "abc" } }, serverName = { "SMP" }, isActive = { active() },
        afterRestore = { File(serverDir, "after-restore.marker").writeText("ran") },
    )

    private fun BackupCenter.await(id: String): BackupJob {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            job(id)?.takeIf { !it.isRunning }?.let { return it }
            Thread.sleep(10)
        }
        fail("job did not finish"); error("unreachable")
    }

    @Test fun backgroundBackupThenRestore() {
        val c = center()
        assertNull(c.startBackup("abc", "nightly"))
        val done = c.await("abc")
        assertEquals(done.message, BackupJob.State.Done, done.state)
        val name = c.list("abc").single().name
        assertEquals(name, done.backupName)

        File(serverDir, "pumpkin.toml").writeText("motd = \"broken\"")
        assertNull(c.startRestore("abc", name))
        assertEquals(BackupJob.State.Done, c.await("abc").state)
        assertEquals("motd = \"original\"\n", File(serverDir, "pumpkin.toml").readText())
        assertEquals("afterRestore hook ran", "ran", File(serverDir, "after-restore.marker").readText())
        c.dismiss("abc")
        assertNull(c.job("abc"))
    }

    @Test fun restoreIsRefusedWhileTheServerRuns() {
        val c = center(active = { true })
        assertNull(c.startBackup("abc", "")) // backing up a running server is allowed
        c.await("abc")
        val name = c.list("abc").single().name
        assertTrue(c.startRestore("abc", name)!!.contains("Stop the server"))
        assertNotNull(c.startBackup("nope", "")) // unknown server
        assertNotNull(c.startRestore("abc", "20990101-000000.zip")) // unknown backup
    }

    @Test fun aFailedJobReportsWhyAndLeavesNothingBehind() {
        val c = center()
        serverDir.deleteRecursively()
        assertNull(c.startBackup("abc", ""))
        val job = c.await("abc")
        assertEquals(BackupJob.State.Failed, job.state)
        assertTrue(job.message!!.contains("doesn't exist"))
        assertTrue(c.list("abc").isEmpty())
        assertTrue("no .part files", File(base, "backups/abc").listFiles().orEmpty().none { it.name.endsWith(".part") })
    }
}

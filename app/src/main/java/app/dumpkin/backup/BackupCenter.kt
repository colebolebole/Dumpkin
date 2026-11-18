package app.dumpkin.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File

/** What a server's background backup or restore is doing (or just did). */
data class BackupJob(
    val kind: Kind,
    val backupName: String?,
    val progress: Float,
    val state: State,
    val message: String?,
    /** When this job last changed state; a finished job stops being shown a minute later. */
    val updatedAt: Long = System.currentTimeMillis(),
) {
    enum class Kind { Backup, Restore }
    enum class State { Running, Done, Failed }

    val isRunning get() = state == State.Running
}

/**
 * Runs backups and restores in the background and tracks one job per server, so the app and the web dashboard see the
 * same progress. Heavy work happens on [scope]'s dispatcher (use an IO one).
 */
class BackupCenter(
    private val store: BackupStore,
    private val scope: CoroutineScope,
    /** The server's folder, or null when there is no such server. */
    private val serverDir: (String) -> File?,
    private val serverName: (String) -> String?,
    private val isActive: (String) -> Boolean,
    private val appVersion: String = "",
    /** Called on the background thread after a successful restore, e.g. to rewrite settings that must not change. */
    private val afterRestore: (String) -> Unit = {},
) {
    private val _jobs = MutableStateFlow<Map<String, BackupJob>>(emptyMap())
    val jobs: StateFlow<Map<String, BackupJob>> = _jobs.asStateFlow()

    fun job(serverId: String): BackupJob? = _jobs.value[serverId]

    /** The running job, or one that finished in the last minute, so a UI can show how it ended. */
    fun visibleJob(serverId: String, now: Long = System.currentTimeMillis()): BackupJob? =
        _jobs.value[serverId]?.takeIf { it.isRunning || now - it.updatedAt < RESULT_VISIBLE_MS }

    fun list(serverId: String): List<BackupInfo> = runCatching { store.list(serverId) }.getOrDefault(emptyList())
    fun file(serverId: String, name: String): File? = runCatching { store.file(serverId, name) }.getOrNull()
    fun delete(serverId: String, name: String): Boolean = runCatching { store.delete(serverId, name) }.getOrDefault(false)
    fun deleteAll(serverId: String) { runCatching { store.deleteAll(serverId) }; _jobs.update { it - serverId } }

    private fun meta(serverId: String) = JSONObject()
        .put("serverId", serverId).put("serverName", serverName(serverId) ?: "").put("appVersion", appVersion)
        .put("serverWasRunning", isActive(serverId))

    /** @return null when the backup started, otherwise a message explaining why not. */
    fun startBackup(serverId: String, label: String): String? {
        val dir = serverDir(serverId) ?: return "That server no longer exists."
        if (!claim(serverId, BackupJob(BackupJob.Kind.Backup, null, 0f, BackupJob.State.Running, "Backing up…"))) {
            return "A backup or restore is already running for this server."
        }
        val meta = meta(serverId)
        scope.launch {
            try {
                val info = store.create(serverId, dir, label, meta) { p -> setProgress(serverId, p) }
                finish(serverId, BackupJob(BackupJob.Kind.Backup, info.name, 1f, BackupJob.State.Done, "Backup saved."))
            } catch (e: BackupError) {
                finish(serverId, BackupJob(BackupJob.Kind.Backup, null, 0f, BackupJob.State.Failed, e.message))
            } catch (e: Exception) {
                finish(serverId, BackupJob(BackupJob.Kind.Backup, null, 0f, BackupJob.State.Failed, "Backup failed: ${e.message}"))
            }
        }
        return null
    }

    /** @return null when the restore started, otherwise a message explaining why not. */
    fun startRestore(serverId: String, name: String): String? {
        val dir = serverDir(serverId) ?: return "That server no longer exists."
        if (isActive(serverId)) return "Stop the server before restoring a backup."
        if (store.file(serverId, name) == null) return "That backup no longer exists."
        if (!claim(serverId, BackupJob(BackupJob.Kind.Restore, name, 0f, BackupJob.State.Running, "Restoring…"))) {
            return "A backup or restore is already running for this server."
        }
        val meta = meta(serverId)
        scope.launch {
            try {
                store.restore(serverId, name, dir, meta) { p -> setProgress(serverId, p) }
                runCatching { afterRestore(serverId) }
                finish(serverId, BackupJob(BackupJob.Kind.Restore, name, 1f, BackupJob.State.Done, "Backup restored."))
            } catch (e: BackupError) {
                finish(serverId, BackupJob(BackupJob.Kind.Restore, name, 0f, BackupJob.State.Failed, e.message))
            } catch (e: Exception) {
                finish(serverId, BackupJob(BackupJob.Kind.Restore, name, 0f, BackupJob.State.Failed, "Restore failed: ${e.message}"))
            }
        }
        return null
    }

    /** Forgets a finished job's result (after the UI has shown it). */
    fun dismiss(serverId: String) {
        _jobs.update { m -> if (m[serverId]?.isRunning == false) m - serverId else m }
    }

    private fun claim(serverId: String, job: BackupJob): Boolean {
        var claimed = false
        _jobs.update { m ->
            if (m[serverId]?.isRunning == true) m else { claimed = true; m + (serverId to job) }
        }
        return claimed
    }

    private fun setProgress(serverId: String, p: Float) {
        _jobs.update { m -> m[serverId]?.takeIf { it.isRunning }?.let { m + (serverId to it.copy(progress = p)) } ?: m }
    }

    private fun finish(serverId: String, job: BackupJob) {
        _jobs.update { it + (serverId to job) }
    }

    companion object {
        const val RESULT_VISIBLE_MS = 60_000L
    }
}

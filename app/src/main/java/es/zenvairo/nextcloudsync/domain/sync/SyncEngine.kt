package es.zenvairo.nextcloudsync.domain.sync

import es.zenvairo.nextcloudsync.data.db.dao.ConflictDao
import es.zenvairo.nextcloudsync.data.db.dao.FileStateDao
import es.zenvairo.nextcloudsync.data.db.dao.SyncEventDao
import es.zenvairo.nextcloudsync.data.db.entity.ConflictEntity
import es.zenvairo.nextcloudsync.data.db.entity.FileStateEntity
import es.zenvairo.nextcloudsync.data.db.entity.SyncEventEntity
import es.zenvairo.nextcloudsync.data.db.entity.SyncEventType
import es.zenvairo.nextcloudsync.data.db.entity.SyncFolderEntity
import es.zenvairo.nextcloudsync.data.remote.ChunkedUploader
import es.zenvairo.nextcloudsync.data.remote.DavException
import es.zenvairo.nextcloudsync.data.remote.WebDavClient
import java.io.File

data class SyncSummary(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val deletedLocal: Int = 0,
    val deletedRemote: Int = 0,
    val conflicts: Int = 0,
    val errors: List<String> = emptyList()
)

private const val CHUNKED_UPLOAD_THRESHOLD = 20L * 1024 * 1024

/** Runs one full sync pass for a single folder pair: scan both sides, diff, execute, persist state. */
class SyncEngine(
    private val webDavClient: WebDavClient,
    private val chunkedUploader: ChunkedUploader,
    private val fileStateDao: FileStateDao,
    private val conflictDao: ConflictDao,
    private val syncEventDao: SyncEventDao,
    private val localScanner: LocalScanner = LocalScanner(),
    private val remoteScanner: RemoteScanner = RemoteScanner(webDavClient),
    private val diffCalculator: DiffCalculator = DiffCalculator(),
    private val conflictResolver: ConflictResolver = ConflictResolver()
) {
    suspend fun syncFolder(folder: SyncFolderEntity, filter: PathFilter): SyncSummary {
        val localRoot = File(folder.localPath)
        localRoot.mkdirs()

        val localSnapshots = localScanner.scan(localRoot, filter)
        val remoteSnapshots = remoteScanner.scan(folder.remotePath, filter)
        val knownStates = fileStateDao.getAllForFolder(folder.id).associateBy { it.relativePath }

        val allPaths = (localSnapshots.keys + remoteSnapshots.keys + knownStates.keys)
            .sortedBy { it.count { c -> c == '/' } } // parents before children

        var uploaded = 0; var downloaded = 0; var deletedLocal = 0; var deletedRemote = 0; var conflicts = 0
        val errors = mutableListOf<String>()

        for (path in allPaths) {
            val local = localSnapshots[path] ?: SideSnapshot(exists = false)
            val remote = remoteSnapshots[path] ?: SideSnapshot(exists = false)
            val known = knownStates[path]
            val plan = diffCalculator.decide(path, local, remote, known, folder.mode)

            try {
                when (plan.action) {
                    SyncAction.NO_OP -> Unit
                    SyncAction.FORGET -> fileStateDao.delete(folder.id, path)
                    SyncAction.DOWNLOAD -> {
                        downloadOne(folder, localRoot, path, plan.isDirectory, remote)
                        downloaded++
                    }
                    SyncAction.UPLOAD -> {
                        uploadOne(folder, localRoot, path, plan.isDirectory, local)
                        uploaded++
                    }
                    SyncAction.DELETE_LOCAL -> {
                        deleteLocal(localRoot, path)
                        fileStateDao.delete(folder.id, path)
                        deletedLocal++
                        logEvent(folder.id, SyncEventType.DELETED_LOCAL, path)
                    }
                    SyncAction.DELETE_REMOTE -> {
                        webDavClient.delete(remotePathOf(folder, path))
                        fileStateDao.delete(folder.id, path)
                        deletedRemote++
                        logEvent(folder.id, SyncEventType.DELETED_REMOTE, path)
                    }
                    SyncAction.CONFLICT -> {
                        conflicts++
                        handleConflict(folder, localRoot, path, plan.isDirectory, local, remote)
                    }
                }
            } catch (e: Exception) {
                errors += "$path: ${e.message}"
                logEvent(folder.id, SyncEventType.ERROR, path, e.message)
            }
        }

        return SyncSummary(uploaded, downloaded, deletedLocal, deletedRemote, conflicts, errors)
    }

    private suspend fun handleConflict(
        folder: SyncFolderEntity,
        localRoot: File,
        path: String,
        isDirectory: Boolean,
        local: SideSnapshot,
        remote: SideSnapshot
    ) {
        logEvent(folder.id, SyncEventType.CONFLICT, path)
        when (val outcome = conflictResolver.resolve(path, folder.conflictPolicy)) {
            is ConflictOutcome.PreferLocal -> if (local.exists) uploadOne(folder, localRoot, path, isDirectory, local)
            is ConflictOutcome.PreferRemote -> downloadOne(folder, localRoot, path, isDirectory, remote)
            is ConflictOutcome.KeepBoth -> {
                if (local.exists) {
                    File(localRoot, path).copyTo(File(localRoot, outcome.renamedLocalPath), overwrite = true)
                }
                if (remote.exists) downloadOne(folder, localRoot, path, isDirectory, remote)
            }
            is ConflictOutcome.DeferToUser -> {
                val existing = conflictDao.findOpen(folder.id, path)
                if (existing == null) {
                    conflictDao.insert(
                        ConflictEntity(
                            folderId = folder.id,
                            relativePath = path,
                            detectedAtMillis = System.currentTimeMillis(),
                            localSize = local.size,
                            localMtimeMillis = local.mtimeMillis,
                            remoteEtag = remote.etag
                        )
                    )
                }
            }
        }
    }

    private suspend fun downloadOne(folder: SyncFolderEntity, localRoot: File, path: String, isDirectory: Boolean, remote: SideSnapshot) {
        val destination = File(localRoot, path)
        if (isDirectory) {
            destination.mkdirs()
        } else {
            destination.parentFile?.mkdirs()
            webDavClient.download(remotePathOf(folder, path), destination)
            destination.setLastModified(remote.mtimeMillis)
        }
        fileStateDao.upsert(
            FileStateEntity(
                folderId = folder.id,
                relativePath = path,
                isDirectory = isDirectory,
                localSize = destination.length(),
                localMtimeMillis = destination.lastModified(),
                remoteEtag = remote.etag,
                remoteFileId = remote.fileId,
                lastSyncedAtMillis = System.currentTimeMillis()
            )
        )
        logEvent(folder.id, SyncEventType.DOWNLOADED, path)
    }

    private suspend fun uploadOne(folder: SyncFolderEntity, localRoot: File, path: String, isDirectory: Boolean, local: SideSnapshot) {
        val source = File(localRoot, path)
        val remotePath = remotePathOf(folder, path)
        val etag = if (isDirectory) {
            webDavClient.mkcol(remotePath)
            null
        } else if (source.length() >= CHUNKED_UPLOAD_THRESHOLD) {
            chunkedUploader.upload(remotePath, source, source.lastModified())
        } else {
            val known = fileStateDao.get(folder.id, path)
            try {
                webDavClient.upload(remotePath, source, known?.remoteEtag, source.lastModified())
            } catch (e: DavException.Conflict) {
                handleConflict(folder, localRoot, path, isDirectory, local, SideSnapshot(exists = true))
                return
            }
        }
        fileStateDao.upsert(
            FileStateEntity(
                folderId = folder.id,
                relativePath = path,
                isDirectory = isDirectory,
                localSize = local.size,
                localMtimeMillis = local.mtimeMillis,
                remoteEtag = etag,
                remoteFileId = fileStateDao.get(folder.id, path)?.remoteFileId,
                lastSyncedAtMillis = System.currentTimeMillis()
            )
        )
        logEvent(folder.id, SyncEventType.UPLOADED, path)
    }

    private fun deleteLocal(localRoot: File, path: String) {
        val target = File(localRoot, path)
        if (target.isDirectory) target.deleteRecursively() else target.delete()
    }

    private fun remotePathOf(folder: SyncFolderEntity, relativePath: String): String {
        val root = folder.remotePath.trimEnd('/')
        return if (relativePath.isEmpty()) root else "$root/$relativePath"
    }

    private suspend fun logEvent(folderId: Long, type: SyncEventType, path: String?, message: String? = null) {
        syncEventDao.insert(
            SyncEventEntity(
                folderId = folderId,
                type = type,
                relativePath = path,
                message = message,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }
}

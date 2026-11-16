package es.zenvairo.nextcloudsync.domain.sync

import es.zenvairo.nextcloudsync.data.db.entity.FileStateEntity
import es.zenvairo.nextcloudsync.domain.model.SyncMode

/**
 * Three-way diff: compares the current local and remote snapshots against the last known-good
 * [FileStateEntity] snapshot to classify what changed on each side independently, then decides
 * an action. This is what lets "deleted locally" be told apart from "never existed locally" -
 * a two-way (local vs remote only) diff cannot make that distinction.
 */
class DiffCalculator {

    fun decide(
        relativePath: String,
        local: SideSnapshot,
        remote: SideSnapshot,
        lastKnown: FileStateEntity?,
        mode: SyncMode
    ): PlannedChange {
        val localChanged = hasLocalChanged(local, lastKnown)
        val remoteChanged = hasRemoteChanged(remote, lastKnown)
        val isDirectory = local.isDirectory || remote.isDirectory

        val action = when {
            // Neither side ever recorded and both now absent - nothing to do.
            !local.exists && !remote.exists -> SyncAction.FORGET

            // First sight of a path that exists on exactly one side.
            lastKnown == null && local.exists && !remote.exists -> SyncAction.UPLOAD
            lastKnown == null && !local.exists && remote.exists -> SyncAction.DOWNLOAD
            lastKnown == null -> if (localChanged) SyncAction.CONFLICT else SyncAction.NO_OP

            // One side deleted since last sync, other side unchanged -> propagate delete.
            !local.exists && remote.exists && !remoteChanged -> SyncAction.DELETE_REMOTE
            local.exists && !remote.exists && !localChanged -> SyncAction.DELETE_LOCAL

            // One side deleted, but the *other* side also changed -> conflict, don't destroy data.
            !local.exists && remote.exists && remoteChanged -> SyncAction.CONFLICT
            local.exists && !remote.exists && localChanged -> SyncAction.CONFLICT

            // Both present.
            !localChanged && remoteChanged -> SyncAction.DOWNLOAD
            localChanged && !remoteChanged -> SyncAction.UPLOAD
            localChanged && remoteChanged -> SyncAction.CONFLICT
            else -> SyncAction.NO_OP
        }

        return PlannedChange(relativePath, filterForMode(action, mode), isDirectory)
    }

    /** Unidirectional modes only ever perform the actions they're allowed to; everything else becomes a no-op. */
    private fun filterForMode(action: SyncAction, mode: SyncMode): SyncAction = when (action) {
        SyncAction.UPLOAD -> if (mode.allowsLocalToRemote) action else SyncAction.NO_OP
        SyncAction.DOWNLOAD -> if (mode.allowsRemoteToLocal) action else SyncAction.NO_OP
        SyncAction.DELETE_REMOTE -> if (mode.propagatesDeletes && mode.allowsLocalToRemote) action else SyncAction.NO_OP
        SyncAction.DELETE_LOCAL -> if (mode.propagatesDeletes && mode.allowsRemoteToLocal) action else SyncAction.NO_OP
        else -> action
    }

    private fun hasLocalChanged(local: SideSnapshot, lastKnown: FileStateEntity?): Boolean {
        if (lastKnown == null) return local.exists
        if (!local.exists) return false
        return local.size != lastKnown.localSize || local.mtimeMillis != lastKnown.localMtimeMillis
    }

    private fun hasRemoteChanged(remote: SideSnapshot, lastKnown: FileStateEntity?): Boolean {
        if (lastKnown == null) return remote.exists
        if (!remote.exists) return false
        return remote.etag != lastKnown.remoteEtag
    }
}

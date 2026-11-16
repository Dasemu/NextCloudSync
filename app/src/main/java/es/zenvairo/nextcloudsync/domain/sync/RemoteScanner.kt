package es.zenvairo.nextcloudsync.domain.sync

import es.zenvairo.nextcloudsync.data.remote.DavException
import es.zenvairo.nextcloudsync.data.remote.WebDavClient

/**
 * Walks a remote folder tree via repeated Depth:1 PROPFINDs (Depth:infinity is disabled on most
 * Nextcloud instances for performance reasons) and returns every entry keyed by its path relative
 * to the synced folder's remote root.
 */
class RemoteScanner(private val webDavClient: WebDavClient) {

    suspend fun scan(remoteRoot: String, filter: PathFilter): Map<String, SideSnapshot> {
        val result = LinkedHashMap<String, SideSnapshot>()
        val normalizedRoot = if (remoteRoot.startsWith("/")) remoteRoot else "/$remoteRoot"
        try {
            webDavClient.mkcol(normalizedRoot)
        } catch (_: Exception) {
            // best-effort: folder may already exist or the account may lack create rights yet
        }
        walk(normalizedRoot, "", filter, result)
        return result
    }

    private suspend fun walk(remoteRoot: String, relativeDir: String, filter: PathFilter, out: MutableMap<String, SideSnapshot>) {
        val path = if (relativeDir.isEmpty()) remoteRoot else "$remoteRoot/$relativeDir"
        val entries = try {
            webDavClient.propfind(path, depth = 1)
        } catch (e: DavException.NotFound) {
            return
        }
        for (entry in entries) {
            val name = entry.path.substringAfterLast('/')
            val relative = if (relativeDir.isEmpty()) name else "$relativeDir/$name"
            if (filter.isExcluded(relative, entry.isDirectory, entry.size)) continue
            out[relative] = SideSnapshot(
                exists = true,
                isDirectory = entry.isDirectory,
                size = entry.size,
                mtimeMillis = entry.lastModifiedMillis,
                etag = entry.etag,
                fileId = entry.fileId
            )
            if (entry.isDirectory) walk(remoteRoot, relative, filter, out)
        }
    }
}

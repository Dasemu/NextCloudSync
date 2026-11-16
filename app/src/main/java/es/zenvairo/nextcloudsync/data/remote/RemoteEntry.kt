package es.zenvairo.nextcloudsync.data.remote

/** One entry returned by a PROPFIND, path relative to the files root (leading slash, no host). */
data class RemoteEntry(
    val path: String,
    val isDirectory: Boolean,
    val etag: String?,
    val fileId: String?,
    val size: Long,
    val lastModifiedMillis: Long
)

sealed class DavException(message: String) : Exception(message) {
    class NotFound(path: String) : DavException("Not found: $path")
    class Conflict(path: String) : DavException("Conflict (412) writing $path")
    class Locked(path: String) : DavException("Locked (423): $path")
    class InsufficientStorage(path: String) : DavException("Server out of space (507) for $path")
    class Unauthorized(path: String) : DavException("Unauthorized: $path")
    class Http(val code: Int, path: String) : DavException("HTTP $code for $path")
    class Network(cause: Throwable) : DavException(cause.message ?: "Network error")
}

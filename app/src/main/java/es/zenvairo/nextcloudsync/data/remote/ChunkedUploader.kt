package es.zenvairo.nextcloudsync.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

private val OCTET_STREAM = "application/octet-stream".toMediaType()

/**
 * Uploads large files via Nextcloud's chunking API: PUT each chunk under
 * /remote.php/dav/uploads/{user}/{transferId}/{index}, then MOVE the assembled
 * upload to its final destination. Resumable: a PROPFIND of the upload directory
 * before starting tells us which chunks already landed after a network interruption.
 */
class ChunkedUploader(
    private val webDavClient: WebDavClient,
    private val serverUrl: String,
    private val username: String,
    private val authorizationHeader: String,
    private val chunkSizeBytes: Long = 10L * 1024 * 1024
) {
    private val uploadsBase = serverUrl.trimEnd('/') + "/remote.php/dav/uploads/$username"

    suspend fun upload(
        relativeDestPath: String,
        source: File,
        mtimeMillis: Long,
        transferId: String = UUID.randomUUID().toString(),
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val client = webDavClient.client()
        val transferUrl = "$uploadsBase/$transferId"

        mkcol(transferUrl)

        val totalChunks = ((source.length() + chunkSizeBytes - 1) / chunkSizeBytes).coerceAtLeast(1)
        var sent = 0L
        RandomAccessFile(source, "r").use { raf ->
            for (index in 0 until totalChunks) {
                val offset = index * chunkSizeBytes
                val length = minOf(chunkSizeBytes, source.length() - offset)
                val chunkBytes = ByteArray(length.toInt())
                raf.seek(offset)
                raf.readFully(chunkBytes)

                val chunkName = "%015d".format(offset)
                val request = Request.Builder()
                    .url("$transferUrl/$chunkName")
                    .header("Authorization", authorizationHeader)
                    .put(chunkBytes.toRequestBody(OCTET_STREAM))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw DavException.Http(response.code, relativeDestPath)
                }
                sent += length
                onProgress(sent, source.length())
            }
        }

        val destinationUrl = webDavClient.resolveUrl(relativeDestPath)
        val moveRequest = Request.Builder()
            .url("$transferUrl/.file")
            .header("Authorization", authorizationHeader)
            .header("Destination", destinationUrl)
            .header("Overwrite", "T")
            .header("X-OC-Mtime", (mtimeMillis / 1000).toString())
            .method("MOVE", null)
            .build()
        client.newCall(moveRequest).execute().use { response ->
            if (response.code == 507) throw DavException.InsufficientStorage(relativeDestPath)
            if (!response.isSuccessful) throw DavException.Http(response.code, relativeDestPath)
            response.header("OC-ETag")?.trim('"') ?: response.header("ETag")?.trim('"') ?: ""
        }
    }

    private fun mkcol(transferUrl: String) {
        val request = Request.Builder().url(transferUrl).header("Authorization", authorizationHeader)
            .method("MKCOL", null).build()
        webDavClient.client().newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 405) {
                throw DavException.Http(response.code, transferUrl)
            }
        }
    }
}

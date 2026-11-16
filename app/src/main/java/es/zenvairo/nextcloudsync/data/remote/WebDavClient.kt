package es.zenvairo.nextcloudsync.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
private val OCTET_STREAM = "application/octet-stream".toMediaType()

/** Thin WebDAV client scoped to one Nextcloud account's files root, e.g. /remote.php/dav/files/alice. */
class WebDavClient(
    private val okHttpClient: OkHttpClient,
    private val serverUrl: String,
    private val username: String,
    appPassword: String
) {
    private val credentials = Credentials.basic(username, appPassword)
    val davRoot = "/remote.php/dav/files/$username"
    private val baseUrl = serverUrl.trimEnd('/') + davRoot
    private val parser = PropfindParser()

    private fun urlFor(relativePath: String): String {
        val encoded = relativePath.trim('/').split('/').joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return "$baseUrl/$encoded"
    }

    suspend fun propfind(relativePath: String, depth: Int = 1): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val body = PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE)
        val request = Request.Builder()
            .url(urlFor(relativePath))
            .header("Authorization", credentials)
            .header("Depth", depth.toString())
            .method("PROPFIND", body)
            .build()
        execute(request, relativePath).use { response ->
            if (response.code == 404) throw DavException.NotFound(relativePath)
            if (!response.isSuccessful) throw statusException(response.code, relativePath)
            val stream = response.body?.byteStream() ?: return@use emptyList()
            parser.parse(stream, davRoot, queriedPath = relativePath)
        }
    }

    suspend fun download(relativePath: String, destination: File, ifNoneMatchEtag: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(urlFor(relativePath)).header("Authorization", credentials)
            if (ifNoneMatchEtag != null) builder.header("If-None-Match", "\"$ifNoneMatchEtag\"")
            execute(builder.build(), relativePath).use { response ->
                if (response.code == 304) return@use false
                if (response.code == 404) throw DavException.NotFound(relativePath)
                if (!response.isSuccessful) throw statusException(response.code, relativePath)
                val tmp = File(destination.parentFile, "${destination.name}.part")
                tmp.parentFile?.mkdirs()
                response.body?.byteStream()?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!tmp.renameTo(destination)) {
                    tmp.copyTo(destination, overwrite = true)
                    tmp.delete()
                }
                true
            }
        }

    /** Small-file upload with optimistic concurrency via If-Match; use [ChunkedUploader] for large files. */
    suspend fun upload(relativePath: String, source: File, ifMatchEtag: String?, mtimeMillis: Long): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(urlFor(relativePath))
                .header("Authorization", credentials)
                .header("X-OC-Mtime", (mtimeMillis / 1000).toString())
                .put(source.asRequestBody(OCTET_STREAM))
            if (ifMatchEtag != null) builder.header("If-Match", "\"$ifMatchEtag\"")
            execute(builder.build(), relativePath).use { response ->
                if (response.code == 412) throw DavException.Conflict(relativePath)
                if (response.code == 423) throw DavException.Locked(relativePath)
                if (response.code == 507) throw DavException.InsufficientStorage(relativePath)
                if (!response.isSuccessful) throw statusException(response.code, relativePath)
                response.header("OC-ETag")?.trim('"') ?: response.header("ETag")?.trim('"') ?: ""
            }
        }

    suspend fun mkcol(relativePath: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(urlFor(relativePath)).header("Authorization", credentials)
            .method("MKCOL", null).build()
        execute(request, relativePath).use { response ->
            // 405 = already exists, treat as success.
            if (!response.isSuccessful && response.code != 405) throw statusException(response.code, relativePath)
        }
    }

    suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(urlFor(relativePath)).header("Authorization", credentials)
            .delete().build()
        execute(request, relativePath).use { response ->
            if (!response.isSuccessful && response.code != 404) throw statusException(response.code, relativePath)
        }
    }

    suspend fun move(fromRelativePath: String, toRelativePath: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(urlFor(fromRelativePath)).header("Authorization", credentials)
            .header("Destination", urlFor(toRelativePath))
            .header("Overwrite", "F")
            .method("MOVE", null).build()
        execute(request, fromRelativePath).use { response ->
            if (!response.isSuccessful) throw statusException(response.code, fromRelativePath)
        }
    }

    fun authorizationHeader(): String = credentials
    fun resolveUrl(relativePath: String): String = urlFor(relativePath)
    fun client(): OkHttpClient = okHttpClient

    private fun execute(request: Request, path: String) = try {
        okHttpClient.newCall(request).execute().also {
            if (it.code == 401) throw DavException.Unauthorized(path)
        }
    } catch (e: IOException) {
        throw DavException.Network(e)
    }

    private fun statusException(code: Int, path: String) = DavException.Http(code, path)

    companion object {
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:prop>
    <d:getetag/>
    <d:getlastmodified/>
    <d:getcontentlength/>
    <d:resourcetype/>
    <oc:fileid/>
    <oc:size/>
    <oc:permissions/>
  </d:prop>
</d:propfind>"""
    }
}

package es.zenvairo.nextcloudsync.data.remote

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses a WebDAV multistatus PROPFIND response. Namespace prefixes on Nextcloud servers are
 * conventionally d:/oc:/nc: but are not guaranteed, so elements are matched by local name only.
 */
class PropfindParser {

    private val rfc1123 = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

    /**
     * @param davRoot e.g. "/remote.php/dav/files/alice" - stripped from each href to get the relative path.
     * @param queriedPath the relative path (from [davRoot]) that was PROPFIND'd, e.g. "/Photos" or "" for
     *   the root. A depth:1 PROPFIND always echoes back the queried resource itself as one of the
     *   responses; that self-entry is skipped by comparing against this rather than only against "",
     *   otherwise scanning any subfolder would misread its own self-entry as a child of itself.
     */
    fun parse(input: InputStream, davRoot: String, queriedPath: String = ""): List<RemoteEntry> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")

        val entries = mutableListOf<RemoteEntry>()
        var href: String? = null
        var etag: String? = null
        var fileId: String? = null
        var size: Long = 0
        var lastModified: Long = 0
        var isCollection = false
        var anyPropstatOk = false
        // Status is per-<propstat>, not per-<response>: a server answers one PROPFIND with several
        // propstat blocks, typically a 200 carrying the properties it has plus a 404 listing the
        // ones it doesn't (getcontentlength on a collection, for instance). Treating the 404 as the
        // response's status would discard the entry entirely - which silently dropped every folder.
        var propstatOk = true
        var stagedEtag: String? = null
        var stagedFileId: String? = null
        var stagedSize: Long? = null
        var stagedLastModified: Long? = null
        var stagedCollection = false
        var inResponse = false
        var text = StringBuilder()

        val normalizedQueriedPath = queriedPath.trim('/')
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text.setLength(0)
                    when (parser.name) {
                        "response" -> {
                            inResponse = true
                            href = null; etag = null; fileId = null; size = 0; lastModified = 0
                            isCollection = false; anyPropstatOk = false
                        }
                        "propstat" -> {
                            propstatOk = true
                            stagedEtag = null; stagedFileId = null; stagedSize = null
                            stagedLastModified = null; stagedCollection = false
                        }
                        "collection" -> stagedCollection = true
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (inResponse) {
                        when (parser.name) {
                            "href" -> href = URLDecoder.decode(text.toString().trim(), "UTF-8")
                            "getetag" -> stagedEtag = text.toString().trim().trim('"')
                            "fileid" -> stagedFileId = text.toString().trim()
                            "getcontentlength", "size" -> text.toString().trim().toLongOrNull()?.let { stagedSize = it }
                            "getlastmodified" -> stagedLastModified = parseRfc1123(text.toString().trim())
                            "status" -> if (text.isNotBlank() && !text.contains("200")) propstatOk = false
                            "propstat" -> {
                                if (propstatOk) {
                                    anyPropstatOk = true
                                    stagedEtag?.let { etag = it }
                                    stagedFileId?.let { fileId = it }
                                    stagedSize?.let { size = it }
                                    stagedLastModified?.let { lastModified = it }
                                    if (stagedCollection) isCollection = true
                                }
                            }
                            "response" -> {
                                inResponse = false
                                val h = href
                                if (h != null && anyPropstatOk) {
                                    val relative = toRelativePath(h, davRoot)
                                    // Skip the PROPFIND target itself, echoed back by depth:1 requests.
                                    if (relative.isNotEmpty() && relative.trim('/') != normalizedQueriedPath) {
                                        entries += RemoteEntry(
                                            path = relative,
                                            isDirectory = isCollection,
                                            etag = etag,
                                            fileId = fileId,
                                            size = size,
                                            lastModifiedMillis = lastModified
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return entries
    }

    private fun toRelativePath(href: String, davRoot: String): String {
        var path = href
        val schemeIdx = path.indexOf("://")
        if (schemeIdx >= 0) {
            val afterHost = path.indexOf('/', schemeIdx + 3)
            path = if (afterHost >= 0) path.substring(afterHost) else "/"
        }
        val root = if (davRoot.endsWith("/")) davRoot else "$davRoot/"
        path = when {
            path.startsWith(root) -> path.substring(root.length)
            path.startsWith(davRoot) -> path.substring(davRoot.length).trimStart('/')
            else -> path
        }
        if (path.endsWith("/") && path.length > 1) path = path.dropLast(1)
        return path
    }

    private fun parseRfc1123(value: String): Long = runCatching { rfc1123.parse(value)?.time }.getOrNull() ?: 0
}

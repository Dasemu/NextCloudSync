package es.zenvairo.nextcloudsync.ui.foldereditor

import android.net.Uri
import android.os.Environment

/**
 * The app has MANAGE_EXTERNAL_STORAGE, so it works with real filesystem paths rather than SAF
 * document URIs. [android.content.Intent.ACTION_OPEN_DOCUMENT_TREE] is still the best system
 * folder picker UI, so it's used purely for its picker experience and the resulting tree URI is
 * translated back into an absolute path on primary external storage.
 */
object DocumentTreeUriMapper {
    fun toAbsolutePath(treeUri: Uri): String? {
        val lastSegment = treeUri.lastPathSegment ?: return null // e.g. "primary:Pictures/Camera"
        val parts = lastSegment.split(":", limit = 2)
        if (parts.size != 2) return null
        val (volume, relativePath) = parts
        val root = if (volume == "primary") {
            Environment.getExternalStorageDirectory().path
        } else {
            "/storage/$volume"
        }
        return if (relativePath.isBlank()) root else "$root/$relativePath"
    }
}

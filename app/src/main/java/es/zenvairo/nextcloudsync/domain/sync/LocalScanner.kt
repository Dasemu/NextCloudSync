package es.zenvairo.nextcloudsync.domain.sync

import java.io.File

/** Walks a local folder tree and returns every entry keyed by its path relative to the root. */
class LocalScanner {

    fun scan(root: File, filter: PathFilter): Map<String, SideSnapshot> {
        val result = LinkedHashMap<String, SideSnapshot>()
        if (!root.exists()) return result
        walk(root, root, filter, result)
        return result
    }

    private fun walk(root: File, dir: File, filter: PathFilter, out: MutableMap<String, SideSnapshot>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val relative = child.relativeTo(root).path.replace(File.separatorChar, '/')
            if (filter.isExcludedFile(child, relative)) continue
            out[relative] = SideSnapshot(
                exists = true,
                isDirectory = child.isDirectory,
                size = if (child.isFile) child.length() else 0,
                mtimeMillis = child.lastModified()
            )
            if (child.isDirectory) walk(root, child, filter, out)
        }
    }
}

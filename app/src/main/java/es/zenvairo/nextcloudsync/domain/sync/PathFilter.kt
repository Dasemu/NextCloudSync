package es.zenvairo.nextcloudsync.domain.sync

import es.zenvairo.nextcloudsync.domain.model.SyncConstraints
import java.io.File

/** Applies exclude globs, hidden-file skipping and max-size filtering to a candidate path. */
class PathFilter(private val constraints: SyncConstraints) {

    private val excludeRegexes = constraints.excludeGlobs.map { globToRegex(it) }

    fun isExcluded(relativePath: String, isDirectory: Boolean, sizeBytes: Long): Boolean {
        val name = relativePath.substringAfterLast('/')
        if (constraints.skipHidden && name.startsWith(".")) return true
        if (excludeRegexes.any { it.matches(relativePath) || it.matches(name) }) return true
        if (!isDirectory) {
            val maxSize = constraints.maxFileSizeBytes
            if (maxSize != null && sizeBytes > maxSize) return true
        }
        return false
    }

    fun isExcludedFile(file: File, relativePath: String): Boolean =
        isExcluded(relativePath, file.isDirectory, if (file.isFile) file.length() else 0)

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder()
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        sb.append(".*"); i++
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append("[^/]")
                '.', '(', ')', '+', '|', '^', '$', '{', '}', '[', ']', '\\' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i++
        }
        return Regex(sb.toString())
    }
}

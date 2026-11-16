package es.zenvairo.nextcloudsync.domain.sync

import es.zenvairo.nextcloudsync.domain.model.ConflictPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ConflictOutcome {
    /** Duplicate the local file under a new name, then treat the original path as a plain download. */
    data class KeepBoth(val renamedLocalPath: String) : ConflictOutcome()
    object PreferLocal : ConflictOutcome()
    object PreferRemote : ConflictOutcome()
    /** Policy is ASK: leave both sides untouched and record it in the conflicts table for the user. */
    object DeferToUser : ConflictOutcome()
}

class ConflictResolver {
    private val stamp = SimpleDateFormat("yyyy-MM-dd HHmmss", Locale.US)

    fun resolve(relativePath: String, policy: ConflictPolicy): ConflictOutcome = when (policy) {
        ConflictPolicy.PREFER_LOCAL -> ConflictOutcome.PreferLocal
        ConflictPolicy.PREFER_REMOTE -> ConflictOutcome.PreferRemote
        ConflictPolicy.ASK -> ConflictOutcome.DeferToUser
        ConflictPolicy.KEEP_BOTH -> ConflictOutcome.KeepBoth(renamedPath(relativePath))
    }

    private fun renamedPath(relativePath: String): String {
        val dot = relativePath.lastIndexOf('.')
        val slash = relativePath.lastIndexOf('/')
        val suffix = " (conflict ${stamp.format(Date())})"
        return if (dot > slash) {
            relativePath.substring(0, dot) + suffix + relativePath.substring(dot)
        } else {
            relativePath + suffix
        }
    }
}

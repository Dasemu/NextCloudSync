package es.zenvairo.nextcloudsync.domain.model

/**
 * How a [es.zenvairo.nextcloudsync.data.db.entity.SyncFolderEntity] propagates changes.
 * MIRROR_* modes propagate deletes; UPLOAD_ONLY/DOWNLOAD_ONLY are additive-only and never delete.
 */
enum class SyncMode {
    BIDIRECTIONAL,
    UPLOAD_ONLY,
    DOWNLOAD_ONLY,
    MIRROR_UP,
    MIRROR_DOWN;

    val allowsLocalToRemote: Boolean
        get() = this == BIDIRECTIONAL || this == UPLOAD_ONLY || this == MIRROR_UP

    val allowsRemoteToLocal: Boolean
        get() = this == BIDIRECTIONAL || this == DOWNLOAD_ONLY || this == MIRROR_DOWN

    val propagatesDeletes: Boolean
        get() = this == BIDIRECTIONAL || this == MIRROR_UP || this == MIRROR_DOWN
}

enum class ConflictPolicy {
    KEEP_BOTH,
    PREFER_LOCAL,
    PREFER_REMOTE,
    ASK
}

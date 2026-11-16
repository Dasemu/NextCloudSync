package es.zenvairo.nextcloudsync.domain.sync

import com.google.common.truth.Truth.assertThat
import es.zenvairo.nextcloudsync.data.db.entity.FileStateEntity
import es.zenvairo.nextcloudsync.domain.model.SyncMode
import org.junit.Test

class DiffCalculatorTest {

    private val calculator = DiffCalculator()
    private val absent = SideSnapshot(exists = false)

    private fun known(localSize: Long = 10, localMtime: Long = 1000, etag: String? = "etag-1") = FileStateEntity(
        folderId = 1, relativePath = "a.txt", isDirectory = false,
        localSize = localSize, localMtimeMillis = localMtime,
        remoteEtag = etag, remoteFileId = "fid-1", lastSyncedAtMillis = 500
    )

    @Test
    fun `new file only local uploads`() {
        val local = SideSnapshot(exists = true, size = 10, mtimeMillis = 1000)
        val result = calculator.decide("a.txt", local, absent, null, SyncMode.BIDIRECTIONAL)
        assertThat(result.action).isEqualTo(SyncAction.UPLOAD)
    }

    @Test
    fun `new file only remote downloads`() {
        val remote = SideSnapshot(exists = true, size = 10, etag = "e1")
        val result = calculator.decide("a.txt", absent, remote, null, SyncMode.BIDIRECTIONAL)
        assertThat(result.action).isEqualTo(SyncAction.DOWNLOAD)
    }

    @Test
    fun `unchanged both sides is no-op`() {
        val state = known()
        val local = SideSnapshot(exists = true, size = state.localSize, mtimeMillis = state.localMtimeMillis)
        val remote = SideSnapshot(exists = true, etag = state.remoteEtag)
        val result = calculator.decide("a.txt", local, remote, state, SyncMode.BIDIRECTIONAL)
        assertThat(result.action).isEqualTo(SyncAction.NO_OP)
    }

    @Test
    fun `local modified only uploads`() {
        val state = known()
        val local = SideSnapshot(exists = true, size = 99, mtimeMillis = 9999)
        val remote = SideSnapshot(exists = true, etag = state.remoteEtag)
        val result = calculator.decide("a.txt", local, remote, state, SyncMode.BIDIRECTIONAL)
        assertThat(result.action).isEqualTo(SyncAction.UPLOAD)
    }

    @Test
    fun `remote modified only downloads`() {
        val state = known()
        val local = SideSnapshot(exists = true, size = state.localSize, mtimeMillis = state.localMtimeMillis)
        val remote = SideSnapshot(exists = true, etag = "etag-2")
        val result = calculator.decide("a.txt", local, remote, state, SyncMode.BIDIRECTIONAL)
        assertThat(result.action).isEqualTo(SyncAction.DOWNLOAD)
    }

    @Test
    fun `both sides modified is a conflict`() {
        val state = known()
        val local = SideSnapshot(exists = true, size = 42, mtimeMillis = 42)
        val remote = SideSnapshot(exists = true, etag = "etag-2")
        val result = calculator.decide("a.txt", local, remote, state, SyncMode.BIDIRECTIONAL)
        assertThat(result.action).isEqualTo(SyncAction.CONFLICT)
    }

    @Test
    fun `deleted locally unchanged remote deletes remote`() {
        val state = known()
        val remote = SideSnapshot(exists = true, etag = state.remoteEtag)
        val result = calculator.decide("a.txt", absent, remote, state, SyncMode.BIDIRECTIONAL)
        assertThat(result.action).isEqualTo(SyncAction.DELETE_REMOTE)
    }

    @Test
    fun `deleted remotely unchanged local deletes local`() {
        val state = known()
        val local = SideSnapshot(exists = true, size = state.localSize, mtimeMillis = state.localMtimeMillis)
        val result = calculator.decide("a.txt", local, absent, state, SyncMode.BIDIRECTIONAL)
        assertThat(result.action).isEqualTo(SyncAction.DELETE_LOCAL)
    }

    @Test
    fun `deleted locally but remote also changed is a conflict not silent data loss`() {
        val state = known()
        val remote = SideSnapshot(exists = true, etag = "etag-2")
        val result = calculator.decide("a.txt", absent, remote, state, SyncMode.BIDIRECTIONAL)
        assertThat(result.action).isEqualTo(SyncAction.CONFLICT)
    }

    @Test
    fun `both deleted forgets the path`() {
        val state = known()
        val result = calculator.decide("a.txt", absent, absent, state, SyncMode.BIDIRECTIONAL)
        assertThat(result.action).isEqualTo(SyncAction.FORGET)
    }

    @Test
    fun `upload-only mode never deletes local even if remote deleted`() {
        val state = known()
        val local = SideSnapshot(exists = true, size = state.localSize, mtimeMillis = state.localMtimeMillis)
        val result = calculator.decide("a.txt", local, absent, state, SyncMode.UPLOAD_ONLY)
        assertThat(result.action).isEqualTo(SyncAction.NO_OP)
    }

    @Test
    fun `download-only mode never uploads local changes`() {
        val state = known()
        val local = SideSnapshot(exists = true, size = 999, mtimeMillis = 999)
        val remote = SideSnapshot(exists = true, etag = state.remoteEtag)
        val result = calculator.decide("a.txt", local, remote, state, SyncMode.DOWNLOAD_ONLY)
        assertThat(result.action).isEqualTo(SyncAction.NO_OP)
    }

    @Test
    fun `mirror-up propagates local deletes to remote`() {
        val state = known()
        val remote = SideSnapshot(exists = true, etag = state.remoteEtag)
        val result = calculator.decide("a.txt", absent, remote, state, SyncMode.MIRROR_UP)
        assertThat(result.action).isEqualTo(SyncAction.DELETE_REMOTE)
    }
}

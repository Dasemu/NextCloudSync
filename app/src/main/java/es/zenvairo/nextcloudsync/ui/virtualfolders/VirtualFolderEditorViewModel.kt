package es.zenvairo.nextcloudsync.ui.virtualfolders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.zenvairo.nextcloudsync.data.db.dao.VirtualFolderDao
import es.zenvairo.nextcloudsync.data.db.entity.VirtualFolderEntity
import es.zenvairo.nextcloudsync.domain.vfs.VirtualFolderManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VirtualFolderEditorViewModel @Inject constructor(
    private val virtualFolderDao: VirtualFolderDao,
    private val manager: VirtualFolderManager
) : ViewModel() {

    suspend fun load(id: Long): VirtualFolderEntity? = virtualFolderDao.getById(id)

    fun save(id: Long?, displayName: String, remotePath: String, cacheMaxBytes: Long) {
        viewModelScope.launch {
            val existing = id?.let { virtualFolderDao.getById(it) }
            if (existing == null) {
                virtualFolderDao.insert(
                    VirtualFolderEntity(
                        displayName = displayName,
                        remotePath = normalize(remotePath),
                        cacheMaxBytes = cacheMaxBytes
                    )
                )
            } else {
                // The mountpoint is derived from the display name and the served tree from the
                // remote path, so an edit to either has to take the current mount down with it.
                val renamed = existing.displayName != displayName || existing.remotePath != normalize(remotePath)
                if (renamed) manager.unmount(existing)
                virtualFolderDao.update(
                    existing.copy(
                        displayName = displayName,
                        remotePath = normalize(remotePath),
                        cacheMaxBytes = cacheMaxBytes
                    )
                )
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val folder = virtualFolderDao.getById(id) ?: return@launch
            manager.forget(folder)
            virtualFolderDao.delete(folder)
        }
    }

    /** WebDavClient expects a path relative to the files root, leading slash, no trailing one. */
    private fun normalize(path: String): String =
        "/" + path.trim().trim('/')
}

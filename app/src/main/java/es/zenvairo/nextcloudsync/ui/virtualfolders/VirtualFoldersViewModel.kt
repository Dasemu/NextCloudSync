package es.zenvairo.nextcloudsync.ui.virtualfolders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.zenvairo.nextcloudsync.data.db.dao.VirtualFolderDao
import es.zenvairo.nextcloudsync.data.db.entity.VirtualFolderEntity
import es.zenvairo.nextcloudsync.domain.vfs.MountState
import es.zenvairo.nextcloudsync.domain.vfs.VirtualFolderManager
import es.zenvairo.nextcloudsync.root.RootAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VirtualFoldersViewModel @Inject constructor(
    private val virtualFolderDao: VirtualFolderDao,
    private val manager: VirtualFolderManager,
    private val rootAvailability: RootAvailability
) : ViewModel() {

    val folders: StateFlow<List<VirtualFolderEntity>> = virtualFolderDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mountStates: StateFlow<Map<Long, MountState>> = manager.states

    /** Null until probed - the UI shows the root warning only once we actually know. */
    private val _hasRoot = MutableStateFlow<Boolean?>(null)
    val hasRoot: StateFlow<Boolean?> = _hasRoot.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _hasRoot.value = rootAvailability.isAvailable()
            manager.refreshStates(folders.value)
        }
    }

    /** After the user has gone to grant root in their manager, the cached "no" is stale. */
    fun recheckRoot() {
        rootAvailability.invalidate()
        refresh()
    }

    fun mountPathFor(folder: VirtualFolderEntity) = manager.mountPathFor(folder)

    fun mount(folder: VirtualFolderEntity) = viewModelScope.launch { manager.mount(folder) }

    /** Disabled folders are skipped by the boot remount; unmount one straight away when turned off. */
    fun setEnabled(folder: VirtualFolderEntity, enabled: Boolean) = viewModelScope.launch {
        virtualFolderDao.update(folder.copy(enabled = enabled))
        if (!enabled) manager.unmount(folder)
    }

    fun unmount(folder: VirtualFolderEntity) = viewModelScope.launch { manager.unmount(folder) }

    fun delete(folder: VirtualFolderEntity) = viewModelScope.launch {
        manager.forget(folder)
        virtualFolderDao.delete(folder)
    }
}

package es.zenvairo.nextcloudsync.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.zenvairo.nextcloudsync.data.db.dao.ConflictDao
import es.zenvairo.nextcloudsync.data.db.dao.SyncFolderDao
import es.zenvairo.nextcloudsync.data.db.entity.SyncFolderEntity
import es.zenvairo.nextcloudsync.work.SyncScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderUiModel(val entity: SyncFolderEntity, val unresolvedConflicts: Int)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val syncFolderDao: SyncFolderDao,
    private val conflictDao: ConflictDao,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    val folders: StateFlow<List<FolderUiModel>> = syncFolderDao.observeAll()
        .combine(conflictDao.observeUnresolved()) { folders, conflicts ->
            val countsByFolder = conflicts.groupingBy { it.folderId }.eachCount()
            folders.map { FolderUiModel(it, countsByFolder[it.id] ?: 0) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun syncNow() {
        syncScheduler.requestExpeditedSync()
    }

    fun setEnabled(folder: SyncFolderEntity, enabled: Boolean) {
        viewModelScope.launch { syncFolderDao.update(folder.copy(enabled = enabled)) }
    }
}

package es.zenvairo.nextcloudsync.ui.foldereditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.zenvairo.nextcloudsync.data.db.dao.SyncFolderDao
import es.zenvairo.nextcloudsync.data.db.entity.SyncFolderEntity
import es.zenvairo.nextcloudsync.data.prefs.SettingsRepository
import es.zenvairo.nextcloudsync.domain.model.ConflictPolicy
import es.zenvairo.nextcloudsync.domain.model.SyncConstraints
import es.zenvairo.nextcloudsync.domain.model.SyncMode
import es.zenvairo.nextcloudsync.work.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderEditorState(
    val id: Long? = null,
    val displayName: String = "",
    val localPath: String = "",
    val remotePath: String = "",
    val mode: SyncMode = SyncMode.BIDIRECTIONAL,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
    val useGlobalConstraints: Boolean = true,
    val constraints: SyncConstraints = SyncConstraints(),
    val saved: Boolean = false,
    val deleted: Boolean = false
)

@HiltViewModel
class FolderEditorViewModel @Inject constructor(
    private val syncFolderDao: SyncFolderDao,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(FolderEditorState())
    val state: StateFlow<FolderEditorState> = _state

    fun load(folderId: Long?) {
        if (folderId == null) return
        viewModelScope.launch {
            val entity = syncFolderDao.getById(folderId) ?: return@launch
            val constraints = entity.constraintsJson?.let { runCatching { settingsRepository.decode(it) }.getOrNull() }
            _state.update {
                it.copy(
                    id = entity.id,
                    displayName = entity.displayName,
                    localPath = entity.localPath,
                    remotePath = entity.remotePath,
                    mode = entity.mode,
                    conflictPolicy = entity.conflictPolicy,
                    useGlobalConstraints = constraints == null,
                    constraints = constraints ?: SyncConstraints()
                )
            }
        }
    }

    fun update(transform: (FolderEditorState) -> FolderEditorState) = _state.update(transform)

    fun save() {
        viewModelScope.launch {
            val current = _state.value
            val entity = SyncFolderEntity(
                id = current.id ?: 0,
                displayName = current.displayName.ifBlank { current.localPath.substringAfterLast('/') },
                localPath = current.localPath,
                remotePath = current.remotePath,
                mode = current.mode,
                conflictPolicy = current.conflictPolicy,
                constraintsJson = if (current.useGlobalConstraints) null else settingsRepository.encode(current.constraints)
            )
            if (current.id == null) syncFolderDao.insert(entity) else syncFolderDao.update(entity)
            syncScheduler.requestExpeditedSync()
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            syncFolderDao.getById(id)?.let { syncFolderDao.delete(it) }
            _state.update { it.copy(deleted = true) }
        }
    }
}

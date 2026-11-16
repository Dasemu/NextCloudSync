package es.zenvairo.nextcloudsync.ui.conflicts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.zenvairo.nextcloudsync.data.db.dao.ConflictDao
import es.zenvairo.nextcloudsync.data.db.dao.SyncFolderDao
import es.zenvairo.nextcloudsync.data.db.entity.ConflictEntity
import es.zenvairo.nextcloudsync.domain.model.ConflictPolicy
import es.zenvairo.nextcloudsync.domain.sync.SyncEngineFactory
import es.zenvairo.nextcloudsync.domain.sync.PathFilter
import es.zenvairo.nextcloudsync.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConflictChoice { LOCAL, REMOTE, BOTH }

@HiltViewModel
class ConflictsViewModel @Inject constructor(
    private val conflictDao: ConflictDao,
    private val syncFolderDao: SyncFolderDao,
    private val syncEngineFactory: SyncEngineFactory,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val conflicts: StateFlow<List<ConflictEntity>> = conflictDao.observeUnresolved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun resolve(conflict: ConflictEntity, choice: ConflictChoice) {
        viewModelScope.launch {
            val folder = syncFolderDao.getById(conflict.folderId) ?: return@launch
            val engine = syncEngineFactory.create() ?: return@launch
            val policy = when (choice) {
                ConflictChoice.LOCAL -> ConflictPolicy.PREFER_LOCAL
                ConflictChoice.REMOTE -> ConflictPolicy.PREFER_REMOTE
                ConflictChoice.BOTH -> ConflictPolicy.KEEP_BOTH
            }
            val constraints = folder.constraintsJson?.let { runCatching { settingsRepository.decode(it) }.getOrNull() }
                ?: settingsRepository.defaultConstraints.first()
            conflictDao.markResolved(conflict.id)
            engine.syncFolder(folder.copy(conflictPolicy = policy), PathFilter(constraints))
        }
    }
}

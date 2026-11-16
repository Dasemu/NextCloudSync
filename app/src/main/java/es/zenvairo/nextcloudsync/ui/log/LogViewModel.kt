package es.zenvairo.nextcloudsync.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.zenvairo.nextcloudsync.data.db.dao.SyncEventDao
import es.zenvairo.nextcloudsync.data.db.entity.SyncEventEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(syncEventDao: SyncEventDao) : ViewModel() {
    val events: StateFlow<List<SyncEventEntity>> = syncEventDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

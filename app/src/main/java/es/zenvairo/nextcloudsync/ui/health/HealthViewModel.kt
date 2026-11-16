package es.zenvairo.nextcloudsync.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.zenvairo.nextcloudsync.data.db.dao.SyncEventDao
import es.zenvairo.nextcloudsync.data.db.dao.SyncFolderDao
import es.zenvairo.nextcloudsync.work.BatteryHealthChecker
import es.zenvairo.nextcloudsync.work.BatteryHealthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthUiState(
    val battery: BatteryHealthStatus? = null,
    val lastSuccessfulSyncMillis: Long? = null,
    val missedRunsLast7Days: Int = 0
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val batteryHealthChecker: BatteryHealthChecker,
    private val syncFolderDao: SyncFolderDao,
    private val syncEventDao: SyncEventDao
) : ViewModel() {

    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(battery = batteryHealthChecker.currentStatus())
        viewModelScope.launch {
            val folders = syncFolderDao.getEnabled()
            val lastSuccess = folders.mapNotNull { it.lastSyncAtMillis }.maxOrNull()
            val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            val started = syncEventDao.countRunsSince(sevenDaysAgo)
            val finished = syncEventDao.countCompletedRunsSince(sevenDaysAgo)
            _state.value = _state.value.copy(
                lastSuccessfulSyncMillis = lastSuccess,
                missedRunsLast7Days = (started - finished).coerceAtLeast(0)
            )
        }
    }
}

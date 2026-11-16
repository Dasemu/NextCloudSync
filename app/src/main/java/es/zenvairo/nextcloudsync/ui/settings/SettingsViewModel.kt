package es.zenvairo.nextcloudsync.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.zenvairo.nextcloudsync.data.auth.AccountRepository
import es.zenvairo.nextcloudsync.data.prefs.SettingsRepository
import es.zenvairo.nextcloudsync.domain.model.SyncConstraints
import es.zenvairo.nextcloudsync.work.SyncScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    val account = accountRepository.account
    val defaultConstraints: StateFlow<SyncConstraints> = settingsRepository.defaultConstraints
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncConstraints())

    fun updateDefaults(constraints: SyncConstraints) {
        viewModelScope.launch {
            settingsRepository.updateDefaultConstraints(constraints)
            syncScheduler.reschedulePeriodic(constraints)
        }
    }

    fun signOut() {
        syncScheduler.cancelAll()
        accountRepository.signOut()
    }
}

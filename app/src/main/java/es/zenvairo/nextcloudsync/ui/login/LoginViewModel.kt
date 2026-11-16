package es.zenvairo.nextcloudsync.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.zenvairo.nextcloudsync.data.auth.AccountRepository
import es.zenvairo.nextcloudsync.data.auth.LoginFlowV2Client
import es.zenvairo.nextcloudsync.data.auth.LoginPollResult
import es.zenvairo.nextcloudsync.work.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    object Idle : LoginUiState()
    object AwaitingBrowser : LoginUiState()
    data class Error(val message: String) : LoginUiState()
    object Success : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginFlowV2Client: LoginFlowV2Client,
    private val accountRepository: AccountRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    private val _browserUrl = MutableStateFlow<String?>(null)
    val browserUrl: StateFlow<String?> = _browserUrl

    fun startLogin(serverUrl: String) {
        if (serverUrl.isBlank()) {
            _uiState.update { LoginUiState.Error("invalid_url") }
            return
        }
        viewModelScope.launch {
            _uiState.update { LoginUiState.AwaitingBrowser }
            try {
                val init = loginFlowV2Client.initiate(serverUrl)
                _browserUrl.update { init.login }
                when (val result = loginFlowV2Client.pollUntilComplete(init.poll)) {
                    is LoginPollResult.Success -> {
                        accountRepository.save(result.credentials)
                        syncScheduler.reschedulePeriodic(es.zenvairo.nextcloudsync.domain.model.SyncConstraints())
                        _uiState.update { LoginUiState.Success }
                    }
                    is LoginPollResult.TimedOut -> _uiState.update { LoginUiState.Error("timeout") }
                    is LoginPollResult.Error -> _uiState.update { LoginUiState.Error("network") }
                    is LoginPollResult.Pending -> _uiState.update { LoginUiState.Error("timeout") }
                }
            } catch (e: Exception) {
                _uiState.update { LoginUiState.Error("network") }
            } finally {
                _browserUrl.update { null }
            }
        }
    }

    fun dismissError() {
        _uiState.update { LoginUiState.Idle }
    }
}

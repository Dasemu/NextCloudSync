package es.zenvairo.nextcloudsync.ui.foldereditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.zenvairo.nextcloudsync.data.auth.AccountRepository
import es.zenvairo.nextcloudsync.data.remote.WebDavClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

data class RemoteFolderPickerState(
    val currentPath: String = "/",
    val folderNames: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val notConfigured: Boolean = false
)

/** Lets the user browse the real folder tree of the connected Nextcloud account via WebDAV PROPFIND. */
@HiltViewModel
class RemoteFolderPickerViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RemoteFolderPickerState())
    val state: StateFlow<RemoteFolderPickerState> = _state

    private val webDavClient: WebDavClient? by lazy {
        val account = accountRepository.account.value ?: return@lazy null
        WebDavClient(okHttpClient, account.serverUrl, account.username, account.appPassword)
    }

    fun loadInitial(startPath: String) {
        val client = webDavClient
        if (client == null) {
            _state.update { it.copy(notConfigured = true, error = null, loading = false) }
            return
        }
        load(normalize(startPath))
    }

    fun navigateInto(folderName: String) {
        val base = _state.value.currentPath
        val next = if (base == "/") "/$folderName" else "$base/$folderName"
        load(next)
    }

    fun navigateUp() {
        val current = _state.value.currentPath
        if (current == "/") return
        val parent = current.substringBeforeLast('/').ifEmpty { "/" }
        load(parent)
    }

    fun retry() = load(_state.value.currentPath)

    private fun load(path: String) {
        val client = webDavClient ?: run {
            _state.update { it.copy(notConfigured = true) }
            return
        }
        val normalized = normalize(path)
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, currentPath = normalized) }
            try {
                val entries = client.propfind(normalized, depth = 1)
                val subfolders = entries
                    .filter { it.isDirectory && normalize(it.path) != normalized }
                    .map { it.path.trimEnd('/').substringAfterLast('/') }
                    .filter { it.isNotBlank() }
                    .sortedBy { it.lowercase() }
                _state.update { it.copy(loading = false, folderNames = subfolders, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e::class.simpleName) }
            }
        }
    }

    private fun normalize(path: String): String {
        val withLeading = if (path.startsWith("/")) path else "/$path"
        return if (withLeading.length > 1) withLeading.trimEnd('/') else withLeading
    }
}

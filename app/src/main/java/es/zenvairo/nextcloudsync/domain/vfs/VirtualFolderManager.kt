package es.zenvairo.nextcloudsync.domain.vfs

import es.zenvairo.nextcloudsync.data.auth.AccountRepository
import es.zenvairo.nextcloudsync.data.db.dao.VirtualFolderDao
import es.zenvairo.nextcloudsync.data.db.entity.VirtualFolderEntity
import es.zenvairo.nextcloudsync.root.MountResult
import es.zenvairo.nextcloudsync.root.RootMountController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class MountState {
    object Unmounted : MountState()
    object Mounting : MountState()
    data class Mounted(val mountpoint: String) : MountState()
    data class Failed(val reason: String) : MountState()
}

/**
 * Owns the mount lifecycle of every virtual folder and the observable state the UI renders.
 *
 * Mounts are never established automatically: they need root at the moment they happen, and on
 * KernelSU a denied or not-yet-granted request just fails rather than prompting. So the user
 * mounts explicitly, and after a reboot they mount again.
 */
@Singleton
class VirtualFolderManager @Inject constructor(
    private val controller: RootMountController,
    private val virtualFolderDao: VirtualFolderDao,
    private val accountRepository: AccountRepository
) {
    private val _states = MutableStateFlow<Map<Long, MountState>>(emptyMap())
    val states: StateFlow<Map<Long, MountState>> = _states.asStateFlow()

    fun mountPathFor(folder: VirtualFolderEntity): String = controller.mountPathFor(folder)

    suspend fun isMounted(folder: VirtualFolderEntity): Boolean = controller.isMounted(folder)

    suspend fun mount(folder: VirtualFolderEntity) {
        val account = accountRepository.account.value ?: run {
            setState(folder.id, MountState.Failed("no account signed in"))
            return
        }
        setState(folder.id, MountState.Mounting)
        when (val result = controller.mount(folder, account)) {
            is MountResult.Success -> {
                setState(folder.id, MountState.Mounted(result.mountpoint))
                virtualFolderDao.updateError(folder.id, null)
            }
            is MountResult.Failure -> {
                setState(folder.id, MountState.Failed(result.reason))
                virtualFolderDao.updateError(folder.id, result.reason)
            }
        }
    }

    suspend fun unmount(folder: VirtualFolderEntity) {
        when (val result = controller.unmount(folder)) {
            is MountResult.Success -> setState(folder.id, MountState.Unmounted)
            is MountResult.Failure -> setState(folder.id, MountState.Failed(result.reason))
        }
    }

    /**
     * Reconciles in-memory state with what is actually mounted. The root processes outlive the app's
     * own process, so after the UI is recreated a folder can already be mounted with no state here.
     */
    suspend fun refreshStates(folders: List<VirtualFolderEntity>) {
        val refreshed = folders.associate { folder ->
            val current = _states.value[folder.id]
            // Don't clobber a mount in flight, or a failure the user hasn't seen yet.
            if (current is MountState.Mounting || current is MountState.Failed) {
                folder.id to current
            } else if (controller.isMounted(folder)) {
                folder.id to MountState.Mounted(controller.mountPathFor(folder))
            } else {
                folder.id to MountState.Unmounted
            }
        }
        _states.value = refreshed
    }

    /** Unmounts and drops the folder's hydration cache; the entity row is deleted by the caller. */
    suspend fun forget(folder: VirtualFolderEntity) {
        controller.unmount(folder)
        controller.cacheDirFor(folder).deleteRecursively()
        _states.value = _states.value - folder.id
    }

    private fun setState(id: Long, state: MountState) {
        _states.value = _states.value + (id to state)
    }
}

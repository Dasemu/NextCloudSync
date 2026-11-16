package es.zenvairo.nextcloudsync.ui.virtualfolders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.data.db.entity.VirtualFolderEntity
import es.zenvairo.nextcloudsync.domain.vfs.MountState
import es.zenvairo.nextcloudsync.ui.theme.Spacing
import es.zenvairo.nextcloudsync.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualFoldersScreen(
    onBack: () -> Unit,
    onAddFolder: () -> Unit,
    onEditFolder: (Long) -> Unit,
    viewModel: VirtualFoldersViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsState()
    val mountStates by viewModel.mountStates.collectAsState()
    val hasRoot by viewModel.hasRoot.collectAsState()

    // Root can be granted from outside the app, and the root-side mounts outlive this process, so
    // both are re-checked whenever this screen comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.virtual_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFolder) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.virtual_add))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(R.string.virtual_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)
            )

            if (hasRoot == false) {
                NoRootCard(onRecheck = { viewModel.recheckRoot() })
            }

            if (folders.isEmpty()) {
                EmptyVirtualFoldersState()
            } else {
                Text(
                    stringResource(R.string.virtual_remount_after_reboot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs)
                )
                LazyColumn(contentPadding = PaddingValues(vertical = Spacing.s)) {
                    items(folders, key = { it.id }) { folder ->
                        VirtualFolderRow(
                            folder = folder,
                            state = mountStates[folder.id] ?: MountState.Unmounted,
                            rootAvailable = hasRoot != false,
                            onClick = { onEditFolder(folder.id) },
                            onMount = { viewModel.mount(folder) },
                            onUnmount = { viewModel.unmount(folder) },
                            onToggleEnabled = { viewModel.setEnabled(folder, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyVirtualFoldersState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.xxl)) {
            Icon(
                Icons.Filled.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Text(
                stringResource(R.string.virtual_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.l)
            )
        }
    }
}

@Composable
private fun NoRootCard(onRecheck: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(Spacing.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(modifier = Modifier.padding(Spacing.l)) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = Spacing.m)
            )
            Column {
                Text(
                    stringResource(R.string.virtual_no_root_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    stringResource(R.string.virtual_no_root_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
                TextButton(onClick = onRecheck, modifier = Modifier.padding(top = Spacing.xs)) {
                    Text(stringResource(R.string.virtual_recheck_root))
                }
            }
        }
    }
}

@Composable
private fun VirtualFolderRow(
    folder: VirtualFolderEntity,
    state: MountState,
    rootAvailable: Boolean,
    onClick: () -> Unit,
    onMount: () -> Unit,
    onUnmount: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    // A failure recorded on a previous run (a boot remount, most likely) is worth showing when
    // nothing has happened since to replace it.
    val staleError = folder.lastErrorMessage.takeIf { state == MountState.Unmounted }
    val isError = state is MountState.Failed || staleError != null
    val isMounted = state is MountState.Mounted
    val isMounting = state == MountState.Mounting

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.l)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(folder.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        folder.remotePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(checked = folder.enabled, onCheckedChange = onToggleEnabled)
            }

            val statusColor = when {
                isError -> MaterialTheme.colorScheme.error
                isMounted -> MaterialTheme.extendedColors.success
                isMounting -> MaterialTheme.extendedColors.syncing
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.s)) {
                when {
                    isMounting -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    isError -> Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                    isMounted -> Icon(Icons.Filled.CloudDone, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                    else -> Icon(Icons.Filled.CloudOff, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                }
                Text(
                    when {
                        state is MountState.Mounted -> stringResource(R.string.virtual_status_mounted, state.mountpoint)
                        isMounting -> stringResource(R.string.virtual_status_mounting)
                        state is MountState.Failed -> stringResource(R.string.virtual_status_failed, state.reason)
                        staleError != null -> stringResource(R.string.virtual_status_failed, staleError)
                        else -> stringResource(R.string.virtual_status_unmounted)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.padding(top = Spacing.s)) {
                TextButton(
                    onClick = onMount,
                    enabled = rootAvailable && !isMounted && !isMounting
                ) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.virtual_mount), modifier = Modifier.padding(start = Spacing.xs))
                }
                TextButton(onClick = onUnmount, enabled = isMounted) {
                    Text(stringResource(R.string.virtual_unmount))
                }
            }
        }
    }
}

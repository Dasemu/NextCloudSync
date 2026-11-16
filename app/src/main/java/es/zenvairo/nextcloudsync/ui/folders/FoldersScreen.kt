package es.zenvairo.nextcloudsync.ui.folders

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.data.db.entity.SyncFolderEntity
import es.zenvairo.nextcloudsync.ui.theme.Spacing
import es.zenvairo.nextcloudsync.ui.theme.extendedColors
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onAddFolder: () -> Unit,
    onEditFolder: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenConflicts: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenVirtualFolders: () -> Unit,
    viewModel: FoldersViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsState()
    var hasStoragePermission by remember { mutableStateOf(hasAllFilesAccess()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasStoragePermission = hasAllFilesAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.folders_title)) },
                actions = {
                    IconButton(onClick = onOpenVirtualFolders) { Icon(Icons.Filled.Cloud, contentDescription = stringResource(R.string.virtual_title)) }
                    IconButton(onClick = onOpenConflicts) { Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.conflicts_title)) }
                    IconButton(onClick = onOpenHealth) { Icon(Icons.Filled.HealthAndSafety, contentDescription = stringResource(R.string.health_title)) }
                    IconButton(onClick = onOpenLog) { Icon(Icons.Filled.History, contentDescription = stringResource(R.string.log_title)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFolder) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.folders_add)) }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!hasStoragePermission) {
                StoragePermissionCard(onGrant = {
                    context.startActivity(allFilesAccessIntent(context.packageName))
                })
            }
            if (folders.isEmpty()) {
                EmptyFoldersState()
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.s)
                ) {
                    items(folders, key = { it.entity.id }) { model ->
                        FolderRow(
                            model = model,
                            onClick = { onEditFolder(model.entity.id) },
                            onToggle = { enabled -> viewModel.setEnabled(model.entity, enabled) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFoldersState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xxl)
        ) {
            Icon(
                Icons.Filled.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Text(
                stringResource(R.string.folders_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.l)
            )
        }
    }
}

@Composable
private fun StoragePermissionCard(onGrant: () -> Unit) {
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
                    stringResource(R.string.permission_storage_rationale),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onGrant, modifier = Modifier.padding(top = Spacing.s)) {
                    Text(stringResource(R.string.action_grant_permission))
                }
            }
        }
    }
}

@Composable
private fun FolderRow(model: FolderUiModel, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val folder = model.entity
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.l)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(folder.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        folder.localPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(checked = folder.enabled, onCheckedChange = onToggle)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s))

            val (icon, color) = when {
                model.unresolvedConflicts > 0 -> Icons.Filled.SyncProblem to MaterialTheme.colorScheme.error
                !folder.lastSyncSucceeded -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
                !folder.enabled -> Icons.Filled.PauseCircle to MaterialTheme.colorScheme.onSurfaceVariant
                else -> Icons.Filled.CheckCircle to MaterialTheme.extendedColors.success
            }
            val statusText = when {
                model.unresolvedConflicts > 0 -> stringResource(R.string.folders_status_conflict, model.unresolvedConflicts)
                !folder.lastSyncSucceeded -> stringResource(R.string.folders_status_error)
                !folder.enabled -> stringResource(R.string.folders_status_paused)
                else -> stringResource(R.string.folders_status_ok)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
            val lastSync = folder.lastSyncAtMillis
            Text(
                if (lastSync != null) stringResource(R.string.folders_last_sync, DateFormat.getDateTimeInstance().format(Date(lastSync)))
                else stringResource(R.string.folders_never_synced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
    }
}

private fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

private fun allFilesAccessIntent(packageName: String): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    }

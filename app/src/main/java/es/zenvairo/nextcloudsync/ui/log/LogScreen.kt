package es.zenvairo.nextcloudsync.ui.log

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.data.db.entity.SyncEventEntity
import es.zenvairo.nextcloudsync.data.db.entity.SyncEventType
import es.zenvairo.nextcloudsync.ui.theme.Spacing
import es.zenvairo.nextcloudsync.ui.theme.extendedColors
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit, viewModel: LogViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel)) } }
            )
        }
    ) { padding ->
        if (events.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.xxl)) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        stringResource(R.string.log_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.l)
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(events, key = { it.id }) { event -> LogRow(event) }
            }
        }
    }
}

@Composable
private fun LogRow(event: SyncEventEntity) {
    val label = when (event.type) {
        SyncEventType.UPLOADED -> stringResource(R.string.log_uploaded, event.relativePath ?: "")
        SyncEventType.DOWNLOADED -> stringResource(R.string.log_downloaded, event.relativePath ?: "")
        SyncEventType.DELETED_LOCAL -> stringResource(R.string.log_deleted_local, event.relativePath ?: "")
        SyncEventType.DELETED_REMOTE -> stringResource(R.string.log_deleted_remote, event.relativePath ?: "")
        SyncEventType.CONFLICT -> stringResource(R.string.log_conflict, event.relativePath ?: "")
        SyncEventType.ERROR -> stringResource(R.string.log_error, event.message ?: event.relativePath ?: "")
        SyncEventType.RUN_STARTED, SyncEventType.RUN_FINISHED -> null
    }
    if (label != null) {
        val (icon, tint) = logIconAndColor(event.type)
        ListItem(
            leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
            headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
            supportingContent = {
                Text(
                    DateFormat.getTimeInstance().format(Date(event.timestampMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
private fun logIconAndColor(type: SyncEventType): Pair<ImageVector, Color> = when (type) {
    SyncEventType.UPLOADED -> Icons.Filled.CloudUpload to MaterialTheme.extendedColors.success
    SyncEventType.DOWNLOADED -> Icons.Filled.CloudDownload to MaterialTheme.extendedColors.syncing
    SyncEventType.DELETED_LOCAL, SyncEventType.DELETED_REMOTE -> Icons.Filled.DeleteOutline to MaterialTheme.colorScheme.onSurfaceVariant
    SyncEventType.CONFLICT -> Icons.Filled.SyncProblem to MaterialTheme.extendedColors.warning
    SyncEventType.ERROR -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
    SyncEventType.RUN_STARTED, SyncEventType.RUN_FINISHED -> Icons.Filled.History to MaterialTheme.colorScheme.onSurfaceVariant
}

package es.zenvairo.nextcloudsync.ui.conflicts

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.ui.theme.Spacing
import es.zenvairo.nextcloudsync.ui.theme.extendedColors
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictsScreen(onBack: () -> Unit, viewModel: ConflictsViewModel = hiltViewModel()) {
    val conflicts by viewModel.conflicts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conflicts_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel)) } }
            )
        }
    ) { padding ->
        if (conflicts.isEmpty()) {
            EmptyConflictsState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = Spacing.s)
            ) {
                items(conflicts, key = { it.id }) { conflict ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(Spacing.l)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.SyncProblem,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    conflict.relativePath,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(start = Spacing.s)
                                )
                            }
                            Text(
                                DateFormat.getDateTimeInstance().format(Date(conflict.detectedAtMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(start = Spacing.xl, top = Spacing.xs)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.resolve(conflict, ConflictChoice.LOCAL) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.conflicts_keep_local), style = MaterialTheme.typography.labelMedium) }
                                OutlinedButton(
                                    onClick = { viewModel.resolve(conflict, ConflictChoice.REMOTE) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.conflicts_keep_remote), style = MaterialTheme.typography.labelMedium) }
                                OutlinedButton(
                                    onClick = { viewModel.resolve(conflict, ConflictChoice.BOTH) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.conflicts_keep_both), style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConflictsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.xxl)) {
            Icon(
                Icons.Filled.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.success,
                modifier = Modifier.size(56.dp)
            )
            Text(
                stringResource(R.string.conflicts_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.l)
            )
        }
    }
}

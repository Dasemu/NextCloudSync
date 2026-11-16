package es.zenvairo.nextcloudsync.ui.foldereditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import es.zenvairo.nextcloudsync.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteFolderPickerDialog(
    startPath: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    viewModel: RemoteFolderPickerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadInitial(startPath) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    stringResource(R.string.remote_picker_title),
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    state.currentPath,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                HorizontalDivider()

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        state.notConfigured -> {
                            CenteredMessage(stringResource(R.string.remote_picker_not_configured))
                        }
                        state.loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        state.error != null -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    stringResource(R.string.remote_picker_error, state.error ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                                )
                                TextButton(onClick = { viewModel.retry() }, modifier = Modifier.padding(top = 8.dp)) {
                                    Text(stringResource(R.string.remote_picker_retry))
                                }
                            }
                        }
                        state.folderNames.isEmpty() -> {
                            CenteredMessage(stringResource(R.string.remote_picker_empty))
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.folderNames) { name ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { viewModel.navigateInto(name) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.Folder, contentDescription = null)
                                            Text(name, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { viewModel.navigateUp() },
                        enabled = !state.notConfigured && state.currentPath != "/"
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null)
                        Text(stringResource(R.string.remote_picker_up), modifier = Modifier.padding(start = 4.dp))
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        TextButton(
                            onClick = { onSelect(state.currentPath) },
                            enabled = !state.notConfigured
                        ) {
                            Text(stringResource(R.string.remote_picker_select_here))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

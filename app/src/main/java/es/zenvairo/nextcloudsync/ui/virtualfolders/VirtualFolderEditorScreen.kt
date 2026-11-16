package es.zenvairo.nextcloudsync.ui.virtualfolders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.ui.theme.Spacing

private const val BYTES_PER_MB = 1024L * 1024L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualFolderEditorScreen(
    folderId: Long?,
    onDone: () -> Unit,
    viewModel: VirtualFolderEditorViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    // Empty rather than "/" so the placeholder shows and typing a path doesn't produce "//Foo".
    var remotePath by remember { mutableStateOf("") }
    var cacheMb by remember { mutableStateOf(512f) }
    var loaded by remember { mutableStateOf(folderId == null) }

    LaunchedEffect(folderId) {
        val existing = folderId?.let { viewModel.load(it) }
        if (existing != null) {
            name = existing.displayName
            remotePath = existing.remotePath
            cacheMb = (existing.cacheMaxBytes / BYTES_PER_MB).toFloat()
        }
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (folderId == null) R.string.virtual_editor_new else R.string.virtual_editor_edit
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel)) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(Spacing.l)) {
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(Spacing.l)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.virtual_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = remotePath,
                        onValueChange = { remotePath = it },
                        label = { Text(stringResource(R.string.virtual_remote_path_label)) },
                        placeholder = { Text(stringResource(R.string.virtual_remote_path_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.m)
                    )

                    Text(
                        stringResource(R.string.virtual_cache_label, cacheMb.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Spacing.l)
                    )
                    Slider(value = cacheMb, onValueChange = { cacheMb = it }, valueRange = 64f..4096f)
                }
            }

            Button(
                onClick = {
                    viewModel.save(
                        id = folderId,
                        displayName = name.trim(),
                        remotePath = remotePath.trim().ifBlank { "/" },
                        cacheMaxBytes = cacheMb.toLong() * BYTES_PER_MB
                    )
                    onDone()
                },
                enabled = loaded && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)
            ) { Text(stringResource(R.string.virtual_save)) }

            if (folderId != null) {
                OutlinedButton(
                    onClick = { viewModel.delete(folderId); onDone() },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s)
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                    Text(stringResource(R.string.virtual_delete))
                }
            }
        }
    }
}

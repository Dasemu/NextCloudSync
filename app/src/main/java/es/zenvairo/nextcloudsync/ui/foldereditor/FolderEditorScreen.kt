package es.zenvairo.nextcloudsync.ui.foldereditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.domain.model.ConflictPolicy
import es.zenvairo.nextcloudsync.domain.model.NetworkTrigger
import es.zenvairo.nextcloudsync.domain.model.SyncMode
import es.zenvairo.nextcloudsync.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderEditorScreen(folderId: Long?, onDone: () -> Unit, viewModel: FolderEditorViewModel = hiltViewModel()) {
    LaunchedEffect(folderId) { viewModel.load(folderId) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    val pickDirectory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            DocumentTreeUriMapper.toAbsolutePath(uri)?.let { path ->
                viewModel.update { it.copy(localPath = path) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (folderId == null) R.string.folder_editor_title_new else R.string.folder_editor_title_edit)) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel)) } }
            )
        }
    ) { padding ->
        var showRemotePicker by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)
        ) {
            EditorSection {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = { name -> viewModel.update { it.copy(displayName = name) } },
                    label = { Text(stringResource(R.string.folder_editor_display_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.localPath,
                        onValueChange = { path -> viewModel.update { it.copy(localPath = path) } },
                        label = { Text(stringResource(R.string.folder_editor_local_path)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { pickDirectory.launch(null) }, modifier = Modifier.padding(start = Spacing.xs)) {
                        Text(stringResource(R.string.folder_editor_choose))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.remotePath,
                        onValueChange = { path -> viewModel.update { it.copy(remotePath = if (path.startsWith("/")) path else "/$path") } },
                        label = { Text(stringResource(R.string.folder_editor_remote_path)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showRemotePicker = true }, modifier = Modifier.padding(start = Spacing.xs)) {
                        Text(stringResource(R.string.folder_editor_choose))
                    }
                }
            }
            if (showRemotePicker) {
                RemoteFolderPickerDialog(
                    startPath = state.remotePath,
                    onDismiss = { showRemotePicker = false },
                    onSelect = { path ->
                        viewModel.update { it.copy(remotePath = path) }
                        showRemotePicker = false
                    }
                )
            }

            EditorSection {
                SectionLabel(stringResource(R.string.folder_editor_sync_mode), topPadding = 0.dp)
                SyncModeRadios(state.mode) { mode -> viewModel.update { it.copy(mode = mode) } }

                SectionLabel(stringResource(R.string.folder_editor_conflict_policy))
                ConflictPolicyDropdown(state.conflictPolicy) { policy -> viewModel.update { it.copy(conflictPolicy = policy) } }
            }

            EditorSection {
                SectionLabel(stringResource(R.string.folder_editor_section_triggers), topPadding = 0.dp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (state.useGlobalConstraints) stringResource(R.string.folder_editor_use_global_settings) else stringResource(R.string.folder_editor_override_settings),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = !state.useGlobalConstraints,
                        onCheckedChange = { override -> viewModel.update { it.copy(useGlobalConstraints = !override) } }
                    )
                }

                if (!state.useGlobalConstraints) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.m))
                    FolderConstraintsEditor(
                        constraints = state.constraints,
                        onChange = { constraints -> viewModel.update { it.copy(constraints = constraints) } }
                    )
                }
            }

            Button(onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.folder_editor_save))
            }
            if (folderId != null) {
                var confirmingDelete by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { confirmingDelete = true },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                    Text(stringResource(R.string.folder_editor_delete))
                }
                if (confirmingDelete) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s)
                    ) {
                        Column(modifier = Modifier.padding(Spacing.l)) {
                            Text(
                                stringResource(R.string.folder_editor_delete_confirm),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.s), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.action_cancel)) }
                                TextButton(onClick = { viewModel.delete() }) { Text(stringResource(R.string.folder_editor_delete), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSection(content: @Composable ColumnScope.() -> Unit) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.l), content = content)
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = Spacing.l) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = topPadding, bottom = Spacing.xs)
    )
}

@Composable
private fun SyncModeRadios(selected: SyncMode, onSelect: (SyncMode) -> Unit) {
    val options = listOf(
        SyncMode.BIDIRECTIONAL to R.string.folder_editor_mode_bidirectional,
        SyncMode.UPLOAD_ONLY to R.string.folder_editor_mode_upload_only,
        SyncMode.DOWNLOAD_ONLY to R.string.folder_editor_mode_download_only,
        SyncMode.MIRROR_UP to R.string.folder_editor_mode_mirror_up,
        SyncMode.MIRROR_DOWN to R.string.folder_editor_mode_mirror_down
    )
    Column {
        options.forEach { (mode, labelRes) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.xs))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConflictPolicyDropdown(selected: ConflictPolicy, onSelect: (ConflictPolicy) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        ConflictPolicy.KEEP_BOTH to R.string.folder_editor_conflict_keep_both,
        ConflictPolicy.PREFER_LOCAL to R.string.folder_editor_conflict_prefer_local,
        ConflictPolicy.PREFER_REMOTE to R.string.folder_editor_conflict_prefer_remote,
        ConflictPolicy.ASK to R.string.folder_editor_conflict_ask
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(options.first { it.first == selected }.second),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (policy, labelRes) ->
                DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = { onSelect(policy); expanded = false })
            }
        }
    }
}

@Composable
private fun FolderConstraintsEditor(constraints: es.zenvairo.nextcloudsync.domain.model.SyncConstraints, onChange: (es.zenvairo.nextcloudsync.domain.model.SyncConstraints) -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.triggers_network_title), topPadding = 0.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(
                NetworkTrigger.ANY to R.string.triggers_network_any,
                NetworkTrigger.UNMETERED to R.string.triggers_network_unmetered,
                NetworkTrigger.WIFI_ONLY to R.string.triggers_network_wifi_only
            ).forEach { (trigger, labelRes) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = constraints.networkTrigger == trigger, onClick = { onChange(constraints.copy(networkTrigger = trigger)) })
                    Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = Spacing.s))
                }
            }
        }

        SectionLabel(stringResource(R.string.triggers_battery_title))
        Text(stringResource(R.string.triggers_battery_min_level) + ": ${constraints.minBatteryPercent}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = constraints.minBatteryPercent.toFloat(),
            onValueChange = { onChange(constraints.copy(minBatteryPercent = it.toInt())) },
            valueRange = 0f..100f
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = constraints.chargingOnly, onCheckedChange = { onChange(constraints.copy(chargingOnly = it)) })
            Text(stringResource(R.string.triggers_battery_charging_only), style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = constraints.respectBatterySaver, onCheckedChange = { onChange(constraints.copy(respectBatterySaver = it)) })
            Text(stringResource(R.string.triggers_battery_respect_saver), style = MaterialTheme.typography.bodyMedium)
        }

        SectionLabel(stringResource(R.string.triggers_schedule_title))
        Text(stringResource(R.string.triggers_schedule_interval) + ": ${constraints.intervalMinutes} min", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = constraints.intervalMinutes.toFloat(),
            onValueChange = { onChange(constraints.copy(intervalMinutes = it.toLong())) },
            valueRange = 15f..1440f
        )

        SectionLabel(stringResource(R.string.triggers_filters_title))
        OutlinedTextField(
            value = constraints.excludeGlobs.joinToString(", "),
            onValueChange = { onChange(constraints.copy(excludeGlobs = it.split(",").map(String::trim).filter(String::isNotEmpty))) },
            label = { Text(stringResource(R.string.triggers_filters_exclude_patterns)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
            Checkbox(checked = constraints.skipHidden, onCheckedChange = { onChange(constraints.copy(skipHidden = it)) })
            Text(stringResource(R.string.triggers_filters_skip_hidden), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

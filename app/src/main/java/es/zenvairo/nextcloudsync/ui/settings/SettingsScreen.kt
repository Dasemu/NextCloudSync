package es.zenvairo.nextcloudsync.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSignedOut: () -> Unit, onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsState()
    val defaults by viewModel.defaultConstraints.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel)) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)
        ) {
            SettingsSection(icon = Icons.Filled.AccountCircle, title = stringResource(R.string.settings_section_account)) {
                Text(
                    account?.let { stringResource(R.string.login_signed_in_as, it.username) } ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = { viewModel.signOut(); onSignedOut() },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(top = Spacing.m)
                ) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            }

            SettingsSection(icon = Icons.Filled.Tune, title = stringResource(R.string.settings_section_defaults)) {
                Text(
                    stringResource(R.string.triggers_battery_min_level) + ": ${defaults.minBatteryPercent}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = defaults.minBatteryPercent.toFloat(),
                    onValueChange = { viewModel.updateDefaults(defaults.copy(minBatteryPercent = it.toInt())) },
                    valueRange = 0f..100f
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = defaults.chargingOnly,
                        onCheckedChange = { viewModel.updateDefaults(defaults.copy(chargingOnly = it)) }
                    )
                    Text(stringResource(R.string.triggers_battery_charging_only), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = defaults.respectBatterySaver,
                        onCheckedChange = { viewModel.updateDefaults(defaults.copy(respectBatterySaver = it)) }
                    )
                    Text(stringResource(R.string.triggers_battery_respect_saver), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.triggers_schedule_interval) + ": ${defaults.intervalMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s)
                )
                Slider(
                    value = defaults.intervalMinutes.toFloat(),
                    onValueChange = { viewModel.updateDefaults(defaults.copy(intervalMinutes = it.toLong())) },
                    valueRange = 15f..1440f
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.m)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.s)
                )
            }
            content()
        }
    }
}

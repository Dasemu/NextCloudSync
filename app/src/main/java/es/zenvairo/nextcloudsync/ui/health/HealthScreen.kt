package es.zenvairo.nextcloudsync.ui.health

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.ui.theme.Spacing
import es.zenvairo.nextcloudsync.ui.theme.extendedColors
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(onBack: () -> Unit, viewModel: HealthViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val battery = state.battery

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.health_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel)) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(Spacing.l)) {
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(Spacing.l)) {
                    HealthStatRow(
                        icon = Icons.Filled.Schedule,
                        label = state.lastSuccessfulSyncMillis?.let {
                            stringResource(R.string.health_last_successful_sync, DateFormat.getDateTimeInstance().format(Date(it)))
                        } ?: stringResource(R.string.folders_never_synced)
                    )
                    HealthStatRow(
                        icon = Icons.Filled.HourglassBottom,
                        label = stringResource(R.string.health_missed_runs, state.missedRunsLast7Days),
                        topPadding = Spacing.s
                    )
                }
            }

            if (battery != null && !battery.isIgnoringBatteryOptimizations) {
                WarningCard(
                    icon = Icons.Filled.BatteryAlert,
                    title = stringResource(R.string.health_battery_optimization_title),
                    body = stringResource(R.string.health_battery_optimization_body),
                    actionLabel = stringResource(R.string.health_battery_optimization_action),
                    onAction = {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            if (battery != null && (battery.standbyBucket == es.zenvairo.nextcloudsync.work.StandbyBucket.RARE ||
                    battery.standbyBucket == es.zenvairo.nextcloudsync.work.StandbyBucket.RESTRICTED)
            ) {
                WarningCard(
                    icon = Icons.Filled.WarningAmber,
                    title = stringResource(R.string.health_standby_bucket_title),
                    body = stringResource(R.string.health_standby_bucket_body, battery.standbyBucket.label),
                    actionLabel = null,
                    onAction = {}
                )
            }

            battery?.manufacturerTip?.let { tip ->
                WarningCard(
                    icon = Icons.Filled.Build,
                    title = stringResource(R.string.health_manufacturer_title, tip.manufacturerLabel),
                    body = stringResource(R.string.health_manufacturer_body),
                    actionLabel = stringResource(R.string.health_manufacturer_action),
                    onAction = { context.startActivity(tip.settingsIntent) }
                )
            }

            if (battery != null && battery.isIgnoringBatteryOptimizations && battery.manufacturerTip == null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.extendedColors.successContainer)
                ) {
                    Row(modifier = Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.extendedColors.onSuccessContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.health_all_good),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.onSuccessContainer,
                            modifier = Modifier.padding(start = Spacing.s)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthStatRow(icon: ImageVector, label: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = topPadding)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.s))
    }
}

@Composable
private fun WarningCard(icon: ImageVector, title: String, body: String, actionLabel: String?, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.extendedColors.warningContainer)
    ) {
        Row(modifier = Modifier.padding(Spacing.l)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.onWarningContainer,
                modifier = Modifier.padding(end = Spacing.m)
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.extendedColors.onWarningContainer
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.onWarningContainer,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
                if (actionLabel != null) {
                    TextButton(onClick = onAction, modifier = Modifier.padding(top = Spacing.xs)) { Text(actionLabel) }
                }
            }
        }
    }
}

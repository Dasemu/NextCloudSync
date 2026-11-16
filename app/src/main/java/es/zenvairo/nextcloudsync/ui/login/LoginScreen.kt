package es.zenvairo.nextcloudsync.ui.login

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import es.zenvairo.nextcloudsync.R
import es.zenvairo.nextcloudsync.ui.theme.Spacing
import androidx.core.net.toUri

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val browserUrl by viewModel.browserUrl.collectAsState()
    var serverUrl by remember { mutableStateOf("") }

    LaunchedEffect(browserUrl) {
        browserUrl?.let { url ->
            CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) onLoggedIn()
    }

    val state = uiState
    val isBusy = state is LoginUiState.AwaitingBrowser

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoginBrandHeader()

        Spacer(modifier = Modifier.height(Spacing.xxl))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(Spacing.l)) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.login_server_url_label)) },
                    placeholder = { Text(stringResource(R.string.login_server_url_hint)) },
                    singleLine = true,
                    enabled = !isBusy,
                    isError = state is LoginUiState.Error,
                    modifier = Modifier.fillMaxWidth()
                )

                when (state) {
                    is LoginUiState.AwaitingBrowser -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Text(
                                stringResource(R.string.login_waiting),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = Spacing.s)
                            )
                        }
                    }
                    is LoginUiState.Error -> {
                        val messageRes = when (state.message) {
                            "invalid_url" -> R.string.login_error_invalid_url
                            "timeout" -> R.string.login_error_timeout
                            else -> R.string.login_error_network
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m)
                        ) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                stringResource(messageRes),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = Spacing.s)
                            )
                        }
                        Button(
                            onClick = { viewModel.startLogin(serverUrl) },
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m)
                        ) {
                            Text(stringResource(R.string.login_connect_button))
                        }
                    }
                    else -> {
                        Button(
                            onClick = { viewModel.startLogin(serverUrl) },
                            enabled = serverUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.l)
                        ) {
                            Text(stringResource(R.string.login_connect_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginBrandHeader() {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(72.dp)
    ) {
        IconCentered(Icons.Filled.CloudSync)
    }
    Spacer(modifier = Modifier.height(Spacing.l))
    Text(
        stringResource(R.string.login_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
    Text(
        stringResource(R.string.app_name),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs)
    )
}

@Composable
private fun IconCentered(icon: ImageVector) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(36.dp)
        )
    }
}

/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sayertv.mobile.core.common.AppError

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.done) { if (ui.done) onComplete() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // Note 2: Visible above keyboard
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = com.sayertv.mobile.core.designsystem.R.drawable.ic_logo),
            contentDescription = "S.E.E.D TV Logo",
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Jellyfin, the VLC way",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        when (ui.step) {
            OnboardingUiState.Step.ServerUrl -> ServerUrlStep(ui, viewModel)
            OnboardingUiState.Step.CleartextWarning -> CleartextWarningStep(viewModel)
            OnboardingUiState.Step.Credentials -> CredentialsStep(ui, viewModel)
            OnboardingUiState.Step.QuickConnect -> QuickConnectStep(ui, viewModel)
        }

        ui.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it.userMessage(), color = MaterialTheme.colorScheme.error)
        }
    }

    if (ui.showSwitchConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelSwitch,
            title = { Text("Switch Server?") },
            text = { Text("You are already connected to another server. Do you want to switch to ${ui.server?.serverName}?") },
            confirmButton = {
                Button(onClick = viewModel::confirmSwitch) { Text("Switch") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelSwitch) { Text("Keep Current") }
            }
        )
    }
}

@Composable
private fun ServerUrlStep(ui: OnboardingUiState, vm: OnboardingViewModel) {
    OutlinedTextField(
        value = ui.serverUrl,
        onValueChange = vm::onUrlChange,
        label = { Text("Server address") },
        placeholder = { Text("https://jellyfin.example.com") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = vm::probeServer,
        enabled = !ui.probing && ui.serverUrl.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (ui.probing) ThreeDotProgress() else Text("Connect")
    }

    if (ui.savedServers.isNotEmpty()) {
        Spacer(Modifier.height(32.dp))
        Text("Saved Servers", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            ui.savedServers.forEach { server ->
                Surface(
                    onClick = { vm.selectSavedServer(server) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                server.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.removeServer(server.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleartextWarningStep(vm: OnboardingViewModel) {
    Text("Unencrypted connection", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "This server uses HTTP, not HTTPS. Your password and everything you " +
            "watch will be visible to anyone on the network path. Only continue " +
            "for servers on your own local network.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = vm::acknowledgeCleartext, modifier = Modifier.fillMaxWidth()) {
        Text("I understand the risk — continue")
    }
}

@Composable
private fun CredentialsStep(ui: OnboardingUiState, vm: OnboardingViewModel) {
    Text(
        ui.server?.serverName ?: "",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(16.dp))

    if (ui.publicUsers.isNotEmpty()) {
        Text("Select User", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ui.publicUsers) { user ->
                FilterChip(
                    onClick = { vm.selectUser(user) },
                    label = { Text(user.userName) },
                    selected = ui.username == user.userName
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    OutlinedTextField(
        value = ui.username,
        onValueChange = vm::onUsernameChange,
        label = { Text("Username") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = ui.password,
        onValueChange = vm::onPasswordChange,
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = vm::loginWithPassword,
        enabled = !ui.loggingIn && ui.username.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (ui.loggingIn) ThreeDotProgress() else Text("Sign in")
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = vm::startQuickConnect, modifier = Modifier.fillMaxWidth()) {
        Text("Use Quick Connect")
    }
}

@Composable
private fun ThreeDotProgress() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotCount = 3
    
    Row(
        modifier = Modifier.height(24.dp),
        horizontalArrangement = Arrangement.Center, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until dotCount) {
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 200)
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(LocalContentColor.current.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun QuickConnectStep(ui: OnboardingUiState, vm: OnboardingViewModel) {
    Text("Quick Connect", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    if (ui.quickConnectCode == null) {
        CircularProgressIndicator()
    } else {
        Text(
            ui.quickConnectCode,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter this code in Jellyfin on another signed-in device\n" +
                "(Settings → Quick Connect)",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = vm::cancelQuickConnect) { Text("Cancel") }
}

private fun AppError.userMessage(): String = when (this) {
    AppError.SERVER_UNREACHABLE -> "Could not reach the server. Check the address and your network."
    AppError.SERVER_UNSUPPORTED -> "This server runs an unsupported Jellyfin version. S.E.E.D TV requires 10.11 or newer."
    AppError.UNAUTHORIZED -> "Wrong username or password."
    AppError.NETWORK -> "Network error — please try again."
    else -> "Something went wrong. Please try again."
}

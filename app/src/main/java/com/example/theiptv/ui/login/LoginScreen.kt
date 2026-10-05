package com.example.theiptv.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.theiptv.ui.components.CredentialField
import com.example.theiptv.ui.components.ErrorBanner
import com.example.theiptv.ui.theme.AccentBlue
import com.example.theiptv.ui.theme.AppButtonContentPadding
import com.example.theiptv.ui.theme.AppButtonHeight
import com.example.theiptv.ui.theme.AppButtonShape
import com.example.theiptv.ui.theme.AppIconButtonSize

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoggedIn: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            viewModel.consumeLoggedIn()
            onLoggedIn()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp, vertical = 28.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(AccentBlue.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (onBack != null && state.hasProviders) {
                    IconButton(
                        onClick = onBack,
                        shape = IconButtonDefaults.shape(AppButtonShape),
                        modifier = Modifier.size(AppIconButtonSize),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (state.hasProviders) "Add provider" else "TheIPTV",
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    "Enter Xtream Codes details. You can save several providers and switch later.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
                CredentialField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = "Provider name (optional)",
                    leadingIcon = Icons.AutoMirrored.Filled.Label,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(12.dp))
                CredentialField(
                    value = state.server,
                    onValueChange = viewModel::onServerChange,
                    label = "Server address",
                    leadingIcon = Icons.Default.Link,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )
                )
                Text(
                    "Example: http://host:8080 — or paste a full Xtream / M3U URL.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )
                CredentialField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = "Username",
                    leadingIcon = Icons.Default.Person,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(12.dp))
                CredentialField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Password",
                    leadingIcon = Icons.Default.Lock,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.connect()
                        }
                    )
                )
                if (state.error != null) {
                    Spacer(Modifier.height(16.dp))
                    ErrorBanner(state.error!!)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.connect()
                    },
                    enabled = !state.isLoading,
                    shape = ButtonDefaults.shape(AppButtonShape),
                    contentPadding = AppButtonContentPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppButtonHeight)
                ) {
                    Text(if (state.isLoading) "Connecting…" else "Connect")
                }
            }
        }
    }
}

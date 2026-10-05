package com.example.theiptv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.theiptv.ui.components.CredentialField
import com.example.theiptv.ui.components.rememberListWrapState
import com.example.theiptv.ui.components.wrapListNavigation
import com.example.theiptv.ui.login.LoginViewModel
import com.example.theiptv.ui.providers.ProviderListItem
import com.example.theiptv.ui.theme.AppButtonContentPadding
import com.example.theiptv.ui.theme.AppButtonHeight
import com.example.theiptv.ui.theme.AppButtonShape
import com.example.theiptv.ui.theme.AppIconButtonSize
import com.example.theiptv.ui.providers.ProvidersViewModel
import com.example.theiptv.ui.theme.AccentBlue
import com.example.theiptv.ui.theme.Night
import com.example.theiptv.ui.theme.NightSurface

private enum class SettingsPage { Root, Providers, AddProvider }

private val MenuWidth = 340.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsMenu(onProviderSwitched: () -> Unit, focusable: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(SettingsPage.Root) }
    val itemFocus = remember { FocusRequester() }
    val providersVm: ProvidersViewModel = viewModel()
    val loginVm: LoginViewModel = viewModel()
    val providersState by providersVm.state.collectAsStateWithLifecycle()
    val loginState by loginVm.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    fun close() {
        open = false
        page = SettingsPage.Root
    }

    fun onBack() {
        when (page) {
            SettingsPage.AddProvider -> page = SettingsPage.Providers
            SettingsPage.Providers -> page = SettingsPage.Root
            SettingsPage.Root -> close()
        }
    }

    BackHandler(enabled = open, onBack = ::onBack)

    val density = LocalDensity.current
    val menuOffset = IntOffset(with(density) { 20.dp.roundToPx() }, 0)

    Box {
        IconButton(
            onClick = {
                if (open) close() else {
                    page = SettingsPage.Root
                    open = true
                }
            },
            shape = IconButtonDefaults.shape(AppButtonShape),
            modifier = Modifier
                .size(AppIconButtonSize)
                .focusProperties { canFocus = focusable },
            colors = IconButtonDefaults.colors(
                containerColor = if (open) AccentBlue else NightSurface,
                contentColor = if (open) Night else MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = AccentBlue.copy(alpha = 0.85f),
                focusedContentColor = Night,
                pressedContainerColor = AccentBlue.copy(alpha = 0.85f),
                pressedContentColor = Night,
            )
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
        if (open) {
            Popup(
                alignment = Alignment.BottomEnd,
                offset = menuOffset,
                onDismissRequest = { close() },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = true,
                ),
            ) {
                BackHandler(onBack = ::onBack)
                LaunchedEffect(page) {
                    if (page == SettingsPage.AddProvider) loginVm.resetForm()
                    try {
                        itemFocus.requestFocus()
                    } catch (_: IllegalStateException) {
                    }
                }
                LaunchedEffect(providersState.connected) {
                    if (providersState.connected) {
                        providersVm.consumeConnected()
                        onProviderSwitched()
                    }
                }
                LaunchedEffect(loginState.loggedIn) {
                    if (loginState.loggedIn) {
                        loginVm.consumeLoggedIn()
                        providersVm.reload()
                        onProviderSwitched()
                        page = SettingsPage.Providers
                    }
                }
                Column(
                    modifier = Modifier
                        .width(MenuWidth)
                        .onPreviewKeyEvent { event ->
                            if (event.key != Key.Back && event.key != Key.Escape) return@onPreviewKeyEvent false
                            if (event.type == KeyEventType.KeyUp) onBack()
                            true
                        }
                        .padding(top = 8.dp)
                        .background(NightSurface, RoundedCornerShape(14.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (page) {
                        SettingsPage.Root -> {
                            SettingsMenuItem(
                                icon = Icons.Default.Tv,
                                label = "Providers",
                                modifier = Modifier.focusRequester(itemFocus),
                                onClick = { page = SettingsPage.Providers },
                            )
                        }
                        SettingsPage.Providers -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { page = SettingsPage.Root },
                                    shape = IconButtonDefaults.shape(AppButtonShape),
                                    modifier = Modifier
                                        .size(AppIconButtonSize)
                                        .focusRequester(itemFocus)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                Text("Providers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { page = SettingsPage.AddProvider },
                                    shape = IconButtonDefaults.shape(AppButtonShape),
                                    modifier = Modifier.size(AppIconButtonSize),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add provider")
                                }
                            }
                            if (providersState.error != null) {
                                Text(
                                    providersState.error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (providersState.providers.isEmpty()) {
                                Text(
                                    "No providers saved.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            } else {
                                val providerListState = rememberLazyListState()
                                val providerWrapState = rememberListWrapState()
                                LazyColumn(
                                    state = providerListState,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.heightIn(max = 280.dp)
                                ) {
                                    itemsIndexed(providersState.providers, key = { _, provider -> provider.id }) { index, provider ->
                                        ProviderListItem(
                                            provider = provider,
                                            isActive = provider.id == providersState.activeId,
                                            isConnecting = provider.id == providersState.connectingId,
                                            enabled = providersState.connectingId == null,
                                            onConnect = { providersVm.connect(provider) },
                                            onDelete = { providersVm.delete(provider) },
                                            wrapModifier = Modifier.wrapListNavigation(
                                                index = index,
                                                count = providersState.providers.size,
                                                state = providerWrapState,
                                                scrollTo = { providerListState.scrollToItem(it) },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        SettingsPage.AddProvider -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { page = SettingsPage.Providers },
                                    shape = IconButtonDefaults.shape(AppButtonShape),
                                    modifier = Modifier
                                        .size(AppIconButtonSize)
                                        .focusRequester(itemFocus)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                Text("Add provider", style = MaterialTheme.typography.titleMedium)
                            }
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 420.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CredentialField(
                                    value = loginState.name,
                                    onValueChange = loginVm::onNameChange,
                                    label = "Name (optional)",
                                    leadingIcon = Icons.AutoMirrored.Filled.Label,
                                    compact = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                                )
                                CredentialField(
                                    value = loginState.server,
                                    onValueChange = loginVm::onServerChange,
                                    label = "Server",
                                    leadingIcon = Icons.Default.Link,
                                    compact = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Next
                                    )
                                )
                                CredentialField(
                                    value = loginState.username,
                                    onValueChange = loginVm::onUsernameChange,
                                    label = "Username",
                                    leadingIcon = Icons.Default.Person,
                                    compact = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                                )
                                CredentialField(
                                    value = loginState.password,
                                    onValueChange = loginVm::onPasswordChange,
                                    label = "Password",
                                    leadingIcon = Icons.Default.Lock,
                                    isPassword = true,
                                    compact = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            focusManager.clearFocus()
                                            loginVm.connect()
                                        }
                                    )
                                )
                                if (loginState.error != null) {
                                    Text(
                                        loginState.error!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        loginVm.connect()
                                    },
                                    enabled = !loginState.isLoading,
                                    shape = ButtonDefaults.shape(AppButtonShape),
                                    contentPadding = AppButtonContentPadding,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(AppButtonHeight)
                                ) {
                                    Text(
                                        if (loginState.isLoading) "Connecting…" else "Connect",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = AccentBlue.copy(alpha = 0.22f),
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

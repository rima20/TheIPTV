package com.example.theiptv.ui.providers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.theiptv.data.IptvProvider
import com.example.theiptv.ui.components.ErrorBanner
import com.example.theiptv.ui.components.rememberListWrapState
import com.example.theiptv.ui.components.wrapListNavigation
import com.example.theiptv.ui.theme.AccentBlue
import com.example.theiptv.ui.theme.AppButtonHeight
import com.example.theiptv.ui.theme.AppButtonShape
import com.example.theiptv.ui.theme.AppButtonWithIconContentPadding
import com.example.theiptv.ui.theme.AppIconButtonSize
import com.example.theiptv.ui.theme.NightSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProvidersScreen(
    viewModel: ProvidersViewModel,
    onConnected: () -> Unit,
    onAddProvider: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    LaunchedEffect(state.connected) {
        if (state.connected) {
            viewModel.consumeConnected()
            onConnected()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .widthIn(max = 640.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    shape = IconButtonDefaults.shape(AppButtonShape),
                    modifier = Modifier.size(AppIconButtonSize),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Providers",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Saved accounts",
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = onAddProvider,
                shape = ButtonDefaults.shape(AppButtonShape),
                contentPadding = AppButtonWithIconContentPadding,
                modifier = Modifier.height(AppButtonHeight),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (state.error != null) {
            ErrorBanner(state.error!!)
            Spacer(Modifier.height(8.dp))
        }
        if (state.providers.isEmpty()) {
            Text(
                "Add an Xtream server to get started.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            val listState = rememberLazyListState()
            val wrapState = rememberListWrapState()
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(state.providers, key = { _, provider -> provider.id }) { index, provider ->
                    ProviderListItem(
                        provider = provider,
                        isActive = provider.id == state.activeId,
                        isConnecting = provider.id == state.connectingId,
                        enabled = state.connectingId == null,
                        onConnect = { viewModel.connect(provider) },
                        onDelete = { viewModel.delete(provider) },
                        wrapModifier = Modifier.wrapListNavigation(
                            index = index,
                            count = state.providers.size,
                            state = wrapState,
                            scrollTo = { listState.scrollToItem(it) },
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProviderListItem(
    provider: IptvProvider,
    isActive: Boolean,
    isConnecting: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
    wrapModifier: Modifier = Modifier,
) {
    Surface(
        onClick = onConnect,
        enabled = enabled,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) AccentBlue.copy(alpha = 0.18f) else NightSurface,
            focusedContainerColor = AccentBlue.copy(alpha = 0.32f),
            disabledContainerColor = NightSurface
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        modifier = Modifier.fillMaxWidth().then(wrapModifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Check else Icons.Default.Tv,
                contentDescription = null,
                tint = if (isActive) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    provider.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = buildString {
                        append(provider.hostLabel())
                        if (isConnecting) append("  ·  Connecting…")
                        else if (isActive) append("  ·  Current")
                    },
                    color = if (isActive || isConnecting) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = enabled,
                shape = IconButtonDefaults.shape(AppButtonShape),
                modifier = Modifier.size(AppIconButtonSize),
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete provider", modifier = Modifier.size(16.dp))
            }
        }
    }
}

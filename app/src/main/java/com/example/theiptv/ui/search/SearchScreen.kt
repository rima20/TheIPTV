package com.example.theiptv.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.ContentType
import com.example.theiptv.ui.components.ContentGrid
import com.example.theiptv.ui.components.CredentialField
import com.example.theiptv.ui.components.ErrorBanner
import com.example.theiptv.ui.theme.AppButtonShape
import com.example.theiptv.ui.theme.AppIconButtonSize

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onPlay: (ContentItem) -> Unit,
    onOpenSeries: (ContentItem) -> Unit,
    onOpenMovie: (ContentItem) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val onPlayState = rememberUpdatedState(onPlay)
    val onOpenSeriesState = rememberUpdatedState(onOpenSeries)
    val onOpenMovieState = rememberUpdatedState(onOpenMovie)
    val onItemClick = remember {
        { item: ContentItem ->
            when (item.type) {
                ContentType.Series -> onOpenSeriesState.value(item)
                ContentType.Movies -> onOpenMovieState.value(item)
                ContentType.Live -> onPlayState.value(item)
            }
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                shape = IconButtonDefaults.shape(AppButtonShape),
                modifier = Modifier.size(AppIconButtonSize),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Search", style = MaterialTheme.typography.titleLarge)
                val subtitle = when {
                    state.loadingMessage != null -> state.loadingMessage!!
                    state.query.trim().length < 2 -> "Type at least 2 letters"
                    else -> "${state.results.size} matches"
                }
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        CredentialField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = "Search",
            leadingIcon = Icons.Default.Search,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { focusManager.clearFocus() }
            )
        )
        Spacer(Modifier.height(16.dp))
        if (state.error != null) {
            ErrorBanner(state.error!!)
            Spacer(Modifier.height(12.dp))
        }
        ContentGrid(
            items = state.results,
            emptyTitle = if (state.query.trim().length < 2) "Start typing" else "No matches",
            emptySubtitle = if (state.query.trim().length < 2) {
                "Search live channels, movies, and series."
            } else if (state.isSearching) {
                "Still looking through the catalog…"
            } else {
                "Try a different name."
            },
            showType = true,
            onItemClick = onItemClick,
            modifier = Modifier.weight(1f)
        )
    }
}

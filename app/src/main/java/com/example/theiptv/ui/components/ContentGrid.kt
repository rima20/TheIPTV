package com.example.theiptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.ContentType
import com.example.theiptv.ui.theme.AccentBlue
import com.example.theiptv.ui.theme.NightSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContentGrid(
    items: List<ContentItem>,
    emptyTitle: String,
    emptySubtitle: String,
    onItemClick: (ContentItem) -> Unit,
    modifier: Modifier = Modifier,
    showType: Boolean = false,
    showEmpty: Boolean = true,
    restoreItemId: String? = null,
    listKey: Any = Unit,
    onItemFocused: ((ContentItem) -> Unit)? = null,
    onRestoreHandled: () -> Unit = {},
) {
    if (items.isEmpty()) {
        if (showEmpty) {
            EmptyState(emptyTitle, emptySubtitle, modifier = modifier)
        } else {
            Box(modifier)
        }
        return
    }
    val gridState = remember(listKey) { LazyGridState() }
    val wrapState = rememberListWrapState()
    val restoreRequester = remember { FocusRequester() }
    var pinnedRestoreId by remember(listKey) { mutableStateOf<String?>(null) }
    val requesterItemId = restoreItemId ?: pinnedRestoreId
    LaunchedEffect(restoreItemId, items) {
        val id = restoreItemId ?: return@LaunchedEffect
        pinnedRestoreId = id
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) {
            onRestoreHandled()
            return@LaunchedEffect
        }
        gridState.scrollToItem(index)
        val visible = withTimeoutOrNull(800) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == index } }
                .first { it }
        }
        if (visible != true) {
            onRestoreHandled()
            return@LaunchedEffect
        }
        repeat(4) { attempt ->
            try {
                restoreRequester.requestFocus()
                onRestoreHandled()
                return@LaunchedEffect
            } catch (_: IllegalStateException) {
                delay(16L * (attempt + 1))
            }
        }
        onRestoreHandled()
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val spacing = 8.dp
        val rowHeight = (maxHeight - spacing) / 2
        val cardWidth = (rowHeight * 2f / 3f).coerceIn(100.dp, 128.dp)
        val columns = ((maxWidth + spacing) / (cardWidth + spacing)).toInt().coerceAtLeast(1)
        val extra = (maxWidth - (cardWidth * columns + spacing * (columns - 1))).coerceAtLeast(0.dp)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(extra / 2, 2.dp, extra / 2, 8.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.gridKey },
                contentType = { _, item -> item.type },
            ) { index, item ->
                ContentCard(
                    item = item,
                    showType = showType,
                    restoreRequester = if (item.id == requesterItemId) restoreRequester else null,
                    onClick = { onItemClick(item) },
                    onFocused = { onItemFocused?.invoke(item) },
                    modifier = Modifier.wrapListNavigation(
                        index = index,
                        count = items.size,
                        state = wrapState,
                        scrollTo = { gridState.scrollToItem(it) },
                        columns = columns,
                        wrapVertical = true,
                        wrapHorizontal = true,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContentCard(
    item: ContentItem,
    onClick: () -> Unit,
    showType: Boolean = false,
    restoreRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale = remember { CardDefaults.scale(focusedScale = 1.04f) }
    val onFocusedState = rememberUpdatedState(onFocused)
    LaunchedEffect(focused) {
        if (focused) onFocusedState.value()
    }
    CompactCard(
        onClick = onClick,
        interactionSource = interactionSource,
        scale = scale,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .then(if (restoreRequester != null) Modifier.focusRequester(restoreRequester) else Modifier),
        image = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NightSurface),
                contentAlignment = Alignment.Center
            ) {
                if (item.posterUrl.isBlank()) {
                    Text(
                        text = item.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentBlue
                    )
                } else {
                    val live = item.type == ContentType.Live
                    RemoteImage(
                        url = item.posterUrl,
                        contentDescription = item.name,
                        contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                        requestWidth = if (live) 96.dp else 160.dp,
                        requestHeight = if (live) 96.dp else 240.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        title = {
            MarqueeText(
                text = item.name,
                style = MaterialTheme.typography.labelMedium,
                scrolling = focused,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        subtitle = {
            val rating = item.rating
            val subtitleStyle = MaterialTheme.typography.labelMedium
            when {
                showType && !rating.isNullOrBlank() -> MarqueeText(
                    text = "${item.type.label}  ·  $rating",
                    style = subtitleStyle,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    scrolling = focused,
                    modifier = Modifier.fillMaxWidth(),
                )
                showType -> MarqueeText(
                    text = item.type.label,
                    style = subtitleStyle,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    scrolling = focused,
                    modifier = Modifier.fillMaxWidth(),
                )
                !rating.isNullOrBlank() -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(rating, style = subtitleStyle)
                    }
                }
            }
        }
    )
}

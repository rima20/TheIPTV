package com.example.theiptv.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.theiptv.data.Category
import com.example.theiptv.ui.theme.AppButtonShape
import com.example.theiptv.ui.theme.AppChipPadding
import com.example.theiptv.ui.theme.AppIconButtonSize
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.ContentType
import com.example.theiptv.data.EpgProgram
import com.example.theiptv.data.PlaybackRequest
import com.example.theiptv.ui.components.ContentGrid
import com.example.theiptv.ui.components.ErrorBanner
import com.example.theiptv.ui.components.FocusedItemBanner
import com.example.theiptv.ui.components.LoadingBox
import com.example.theiptv.ui.components.rememberListWrapState
import com.example.theiptv.ui.components.wrapListNavigation
import com.example.theiptv.ui.guide.GuideScreen
import com.example.theiptv.ui.guide.GuideViewModel
import com.example.theiptv.ui.settings.SettingsMenu
import com.example.theiptv.ui.theme.AccentBlue
import com.example.theiptv.ui.theme.Night
import com.example.theiptv.ui.theme.NightSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onPlay: (ContentItem) -> Unit,
    onOpenSeries: (ContentItem) -> Unit,
    onOpenMovie: (ContentItem) -> Unit,
    onSearch: () -> Unit,
    onGuide: () -> Unit,
    onPlayRequest: (PlaybackRequest) -> Unit,
    onSessionLost: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val homeFocus = remember { FocusRequester() }
    val guideFocus = remember { FocusRequester() }
    val moviesFocus = remember { FocusRequester() }
    val seriesFocus = remember { FocusRequester() }
    val menuHasFocusState = remember { mutableStateOf(false) }
    var menuHasFocus by menuHasFocusState
    var headerLocked by remember {
        mutableStateOf(state.selectedHeader == HeaderFocusTarget.Guide)
    }
    var guideHasFocus by remember { mutableStateOf(false) }
    var exitingToHeader by remember { mutableStateOf(false) }
    var guideFocusSerial by remember { mutableIntStateOf(0) }
    val openGuide = onGuide
    val onPlayState = rememberUpdatedState(onPlay)
    val onOpenSeriesState = rememberUpdatedState(onOpenSeries)
    val onOpenMovieState = rememberUpdatedState(onOpenMovie)
    val onItemFocused = remember(viewModel, menuHasFocusState) {
        { item: ContentItem ->
            menuHasFocusState.value = false
            viewModel.onItemFocused(item)
        }
    }
    val onItemClick = remember(viewModel) {
        { item: ContentItem ->
            viewModel.onItemFocused(item)
            when (item.type) {
                ContentType.Series -> onOpenSeriesState.value(item)
                ContentType.Movies -> onOpenMovieState.value(item)
                ContentType.Live -> onPlayState.value(item)
            }
        }
    }
    val onCategoryFocused = remember(viewModel, menuHasFocusState) {
        { id: String ->
            menuHasFocusState.value = false
            viewModel.onCategoryFocused(id)
        }
    }
    val onMenuFocused = remember(menuHasFocusState) {
        { menuHasFocusState.value = true }
    }

    LaunchedEffect(state.sessionLost) {
        if (state.sessionLost) onSessionLost()
    }
    LaunchedEffect(Unit) {
        viewModel.prepareRestore()
    }
    LaunchedEffect(guideHasFocus, exitingToHeader, state.selectedHeader) {
        if (state.selectedHeader != HeaderFocusTarget.Guide) {
            headerLocked = false
            exitingToHeader = false
            return@LaunchedEffect
        }
        if (exitingToHeader || !guideHasFocus) return@LaunchedEffect
        headerLocked = true
    }
    LaunchedEffect(state.restoreHeader) {
        if (state.selectedHeader == HeaderFocusTarget.Guide) return@LaunchedEffect
        val target = state.restoreHeader ?: return@LaunchedEffect
        val requester = when (target) {
            HeaderFocusTarget.Home -> homeFocus
            HeaderFocusTarget.Guide -> guideFocus
            HeaderFocusTarget.Movies -> moviesFocus
            HeaderFocusTarget.Series -> seriesFocus
        }
        repeat(4) { attempt ->
            try {
                requester.requestFocus()
                viewModel.onFocusRestored()
                return@LaunchedEffect
            } catch (_: IllegalStateException) {
                delay(16L * (attempt + 1))
            }
        }
        viewModel.onFocusRestored()
    }
    LaunchedEffect(guideFocusSerial) {
        if (guideFocusSerial == 0) return@LaunchedEffect
        repeat(4) { attempt ->
            try {
                guideFocus.requestFocus()
                return@LaunchedEffect
            } catch (_: IllegalStateException) {
                delay(16L * (attempt + 1))
            }
        }
        exitingToHeader = false
        headerLocked = state.selectedHeader == HeaderFocusTarget.Guide
    }

    BackHandler(enabled = !menuHasFocus && state.section != BrowseSection.Home) {
        val requester = when (state.section) {
            BrowseSection.Movies -> moviesFocus
            BrowseSection.Series -> seriesFocus
            BrowseSection.Home -> homeFocus
        }
        try {
            requester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Header(
            providerName = state.providerName,
            statusLabel = state.statusLabel,
            expiryLabel = state.expiryLabel,
            selectedHeader = state.selectedHeader,
            onSectionSelected = viewModel::selectSection,
            onSearch = onSearch,
            onGuide = {
                headerLocked = true
                exitingToHeader = false
                openGuide()
            },
            onRefresh = viewModel::refresh,
            onProviderSwitched = viewModel::onProviderSwitched,
            homeFocus = homeFocus,
            guideFocus = guideFocus,
            moviesFocus = moviesFocus,
            seriesFocus = seriesFocus,
            onMenuFocused = onMenuFocused,
            onGuideButtonFocused = { exitingToHeader = false },
            focusable = state.selectedHeader != HeaderFocusTarget.Guide || !headerLocked,
        )
        Spacer(Modifier.height(10.dp))
        if (state.error != null) {
            ErrorBanner(state.error!!)
            Spacer(Modifier.height(12.dp))
        }
        if (state.selectedHeader == HeaderFocusTarget.Guide) {
            val guideVm: GuideViewModel = viewModel()
            GuideScreen(
                viewModel = guideVm,
                openSerial = state.guideOpenSerial,
                onPlay = onPlayRequest,
                onActive = { guideHasFocus = it },
                onBack = {
                    exitingToHeader = true
                    headerLocked = false
                    guideFocusSerial++
                },
            )
        } else if (state.section == BrowseSection.Home) {
            CatalogBody(
                focusedItem = state.focusedItem,
                categoryName = "Favourites",
                nowPlaying = null,
                items = state.visibleItems,
                restoreItemId = state.restoreItemId,
                listKey = "home-favorites",
                showType = true,
                emptyTitle = "No favourites yet",
                emptySubtitle = "Add movies and series from their detail pages.",
                showEmpty = state.favoritesReady,
                onItemFocused = onItemFocused,
                onItemClick = onItemClick,
                onRestoreHandled = viewModel::onFocusRestored,
            )
        } else if (state.isLoading) {
            LoadingBox("Loading ${state.section.label.lowercase()}…")
        } else {
            BrowseCatalog(
                wide = wide,
                categories = state.categories,
                selectedCategoryId = state.selectedCategoryId,
                restoreCategoryId = state.restoreCategoryId,
                restoreItemId = state.restoreItemId,
                listKey = "${state.section}-${state.selectedCategoryId}",
                visibleItems = state.visibleItems,
                focusedItem = state.focusedItem,
                nowPlaying = state.nowPlaying,
                onSelectCategory = viewModel::selectCategory,
                onCategoryFocused = onCategoryFocused,
                onItemFocused = onItemFocused,
                onItemClick = onItemClick,
                onRestoreHandled = viewModel::onFocusRestored,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrowseCatalog(
    wide: Boolean,
    categories: List<Category>,
    selectedCategoryId: String,
    restoreCategoryId: String?,
    restoreItemId: String?,
    listKey: Any,
    visibleItems: List<ContentItem>,
    focusedItem: ContentItem?,
    nowPlaying: EpgProgram?,
    onSelectCategory: (String) -> Unit,
    onCategoryFocused: (String) -> Unit,
    onItemFocused: (ContentItem) -> Unit,
    onItemClick: (ContentItem) -> Unit,
    onRestoreHandled: () -> Unit,
) {
    val categoryName = focusedItem
        ?.categoryIds
        ?.firstNotNullOfOrNull { id ->
            categories.firstOrNull { it.id == id && it.id != Category.ALL_ID }?.name
        }
        .orEmpty()
    if (wide) {
        Row(modifier = Modifier.fillMaxSize()) {
            CategoryRail(
                categories = categories,
                selectedId = selectedCategoryId,
                restoreFocusId = restoreCategoryId,
                onSelect = onSelectCategory,
                onFocused = onCategoryFocused,
                onRestoreHandled = onRestoreHandled,
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .padding(end = 16.dp)
            )
            CatalogBody(
                focusedItem = focusedItem,
                categoryName = categoryName,
                nowPlaying = nowPlaying,
                items = visibleItems,
                restoreItemId = restoreItemId,
                listKey = listKey,
                onItemFocused = onItemFocused,
                onItemClick = onItemClick,
                onRestoreHandled = onRestoreHandled,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            CategoryChips(
                categories = categories,
                selectedId = selectedCategoryId,
                restoreFocusId = restoreCategoryId,
                onSelect = onSelectCategory,
                onFocused = onCategoryFocused,
                onRestoreHandled = onRestoreHandled
            )
            Spacer(Modifier.height(12.dp))
            CatalogBody(
                focusedItem = focusedItem,
                categoryName = categoryName,
                nowPlaying = nowPlaying,
                items = visibleItems,
                restoreItemId = restoreItemId,
                listKey = listKey,
                onItemFocused = onItemFocused,
                onItemClick = onItemClick,
                onRestoreHandled = onRestoreHandled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogBody(
    focusedItem: ContentItem?,
    categoryName: String,
    nowPlaying: EpgProgram?,
    items: List<ContentItem>,
    restoreItemId: String?,
    listKey: Any,
    onItemFocused: (ContentItem) -> Unit,
    onItemClick: (ContentItem) -> Unit,
    onRestoreHandled: () -> Unit,
    modifier: Modifier = Modifier,
    showType: Boolean = false,
    emptyTitle: String = "Nothing here yet",
    emptySubtitle: String = "This category is empty.",
    showEmpty: Boolean = true,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FocusedItemBanner(
            item = focusedItem,
            categoryName = categoryName,
            nowPlaying = nowPlaying
        )
        Spacer(Modifier.height(8.dp))
        ContentGrid(
            items = items,
            emptyTitle = emptyTitle,
            emptySubtitle = emptySubtitle,
            showType = showType,
            showEmpty = showEmpty,
            restoreItemId = restoreItemId,
            listKey = listKey,
            onItemFocused = onItemFocused,
            onRestoreHandled = onRestoreHandled,
            onItemClick = onItemClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Header(
    providerName: String,
    statusLabel: String,
    expiryLabel: String,
    selectedHeader: HeaderFocusTarget,
    onSectionSelected: (BrowseSection) -> Unit,
    onSearch: () -> Unit,
    onGuide: () -> Unit,
    onRefresh: () -> Unit,
    onProviderSwitched: () -> Unit,
    homeFocus: FocusRequester,
    guideFocus: FocusRequester,
    moviesFocus: FocusRequester,
    seriesFocus: FocusRequester,
    onMenuFocused: () -> Unit,
    onGuideButtonFocused: () -> Unit,
    focusable: Boolean,
) {
    fun sectionFocus(section: BrowseSection) = when (section) {
        BrowseSection.Home -> homeFocus
        BrowseSection.Movies -> moviesFocus
        BrowseSection.Series -> seriesFocus
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("TheIPTV", style = MaterialTheme.typography.titleLarge)
            Text(
                text = listOfNotNull(
                    providerName.ifBlank { null },
                    statusLabel.ifBlank { null },
                    expiryLabel.ifBlank { null },
                ).joinToString("  -  "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrowseSection.entries.forEach { section ->
                HeaderChip(
                    label = section.label,
                    icon = section.icon(),
                    selected = selectedHeader == section.headerTarget,
                    onClick = { onSectionSelected(section) },
                    focusRequester = sectionFocus(section),
                    onFocused = onMenuFocused,
                    focusable = focusable,
                )
                if (section == BrowseSection.Home) {
                    HeaderChip(
                        label = "Guide",
                        icon = Icons.Default.DateRange,
                        selected = selectedHeader == HeaderFocusTarget.Guide,
                        onClick = onGuide,
                        focusRequester = guideFocus,
                        onFocused = {
                            onMenuFocused()
                            onGuideButtonFocused()
                        },
                        focusable = focusable,
                    )
                }
            }
            HeaderChip(
                label = "Search",
                icon = Icons.Default.Search,
                selected = false,
                onClick = onSearch,
                onFocused = onMenuFocused,
                focusable = focusable,
            )
            IconButton(
                onClick = onRefresh,
                shape = IconButtonDefaults.shape(AppButtonShape),
                modifier = Modifier
                    .size(AppIconButtonSize)
                    .focusProperties { canFocus = focusable },
                colors = IconButtonDefaults.colors(
                    containerColor = NightSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = AccentBlue.copy(alpha = 0.85f),
                    focusedContentColor = Night,
                    pressedContainerColor = AccentBlue.copy(alpha = 0.85f),
                    pressedContentColor = Night,
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            SettingsMenu(onProviderSwitched = onProviderSwitched, focusable = focusable)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeaderChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    focusable: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) onFocused()
    }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AccentBlue else NightSurface,
            contentColor = if (selected) Night else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = AccentBlue.copy(alpha = 0.85f),
            focusedContentColor = Night
        ),
        shape = ClickableSurfaceDefaults.shape(AppButtonShape),
        modifier = Modifier
            .focusProperties { canFocus = focusable }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(AppChipPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRail(
    categories: List<Category>,
    selectedId: String,
    restoreFocusId: String?,
    onSelect: (String) -> Unit,
    onFocused: (String) -> Unit,
    onRestoreHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val wrapState = rememberListWrapState()
    val restoreRequester = remember { FocusRequester() }
    var pinnedRestoreId by remember { mutableStateOf<String?>(null) }
    val requesterId = restoreFocusId ?: pinnedRestoreId
    LaunchedEffect(selectedId, categories) {
        val index = categories.indexOfFirst { it.id == selectedId }
        if (index >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == index }) {
                listState.scrollToItem(index)
            }
        }
    }
    LaunchedEffect(restoreFocusId, categories) {
        val id = restoreFocusId ?: return@LaunchedEffect
        pinnedRestoreId = id
        val index = categories.indexOfFirst { it.id == id }
        if (index >= 0) {
            listState.scrollToItem(index)
            val visible = withTimeoutOrNull(800) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == index } }
                    .first { it }
            }
            if (visible == true) {
                repeat(4) { attempt ->
                    try {
                        restoreRequester.requestFocus()
                        onRestoreHandled()
                        return@LaunchedEffect
                    } catch (_: IllegalStateException) {
                        delay(16L * (attempt + 1))
                    }
                }
            }
        }
        onRestoreHandled()
    }
    LazyColumn(
        state = listState,
        modifier = modifier.background(NightSurface, RoundedCornerShape(16.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            CategoryItem(
                category = category,
                selected = category.id == selectedId,
                fillWidth = true,
                restoreRequester = if (category.id == requesterId) restoreRequester else null,
                onFocused = { onFocused(category.id) },
                wrapModifier = Modifier.wrapListNavigation(
                    index = index,
                    count = categories.size,
                    state = wrapState,
                    scrollTo = { listState.scrollToItem(it) },
                    wrapVertical = true,
                    wrapHorizontal = false,
                ),
            ) { onSelect(category.id) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryChips(
    categories: List<Category>,
    selectedId: String,
    restoreFocusId: String?,
    onSelect: (String) -> Unit,
    onFocused: (String) -> Unit,
    onRestoreHandled: () -> Unit,
) {
    val listState = rememberLazyListState()
    val wrapState = rememberListWrapState()
    val restoreRequester = remember { FocusRequester() }
    var pinnedRestoreId by remember { mutableStateOf<String?>(null) }
    val requesterId = restoreFocusId ?: pinnedRestoreId
    LaunchedEffect(restoreFocusId, categories) {
        val id = restoreFocusId ?: return@LaunchedEffect
        pinnedRestoreId = id
        val index = categories.indexOfFirst { it.id == id }
        if (index >= 0) {
            listState.scrollToItem(index)
            val visible = withTimeoutOrNull(800) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == index } }
                    .first { it }
            }
            if (visible == true) {
                repeat(4) { attempt ->
                    try {
                        restoreRequester.requestFocus()
                        onRestoreHandled()
                        return@LaunchedEffect
                    } catch (_: IllegalStateException) {
                        delay(16L * (attempt + 1))
                    }
                }
            }
        }
        onRestoreHandled()
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            CategoryItem(
                category = category,
                selected = category.id == selectedId,
                fillWidth = false,
                restoreRequester = if (category.id == requesterId) restoreRequester else null,
                onFocused = { onFocused(category.id) },
                wrapModifier = Modifier.wrapListNavigation(
                    index = index,
                    count = categories.size,
                    state = wrapState,
                    scrollTo = { listState.scrollToItem(it) },
                    wrapVertical = false,
                    wrapHorizontal = true,
                ),
            ) { onSelect(category.id) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryItem(
    category: Category,
    selected: Boolean,
    fillWidth: Boolean,
    restoreRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    wrapModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale = remember { ClickableSurfaceDefaults.scale(focusedScale = 1.03f) }
    val onFocusedState = rememberUpdatedState(onFocused)
    LaunchedEffect(focused) {
        if (focused) onFocusedState.value()
    }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AccentBlue.copy(alpha = 0.22f) else Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = AccentBlue.copy(alpha = 0.35f),
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        scale = scale,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .then(if (restoreRequester != null) Modifier.focusRequester(restoreRequester) else Modifier)
            .then(wrapModifier)
    ) {
        Text(
            text = category.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

private fun BrowseSection.icon() = when (this) {
    BrowseSection.Home -> Icons.Default.Home
    BrowseSection.Movies -> Icons.Default.Movie
    BrowseSection.Series -> Icons.Default.VideoLibrary
}

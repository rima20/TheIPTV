package com.example.theiptv.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.theiptv.app
import com.example.theiptv.data.ApiResult
import com.example.theiptv.data.Category
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.ContentType
import com.example.theiptv.data.EpgProgram
import com.example.theiptv.data.FavoriteRef
import com.example.theiptv.data.PlaybackRequest
import com.example.theiptv.data.XtreamRepository.CachedCatalog
import com.example.theiptv.data.groupedByCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.text.DateFormat
import java.util.Date

enum class BrowseSection(val label: String) {
    Home("Home"),
    Movies("Movies"),
    Series("Series");

    val contentType: ContentType?
        get() = when (this) {
            Home -> null
            Movies -> ContentType.Movies
            Series -> ContentType.Series
        }

    val headerTarget: HeaderFocusTarget
        get() = when (this) {
            Home -> HeaderFocusTarget.Home
            Movies -> HeaderFocusTarget.Movies
            Series -> HeaderFocusTarget.Series
        }
}

enum class HeaderFocusTarget { Home, Guide, Movies, Series }

private enum class BrowseFocusPane { Categories, Grid }

data class BrowseUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val section: BrowseSection = BrowseSection.Home,
    val selectedHeader: HeaderFocusTarget = HeaderFocusTarget.Home,
    val guideOpenSerial: Int = 0,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String = Category.ALL_ID,
    val visibleItems: List<ContentItem> = emptyList(),
    val focusedItem: ContentItem? = null,
    val nowPlaying: EpgProgram? = null,
    val restoreItemId: String? = null,
    val restoreCategoryId: String? = null,
    val restoreHeader: HeaderFocusTarget? = HeaderFocusTarget.Home,
    val totalCount: Int = 0,
    val userLabel: String = "",
    val expiryLabel: String = "",
    val statusLabel: String = "",
    val providerName: String = "",
    val sessionLost: Boolean = false,
    val favoritesReady: Boolean = false,
)

class BrowseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.app().repository
    private val store = application.app().credentialsStore
    private val favoritesStore = application.app().favoritesStore

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state

    private var allItems: List<ContentItem> = emptyList()
    private var itemsByCategory: Map<String, List<ContentItem>> = emptyMap()
    private val categoryByType = mutableMapOf<ContentType, String>()
    private val itemByTypeCategory = mutableMapOf<String, String>()
    private var lastPane = BrowseFocusPane.Grid
    private var lastFocusedCategoryId: String? = null
    private var pendingHeaderRestore: HeaderFocusTarget? = null
    private var epgJob: Job? = null
    private var activeProviderId: String? = null
    private var allFavorites: List<FavoriteRef> = emptyList()
    private var favoritesReady = false

    init {
        val session = repository.session
        if (session == null) {
            _state.update { it.copy(isLoading = false, sessionLost = true) }
        } else {
            _state.update {
                it.copy(
                    userLabel = session.userInfo.username.ifBlank { session.credentials.username },
                    statusLabel = session.userInfo.status,
                    expiryLabel = formatExpiry(session.userInfo.expDateSeconds),
                )
            }
            viewModelScope.launch {
                val snapshot = store.loadAll()
                activeProviderId = snapshot.active?.id
                _state.update { it.copy(providerName = snapshot.active?.displayName.orEmpty()) }
                favoritesStore.observe().collect { favorites ->
                    allFavorites = favorites
                    favoritesReady = true
                    if (_state.value.section == BrowseSection.Home) publishHome()
                }
            }
            preloadCatalogs()
        }
    }

    fun selectSection(section: BrowseSection) {
        val current = _state.value
        val header = section.headerTarget
        if (section == current.section && current.selectedHeader == header) {
            if (section == BrowseSection.Home || allItems.isNotEmpty()) return
        }
        current.section.contentType?.let { categoryByType[it] = current.selectedCategoryId }
        val nextType = section.contentType
        val restoredCategory = nextType?.let { categoryByType[it] } ?: Category.ALL_ID
        lastFocusedCategoryId = restoredCategory
        if (section == BrowseSection.Home) {
            allItems = emptyList()
            itemsByCategory = emptyMap()
            _state.update {
                it.copy(
                    section = section,
                    selectedHeader = header,
                    selectedCategoryId = restoredCategory,
                    error = null,
                    nowPlaying = null,
                    categories = emptyList(),
                    isLoading = false,
                    visibleItems = if (favoritesReady) it.visibleItems else emptyList(),
                    focusedItem = if (favoritesReady) it.focusedItem else null,
                )
            }
            publishHome()
            return
        }
        val cached = nextType?.let { repository.cachedCatalog(it) }
        _state.update {
            it.copy(
                section = section,
                selectedHeader = header,
                selectedCategoryId = restoredCategory,
                error = null,
                nowPlaying = null,
                isLoading = cached == null,
            )
        }
        if (cached != null) {
            showCatalog(cached)
        } else {
            load(nextType!!, force = false)
        }
    }

    fun selectCategory(id: String) {
        lastFocusedCategoryId = id
        _state.value.section.contentType?.let { categoryByType[it] = id }
        _state.update { it.copy(selectedCategoryId = id) }
        publishVisible()
    }

    fun onItemFocused(item: ContentItem) {
        lastPane = BrowseFocusPane.Grid
        itemByTypeCategory[focusKey()] = item.id
        val merged = if (item.type == ContentType.Movies) {
            repository.cachedVodDetails(item.id)?.let { item.withDetails(it) } ?: item
        } else {
            item
        }
        val current = _state.value.focusedItem
        val same = current?.id == merged.id && current.type == merged.type
        if (same && current.plot == merged.plot) {
            if (merged.type != ContentType.Movies || !current.plot.isNullOrBlank()) return
        } else {
            _state.update {
                it.copy(
                    focusedItem = merged,
                    nowPlaying = if (same) it.nowPlaying else null,
                )
            }
        }
        loadFocusedExtras(merged)
    }

    fun onCategoryFocused(id: String) {
        lastPane = BrowseFocusPane.Categories
        lastFocusedCategoryId = id
    }

    fun onOpenGuide() {
        _state.update {
            it.copy(
                selectedHeader = HeaderFocusTarget.Guide,
                guideOpenSerial = it.guideOpenSerial + 1,
            )
        }
    }

    fun prepareRestore() {
        if (_state.value.selectedHeader == HeaderFocusTarget.Guide) {
            _state.update {
                it.copy(restoreHeader = null, restoreItemId = null, restoreCategoryId = null)
            }
            return
        }
        val header = pendingHeaderRestore
        if (header != null) {
            _state.update {
                it.copy(
                    restoreHeader = header,
                    restoreItemId = null,
                    restoreCategoryId = null,
                )
            }
            return
        }
        if (_state.value.section == BrowseSection.Home) {
            val restoreItem = lastPane == BrowseFocusPane.Grid
            _state.update {
                it.copy(
                    restoreHeader = if (restoreItem) null else HeaderFocusTarget.Home,
                    restoreItemId = if (restoreItem) itemByTypeCategory[focusKey()] else null,
                    restoreCategoryId = null,
                )
            }
            return
        }
        when (lastPane) {
            BrowseFocusPane.Categories -> {
                _state.update {
                    it.copy(
                        restoreCategoryId = lastFocusedCategoryId ?: it.selectedCategoryId,
                        restoreItemId = null,
                        restoreHeader = null,
                    )
                }
            }
            BrowseFocusPane.Grid -> {
                _state.update {
                    it.copy(
                        restoreItemId = itemByTypeCategory[focusKey()],
                        restoreCategoryId = null,
                        restoreHeader = null,
                    )
                }
            }
        }
    }

    fun onFocusRestored() {
        pendingHeaderRestore = null
        _state.update { it.copy(restoreItemId = null, restoreCategoryId = null, restoreHeader = null) }
    }

    fun refresh() {
        val type = _state.value.section.contentType
        if (type != null) {
            load(type, force = true)
            preloadCatalogs(except = type)
        } else {
            preloadCatalogs(force = true)
        }
    }

    fun onProviderSwitched() {
        val session = repository.session
        if (session == null) {
            _state.update { it.copy(sessionLost = true, isLoading = false) }
            return
        }
        viewModelScope.launch {
            val snapshot = store.loadAll()
            activeProviderId = snapshot.active?.id
            _state.update {
                it.copy(
                    providerName = snapshot.active?.displayName.orEmpty(),
                    userLabel = session.userInfo.username.ifBlank { session.credentials.username },
                    statusLabel = session.userInfo.status,
                    expiryLabel = formatExpiry(session.userInfo.expDateSeconds),
                    error = null,
                )
            }
            if (_state.value.section == BrowseSection.Home) publishHome()
            val type = _state.value.section.contentType
            if (type != null) {
                load(type, force = true)
                preloadCatalogs(except = type)
            } else {
                preloadCatalogs(force = true)
            }
        }
    }

    fun play(item: ContentItem): PlaybackRequest? {
        if (item.type == ContentType.Series) return null
        return repository.play(item)
    }

    private fun preloadCatalogs(except: ContentType? = null, force: Boolean = false) {
        viewModelScope.launch {
            suspend fun load(type: ContentType) {
                if (force) {
                    repository.refresh(type)
                } else {
                    repository.categories(type)
                    repository.items(type)
                }
            }
            if (except != ContentType.Live) {
                load(ContentType.Live)
            }
            launch { repository.syncEpg() }
            supervisorScope {
                for (type in PRELOAD_TYPES) {
                    if (type == except || type == ContentType.Live) continue
                    launch { load(type) }
                }
            }
            if (_state.value.section == BrowseSection.Home) publishHome()
        }
    }

    private fun publishHome() {
        if (!favoritesReady) return
        val providerId = activeProviderId
        val items = allFavorites
            .filter { it.providerId == providerId }
            .map { ref ->
                repository.cachedItem(ref.type, ref.id) ?: ref.toContentItem()
            }
        val rememberedId = itemByTypeCategory[focusKey()]
        val focused = items.find { it.id == rememberedId }
            ?: items.find { it.id == _state.value.focusedItem?.id && it.type == _state.value.focusedItem?.type }
            ?: items.firstOrNull()
        _state.update {
            it.copy(
                visibleItems = items,
                focusedItem = focused,
                nowPlaying = null,
                totalCount = items.size,
                isLoading = false,
                favoritesReady = true,
            )
        }
        if (focused != null) loadFocusedExtras(focused)
    }

    private fun showCatalog(catalog: CachedCatalog) {
        allItems = catalog.items
        itemsByCategory = catalog.items.groupedByCategory()
        _state.update {
            it.copy(
                isLoading = false,
                error = null,
                categories = catalog.categories,
                totalCount = catalog.items.size,
            )
        }
        publishVisible()
    }

    private fun load(type: ContentType, force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val catalog = if (force) {
                repository.refresh(type)
            } else {
                repository.cachedCatalog(type)?.let { ApiResult.Ok(it) }
                    ?: run {
                        val categories = repository.categories(type)
                        val items = repository.items(type)
                        if (categories is ApiResult.Ok && items is ApiResult.Ok) {
                            ApiResult.Ok(CachedCatalog(categories.data, items.data))
                        } else {
                            val err = (categories as? ApiResult.Err)?.message
                                ?: (items as? ApiResult.Err)?.message
                                ?: "Couldn't load content."
                            ApiResult.Err(err)
                        }
                    }
            }
            when (catalog) {
                is ApiResult.Ok -> {
                    if (_state.value.section.contentType != type) return@launch
                    showCatalog(catalog.data)
                }
                is ApiResult.Err -> {
                    if (_state.value.section.contentType != type) return@launch
                    allItems = emptyList()
                    itemsByCategory = emptyMap()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = catalog.message,
                            categories = listOf(Category.All),
                            visibleItems = emptyList(),
                            totalCount = 0,
                        )
                    }
                }
            }
        }
    }

    private fun publishVisible() {
        val categoryId = _state.value.selectedCategoryId
        val filtered = itemsByCategory[categoryId] ?: emptyList()
        val rememberedId = itemByTypeCategory[focusKey()]
        val focused = filtered.find { it.id == rememberedId }
            ?: filtered.find { it.id == _state.value.focusedItem?.id }
            ?: filtered.firstOrNull()
        val nowPlaying = _state.value.nowPlaying.takeIf { focused?.id == _state.value.focusedItem?.id }
        _state.update {
            it.copy(
                visibleItems = filtered,
                focusedItem = focused,
                nowPlaying = nowPlaying,
            )
        }
        if (focused != null) {
            loadFocusedExtras(focused)
        }
    }

    private fun loadFocusedExtras(item: ContentItem) {
        epgJob?.cancel()
        when (item.type) {
            ContentType.Live -> loadNowPlaying(item)
            ContentType.Movies -> loadVodDetails(item)
            ContentType.Series -> Unit
        }
    }

    private fun loadNowPlaying(item: ContentItem) {
        val cached = repository.cachedEpg(item.id)
        if (cached != null) {
            _state.update { it.copy(nowPlaying = currentProgram(cached)) }
            return
        }
        epgJob = viewModelScope.launch {
            delay(280)
            if (_state.value.focusedItem?.id != item.id) return@launch
            when (val result = repository.shortEpg(item.id)) {
                is ApiResult.Ok -> {
                    if (_state.value.focusedItem?.id == item.id) {
                        _state.update { it.copy(nowPlaying = currentProgram(result.data)) }
                    }
                }
                is ApiResult.Err -> Unit
            }
        }
    }

    private fun loadVodDetails(item: ContentItem) {
        val cached = repository.cachedVodDetails(item.id)
        if (cached != null) {
            _state.update {
                val focused = it.focusedItem ?: item
                if (focused.id != item.id) it else it.copy(focusedItem = focused.withDetails(cached))
            }
            return
        }
        epgJob = viewModelScope.launch {
            delay(280)
            if (_state.value.focusedItem?.id != item.id) return@launch
            when (val result = repository.vodDetails(item.id)) {
                is ApiResult.Ok -> {
                    if (_state.value.focusedItem?.id == item.id) {
                        _state.update {
                            val focused = it.focusedItem ?: item
                            it.copy(focusedItem = focused.withDetails(result.data))
                        }
                    }
                }
                is ApiResult.Err -> Unit
            }
        }
    }

    private fun currentProgram(programs: List<EpgProgram>): EpgProgram? {
        val now = System.currentTimeMillis()
        return programs.firstOrNull { it.isPlayingAt(now) } ?: programs.firstOrNull { it.startMs >= now }
    }

    private fun focusKey(): String {
        val state = _state.value
        val type = state.section.contentType ?: return "home"
        return "${type.name}:${state.selectedCategoryId}"
    }

    private fun formatExpiry(seconds: Long?): String {
        if (seconds == null) return "No expiry listed"
        val millis = seconds * 1000L
        val formatted = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
        return if (millis < System.currentTimeMillis()) "Expired $formatted" else "Expires $formatted"
    }

    private companion object {
        val PRELOAD_TYPES = listOf(ContentType.Live, ContentType.Movies, ContentType.Series)
    }
}

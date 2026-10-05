package com.example.theiptv.ui.guide

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.theiptv.app
import com.example.theiptv.data.ApiResult
import com.example.theiptv.data.Category
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.ContentType
import com.example.theiptv.data.EpgProgram
import com.example.theiptv.data.PlaybackRequest
import com.example.theiptv.data.groupedByCategory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GuideUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String = Category.ALL_ID,
    val channels: List<ContentItem> = emptyList(),
    val epgByChannel: Map<String, List<EpgProgram>> = emptyMap(),
    val loadingEpgIds: Set<String> = emptySet(),
    val nowMs: Long = System.currentTimeMillis(),
    val timelineStartMs: Long = GuideViewModel.alignedWindowStart(System.currentTimeMillis() - GuideViewModel.DAY_MS),
    val timelineEndMs: Long = GuideViewModel.alignedWindowStart(System.currentTimeMillis()) + GuideViewModel.WINDOW_MS,
    val followLive: Boolean = true,
    val liveJumpSerial: Int = 0,
    val groupsVisible: Boolean = true,
    val focusedChannelId: String? = null,
    val focusedProgramKey: String? = null,
    val viewStartMs: Long = 0L,
    val focusTimeMs: Long = 0L,
    val timelineScroll: Int = 0,
    val channelScrollIndex: Int = 0,
    val channelScrollOffset: Int = 0,
    val categoryScrollIndex: Int = 0,
    val categoryScrollOffset: Int = 0,
)

class GuideViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.app().repository

    private val _state = MutableStateFlow(GuideUiState())
    val state: StateFlow<GuideUiState> = _state

    private var allChannels: List<ContentItem> = emptyList()
    private var channelsByCategory: Map<String, List<ContentItem>> = emptyMap()
    private var publishJob: Job? = null
    private var categoryJob: Job? = null
    private var hydrateGeneration: Int = 0
    private val hydratingIds = HashSet<String>()
    private val noStoredEpg = HashSet<String>()
    private var consumedOpenSerial: Int = 0
    private val shortEpgResolved = HashSet<String>()

    init {
        loadChannels()
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                val now = System.currentTimeMillis()
                _state.update { current ->
                    current.copy(nowMs = now)
                }
            }
        }
    }

    fun consumeOpenSerial(openSerial: Int): Boolean {
        if (openSerial <= 0 || openSerial <= consumedOpenSerial) return false
        consumedOpenSerial = openSerial
        jumpToLive()
        selectCategory(Category.ALL_ID)
        _state.update {
            it.copy(
                groupsVisible = true,
                focusedChannelId = null,
                focusedProgramKey = null,
                viewStartMs = 0L,
                focusTimeMs = 0L,
                timelineScroll = 0,
                channelScrollIndex = 0,
                channelScrollOffset = 0,
            )
        }
        return true
    }

    fun saveViewport(
        viewStartMs: Long,
        focusTimeMs: Long,
        channelIndex: Int,
        channelOffset: Int,
        categoryIndex: Int,
        categoryOffset: Int,
    ) {
        _state.update {
            it.copy(
                viewStartMs = viewStartMs,
                focusTimeMs = focusTimeMs,
                channelScrollIndex = channelIndex,
                channelScrollOffset = channelOffset,
                categoryScrollIndex = categoryIndex,
                categoryScrollOffset = categoryOffset,
            )
        }
    }

    fun setGroupsVisible(visible: Boolean) {
        _state.update { current ->
            if (current.groupsVisible == visible) current else current.copy(groupsVisible = visible)
        }
    }

    fun rememberFocus(channelId: String, programKey: String) {
        _state.update { current ->
            if (current.focusedChannelId == channelId && current.focusedProgramKey == programKey) {
                current
            } else {
                current.copy(focusedChannelId = channelId, focusedProgramKey = programKey)
            }
        }
    }

    fun selectCategory(id: String) {
        val raw = channelsByCategory[id].orEmpty()
        // Publish the channel list before the programme query so the grid switches with the category.
        val request = beginHydrate(raw)
        _state.update { current ->
            if (current.selectedCategoryId == id && current.channels === raw) {
                current
            } else {
                current.copy(selectedCategoryId = id, channels = raw)
            }
        }
        categoryJob?.cancel()
        categoryJob = viewModelScope.launch {
            if (finishHydrate(request)) publishChannels()
        }
    }

    fun ensureEpg(channelId: String, startMs: Long, endMs: Long) {
        if (channelId in hydratingIds) return
        val existing = _state.value.epgByChannel[channelId]
        if (existing != null && coversWindow(existing, startMs, endMs)) return
        if (channelId in shortEpgResolved) return
        if (_state.value.loadingEpgIds.contains(channelId)) return
        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) {
                repository.localEpg(channelId, startMs, endMs)
            }
            if (local != null && coversWindow(local, startMs, endMs)) {
                _state.update {
                    it.copy(
                        epgByChannel = it.epgByChannel + (channelId to local),
                        loadingEpgIds = it.loadingEpgIds - channelId,
                    )
                }
                return@launch
            }
            val published = _state.value.epgByChannel[channelId]
            if (published != null && coversWindow(published, startMs, endMs)) return@launch
            if (channelId in shortEpgResolved) return@launch
            awaitEpg(channelId)
        }
    }

    fun stopFollowingLive() {
        _state.update { current ->
            if (!current.followLive) current else current.copy(followLive = false)
        }
    }

    fun jumpToLive() {
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                followLive = true,
                nowMs = now,
                liveJumpSerial = it.liveJumpSerial + 1,
            ).withTimeline(allChannels)
        }
    }

    fun play(channel: ContentItem, program: EpgProgram? = null, fromStart: Boolean = false): PlaybackRequest? {
        return repository.play(channel, program, fromStart)
    }

    private fun GuideUiState.withTimeline(channels: List<ContentItem>): GuideUiState {
        val now = nowMs
        val start = alignedWindowStart(now - lookbackMs(channels))
        val epgEnd = epgByChannel.values.maxOfOrNull { programs ->
            programs.maxOfOrNull { it.endMs } ?: Long.MIN_VALUE
        }?.takeIf { it != Long.MIN_VALUE } ?: now
        val end = maxOf(alignedWindowStart(now) + WINDOW_MS, epgEnd)
        return copy(timelineStartMs = start, timelineEndMs = end)
    }

    private fun lookbackMs(channels: List<ContentItem>): Long {
        val days = channels.maxOfOrNull { it.tvArchiveDurationDays }?.coerceAtLeast(1) ?: 1
        return days.coerceAtMost(14) * DAY_MS
    }

    private fun loadChannels() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val categories = repository.categories(ContentType.Live)
            val items = repository.items(ContentType.Live)
            if (categories is ApiResult.Err && items is ApiResult.Err) {
                _state.update {
                    it.copy(isLoading = false, error = items.message)
                }
                return@launch
            }
            allChannels = (items as? ApiResult.Ok)?.data.orEmpty()
            channelsByCategory = allChannels.groupedByCategory()
            val initialChannels = channelsByCategory[_state.value.selectedCategoryId] ?: allChannels
            // End loading only once the channel list is in the same update. Clearing
            // isLoading first shows "No live channels" while hydrateStoredEpg is still running.
            _state.update {
                it.copy(
                    isLoading = false,
                    channels = initialChannels,
                    categories = (categories as? ApiResult.Ok)?.data ?: listOf(Category.All),
                    error = if (allChannels.isEmpty()) {
                        (items as? ApiResult.Err)?.message ?: "No live channels to show."
                    } else {
                        null
                    },
                    nowMs = System.currentTimeMillis(),
                ).withTimeline(allChannels)
            }
            if (hydrateStoredEpg()) publishChannels()
            launch { repository.syncEpg() }
            launch {
                repository.epgEpoch.collect { epoch ->
                    if (epoch <= 0) return@collect
                    noStoredEpg.clear()
                    refreshLocalEpg()
                }
            }
        }
    }

    private fun beginHydrate(channels: List<ContentItem>): HydrateRequest {
        val generation = ++hydrateGeneration
        val now = System.currentTimeMillis()
        val visibleStart = now - VISIBLE_BEHIND_MS
        val visibleEnd = now + VISIBLE_AHEAD_MS
        val missing = ArrayList<String>(PRELOAD_ROWS * 3)
        for (channel in channels) {
            if (missing.size == PRELOAD_ROWS * 3) break
            val id = channel.id
            if (id in noStoredEpg) continue
            val existing = _state.value.epgByChannel[id]
            if (existing == null || !coversWindow(existing, visibleStart, visibleEnd)) {
                missing += id
            }
        }
        hydratingIds.clear()
        hydratingIds.addAll(missing)
        return HydrateRequest(generation, missing, now, visibleStart, visibleEnd)
    }

    private suspend fun hydrateStoredEpg(): Boolean {
        val channels = channelsByCategory[_state.value.selectedCategoryId].orEmpty()
        return finishHydrate(beginHydrate(channels))
    }

    private suspend fun finishHydrate(request: HydrateRequest): Boolean {
        try {
            if (request.generation != hydrateGeneration) return false
            if (request.channelIds.isEmpty() || !repository.epgReady) return true
            val quick = withContext(Dispatchers.IO) {
                repository.localEpgs(request.channelIds, request.visibleStartMs, request.visibleEndMs)
            }
            if (request.generation != hydrateGeneration) return false
            mergeEpg(quick)
            val wideStart = request.nowMs - DAY_MS
            val wideEnd = request.nowMs + 2 * DAY_MS
            val wide = withContext(Dispatchers.IO) {
                repository.localEpgs(request.channelIds, wideStart, wideEnd)
            }
            if (request.generation != hydrateGeneration) return false
            if (repository.epgReady) {
                for (id in request.channelIds) {
                    if (id !in wide) noStoredEpg += id
                }
            }
            mergeEpg(wide)
            return true
        } finally {
            if (request.generation == hydrateGeneration) hydratingIds.clear()
        }
    }

    private fun mergeEpg(loaded: Map<String, List<EpgProgram>>) {
        if (loaded.isEmpty()) return
        _state.update { current ->
            current.copy(epgByChannel = current.epgByChannel + loaded)
        }
    }

    private fun publishChannels() {
        val categoryId = _state.value.selectedCategoryId
        val raw = channelsByCategory[categoryId] ?: emptyList()
        val first = raw.firstOrNull()
        val knownEpg = first?.let { _state.value.epgByChannel[it.id] }
        publishJob?.cancel()
        if (first == null || knownEpg != null) {
            val channels = resolvedChannels(raw, _state.value.epgByChannel)
            _state.update { it.copy(channels = channels) }
            preloadGuide(channels)
            return
        }
        val pending = raw.drop(1)
        // A one-channel list would be empty after holding the first row back, which
        // flashes "No live channels" until its programme info arrives.
        val shown = pending.ifEmpty { raw }
        _state.update { it.copy(channels = shown) }
        preloadGuide(shown)
        publishJob = viewModelScope.launch {
            awaitEpg(first.id)
            if (_state.value.selectedCategoryId != categoryId) return@launch
            val channels = resolvedChannels(raw, _state.value.epgByChannel)
            _state.update { it.copy(channels = channels) }
            preloadGuide(channels)
        }
    }

    private suspend fun awaitEpg(channelId: String) {
        val now = _state.value.nowMs
        val windowStart = now - 8L * 60L * 60L * 1000L
        val windowEnd = now + 12L * 60L * 60L * 1000L
        val existing = _state.value.epgByChannel[channelId]
        if (existing != null && coversWindow(existing, windowStart, windowEnd)) return
        if (channelId in shortEpgResolved) return
        _state.update { it.copy(loadingEpgIds = it.loadingEpgIds + channelId) }
        val result = try {
            repository.shortEpg(channelId)
        } catch (error: kotlinx.coroutines.CancellationException) {
            _state.update { it.copy(loadingEpgIds = it.loadingEpgIds - channelId) }
            throw error
        }
        if (result is ApiResult.Ok) shortEpgResolved += channelId
        val programs = when (result) {
            is ApiResult.Ok -> result.data
            is ApiResult.Err -> emptyList()
        }
        _state.update { current ->
            val kept = current.epgByChannel[channelId].orEmpty()
            val next = when {
                programs.isEmpty() -> kept
                coversWindow(kept, windowStart, windowEnd) -> kept
                else -> programs
            }
            val end = if (next.isNotEmpty()) {
                maxOf(current.timelineEndMs, next.maxOf { it.endMs })
            } else {
                current.timelineEndMs
            }
            current.copy(
                epgByChannel = current.epgByChannel + (channelId to next),
                loadingEpgIds = current.loadingEpgIds - channelId,
                timelineEndMs = end,
            )
        }
    }

    private fun preloadGuide(channels: List<ContentItem>) {
        val now = _state.value.nowMs
        val start = now - 8L * 60L * 60L * 1000L
        val end = now + 12L * 60L * 60L * 1000L
        channels.take(PRELOAD_ROWS).forEach { ensureEpg(it.id, start, end) }
    }

    private suspend fun refreshLocalEpg() {
        val now = _state.value.nowMs
        val start = now - 12L * 60L * 60L * 1000L
        val end = now + 12L * 60L * 60L * 1000L
        val ids = (_state.value.channels.take(PRELOAD_ROWS).map { it.id } + _state.value.epgByChannel.keys)
            .distinct()
        if (ids.isEmpty()) return
        val loaded = withContext(Dispatchers.IO) {
            ids.mapNotNull { id ->
                val programs = repository.localEpg(id, start, end).orEmpty()
                if (programs.isEmpty()) null else id to programs
            }.toMap()
        }
        _state.update { current ->
            current.copy(
                epgByChannel = current.epgByChannel + loaded,
                loadingEpgIds = current.loadingEpgIds - loaded.keys,
            )
        }
    }

    private fun coversWindow(programs: List<EpgProgram>, startMs: Long, endMs: Long): Boolean {
        if (programs.isEmpty()) return false
        val minStart = programs.minOf { it.startMs }
        val maxEnd = programs.maxOf { it.endMs }
        return minStart <= startMs + 30L * 60L * 1000L && maxEnd >= endMs - 30L * 60L * 1000L
    }

    private fun resolvedChannels(
        raw: List<ContentItem>,
        epgByChannel: Map<String, List<EpgProgram>>,
    ): List<ContentItem> {
        val first = raw.firstOrNull() ?: return raw
        val programs = epgByChannel[first.id]
        return if (programs != null && programs.isEmpty()) raw.drop(1) else raw
    }

    private data class HydrateRequest(
        val generation: Int,
        val channelIds: List<String>,
        val nowMs: Long,
        val visibleStartMs: Long,
        val visibleEndMs: Long,
    )

    companion object {
        const val WINDOW_MS = 4 * 60 * 60 * 1000L
        const val SLOT_MS = 30 * 60 * 1000L
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val PRELOAD_ROWS = 16
        private const val VISIBLE_BEHIND_MS = 8L * 60L * 60L * 1000L
        private const val VISIBLE_AHEAD_MS = 12L * 60L * 60L * 1000L

        fun alignedWindowStart(nowMs: Long): Long = nowMs - (nowMs % SLOT_MS)
    }
}

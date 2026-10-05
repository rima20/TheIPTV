package com.example.theiptv.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.tv.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.theiptv.data.Category
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.EpgProgram
import com.example.theiptv.data.PlaybackRequest
import com.example.theiptv.ui.components.EmptyState
import com.example.theiptv.ui.components.ErrorBanner
import com.example.theiptv.ui.components.LoadingBox
import com.example.theiptv.ui.components.isHeldRepeat
import com.example.theiptv.ui.components.rememberListWrapState
import com.example.theiptv.ui.components.wrapListNavigation
import com.example.theiptv.ui.theme.AccentBlue
import com.example.theiptv.ui.theme.AccentTeal
import com.example.theiptv.ui.theme.NightSurface
import com.example.theiptv.ui.theme.NightSurfaceAlt
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val ChannelColumnWidth = 196.dp
private val RowHeight = 30.dp
private val DpPerMinute = 4.dp
private val InfoStripVerticalPadding = 4.dp
private val InfoStripGap = 2.dp
private val GuideCellIdle = Color(0xFF2B3848)
private val GuideCellPast = over(NightSurfaceAlt, AccentBlue.copy(alpha = 0.22f))
private val GuideCellFocused = over(NightSurfaceAlt, AccentBlue.copy(alpha = 0.45f))
private val CategorySelected = AccentBlue.copy(alpha = 0.22f)
private val CategoryFocused = AccentBlue.copy(alpha = 0.35f)
private val CellShape = RoundedCornerShape(6.dp)
private val CategoryShape = RoundedCornerShape(10.dp)
private val NowLineWidth = 1.dp
private val LiveNowViewportFraction = 0.28f
private const val TimelineRevealHourMs = 60L * 60L * 1000L
private val ViewportBufferMs = 90L * 60L * 1000L
private const val MaxGuideSpanPx = 16_384
private const val MaxTimelinePx = 1_000_000

private enum class TimelinePan { None, Left, Right }

private val NoAnimatedBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GuideScreen(
    viewModel: GuideViewModel,
    openSerial: Int,
    onPlay: (PlaybackRequest) -> Unit,
    onActive: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    var groupsVisible by remember { mutableStateOf(state.groupsVisible) }
    var firstProgramFocusSerial by remember { mutableIntStateOf(0) }
    var pendingCategoryFocus by remember { mutableStateOf(state.groupsVisible) }
    var focusCategoryId by remember { mutableStateOf(state.selectedCategoryId) }
    var gridHasFocus by remember { mutableStateOf(!state.groupsVisible && state.focusedProgramKey != null) }
    val categoryFocus = remember { FocusRequester() }
    val categoryRailState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.categoryScrollIndex,
        initialFirstVisibleItemScrollOffset = state.categoryScrollOffset,
    )
    val categoryChipState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.categoryScrollIndex,
        initialFirstVisibleItemScrollOffset = state.categoryScrollOffset,
    )
    val categoryListState = if (wide) categoryRailState else categoryChipState
    val onPlayState = rememberUpdatedState(onPlay)
    val onActiveState = rememberUpdatedState(onActive)
    val playHandler = remember<(ContentItem, EpgProgram?) -> Unit> {
        { channel, program -> viewModel.play(channel, program)?.let(onPlayState.value) }
    }
    val playFromStartHandler = remember<(ContentItem, EpgProgram) -> Unit> {
        { channel, program -> viewModel.play(channel, program, fromStart = true)?.let(onPlayState.value) }
    }
    val ensureEpg = remember<(String, Long, Long) -> Unit>(viewModel) {
        { id, start, end -> viewModel.ensureEpg(id, start, end) }
    }
    val stopFollowingLive = remember(viewModel) { { viewModel.stopFollowingLive() } }

    fun showGroups() {
        groupsVisible = true
        viewModel.setGroupsVisible(true)
        focusCategoryId = state.selectedCategoryId
        pendingCategoryFocus = true
    }

    fun hideGroupsAndFocusFirstProgram() {
        groupsVisible = false
        viewModel.setGroupsVisible(false)
        firstProgramFocusSerial++
    }

    LaunchedEffect(openSerial, state.isLoading) {
        if (openSerial <= 0 || state.isLoading) return@LaunchedEffect
        if (!viewModel.consumeOpenSerial(openSerial)) return@LaunchedEffect
        groupsVisible = true
        focusCategoryId = Category.ALL_ID
        pendingCategoryFocus = true
    }

    LaunchedEffect(groupsVisible, pendingCategoryFocus, focusCategoryId, state.categories) {
        if (!groupsVisible || !pendingCategoryFocus) return@LaunchedEffect
        val index = state.categories.indexOfFirst { it.id == focusCategoryId }
        if (index >= 0) {
            val visible = categoryListState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty() || visible.none { it.index == index }) {
                categoryListState.scrollToItem(index)
                withTimeoutOrNull(800) {
                    snapshotFlow { categoryListState.layoutInfo.visibleItemsInfo.any { it.index == index } }
                        .first { it }
                }
            }
        }
        repeat(4) { attempt ->
            try {
                categoryFocus.requestFocus()
                pendingCategoryFocus = false
                return@LaunchedEffect
            } catch (_: IllegalStateException) {
                delay(16L * (attempt + 1))
            }
        }
        pendingCategoryFocus = false
    }

    BackHandler {
        if (gridHasFocus || !groupsVisible) {
            showGroups()
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { onActiveState.value(it.hasFocus) }
    ) {
        if (state.error != null) {
            ErrorBanner(state.error!!)
            Spacer(Modifier.height(12.dp))
        }
        when {
            state.isLoading -> LoadingBox("Loading channels…")
            state.channels.isEmpty() -> EmptyState("No live channels", "Live TV has to load before the guide can show programmes.")
            else -> {
                val onSelectCategory: (String) -> Unit = { id ->
                    viewModel.selectCategory(id)
                    hideGroupsAndFocusFirstProgram()
                }
                val grid = @Composable {
                    GuideGrid(
                        state = state,
                        categoryName = state.categories.firstOrNull { it.id == state.selectedCategoryId }?.name.orEmpty(),
                        onEnsureEpg = ensureEpg,
                        onStopFollowingLive = stopFollowingLive,
                        onPlay = playHandler,
                        onPlayFromStart = playFromStartHandler,
                        onProgramFocusChanged = { gridHasFocus = it },
                        onRememberFocus = viewModel::rememberFocus,
                        onSaveViewport = { viewStart, focusTime, channelIndex, channelOffset ->
                            viewModel.saveViewport(
                                viewStartMs = viewStart,
                                focusTimeMs = focusTime,
                                channelIndex = channelIndex,
                                channelOffset = channelOffset,
                                categoryIndex = categoryListState.firstVisibleItemIndex,
                                categoryOffset = categoryListState.firstVisibleItemScrollOffset,
                            )
                        },
                        focusFirstProgramSerial = firstProgramFocusSerial,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (wide) {
                    Row(modifier = Modifier.weight(1f)) {
                        if (groupsVisible) {
                            CategoryRail(
                                categories = state.categories,
                                selectedId = state.selectedCategoryId,
                                focusCategoryId = focusCategoryId,
                                listState = categoryRailState,
                                categoryFocusRequester = categoryFocus,
                                onSelect = onSelectCategory,
                                onMoveLeft = onBack,
                                modifier = Modifier
                                    .width(220.dp)
                                    .fillMaxHeight()
                                    .padding(end = 16.dp)
                            )
                        }
                        Box(Modifier.weight(1f)) { grid() }
                    }
                } else {
                    if (groupsVisible) {
                        Column {
                            CategoryChips(
                                categories = state.categories,
                                selectedId = state.selectedCategoryId,
                                focusCategoryId = focusCategoryId,
                                listState = categoryChipState,
                                categoryFocusRequester = categoryFocus,
                                onSelect = onSelectCategory
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    Box(Modifier.weight(1f)) { grid() }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GuideGrid(
    state: GuideUiState,
    categoryName: String,
    onEnsureEpg: (String, Long, Long) -> Unit,
    onStopFollowingLive: () -> Unit,
    onPlay: (ContentItem, EpgProgram?) -> Unit,
    onPlayFromStart: (ContentItem, EpgProgram) -> Unit,
    onProgramFocusChanged: (Boolean) -> Unit,
    onRememberFocus: (String, String) -> Unit,
    onSaveViewport: (viewStartMs: Long, focusTimeMs: Long, channelIndex: Int, channelOffset: Int) -> Unit,
    focusFirstProgramSerial: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pxPerMinute = with(density) { DpPerMinute.toPx() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.channelScrollIndex,
        initialFirstVisibleItemScrollOffset = state.channelScrollOffset,
    )
    val focusedChannel = remember {
        mutableStateOf(state.channels.find { it.id == state.focusedChannelId })
    }
    val focusedProgram = remember {
        mutableStateOf(
            state.focusedChannelId?.let { channelId ->
                state.epgByChannel[channelId]?.find { programKey(it) == state.focusedProgramKey }
            }
        )
    }
    val programHasFocus = remember {
        mutableStateOf(!state.groupsVisible && state.focusedProgramKey != null)
    }
    val focusTimeMs = remember { mutableLongStateOf(state.focusTimeMs) }
    val viewportPx = remember { mutableFloatStateOf(1f) }
    var pendingFocusChannelId by remember {
        mutableStateOf(state.focusedChannelId.takeIf { !state.groupsVisible })
    }
    var pendingFocusProgramKey by remember {
        mutableStateOf(state.focusedProgramKey.takeIf { !state.groupsVisible })
    }
    var armedChannelId by remember { mutableStateOf(pendingFocusChannelId) }
    var armedProgramKey by remember { mutableStateOf(pendingFocusProgramKey) }

    val channels = state.channels
    val onStopFollowingLiveState = rememberUpdatedState(onStopFollowingLive)
    val onProgramFocusChangedState = rememberUpdatedState(onProgramFocusChanged)
    val onRememberFocusState = rememberUpdatedState(onRememberFocus)
    val onSaveViewportState = rememberUpdatedState(onSaveViewport)
    val viewStartMs = remember { mutableLongStateOf(state.viewStartMs) }
    val focusMemory = remember {
        object {
            var channel: ContentItem? = state.channels.find { it.id == state.focusedChannelId }
            var program: EpgProgram? = state.focusedChannelId?.let { channelId ->
                state.epgByChannel[channelId]?.find { programKey(it) == state.focusedProgramKey }
            }
        }
    }
    DisposableEffect(listState) {
        onDispose {
            val channel = focusedChannel.value ?: focusMemory.channel
            val program = focusedProgram.value ?: focusMemory.program
            if (channel != null && program != null) {
                onRememberFocusState.value(channel.id, programKey(program))
            }
            onSaveViewportState.value(
                viewStartMs.longValue,
                focusTimeMs.longValue,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    fun columnTime(): Long {
        val stored = focusTimeMs.longValue
        return if (stored > 0L) stored else state.nowMs
    }

    fun clearProgramFocus() {
        programHasFocus.value = false
        focusedProgram.value = null
        focusedChannel.value = null
        armedChannelId = null
        armedProgramKey = null
        onProgramFocusChangedState.value(false)
    }

    fun keepColumnOn(program: EpgProgram, pan: TimelinePan) {
        val column = columnTime()
        focusTimeMs.longValue = when (pan) {
            TimelinePan.None -> {
                if (column >= program.startMs && column < program.endMs) column
                else program.startMs
            }
            TimelinePan.Right -> {
                if (column >= program.startMs && column < program.endMs) column
                else program.startMs
            }
            TimelinePan.Left -> {
                if (column >= program.startMs && column < program.endMs) column
                else (program.endMs - 60_000L).coerceAtLeast(program.startMs)
            }
        }
    }

    fun visibleDurationMs(): Long {
        return ((viewportPx.floatValue / pxPerMinute) * 60_000f).toLong().coerceAtLeast(GuideViewModel.SLOT_MS)
    }

    fun windowStartForTime(timeMs: Long): Long {
        return timeMs - (visibleDurationMs() * LiveNowViewportFraction).toLong()
    }

    fun displayedViewStart(): Long {
        return if (state.followLive) {
            state.nowMs - (visibleDurationMs() * LiveNowViewportFraction).toLong()
        } else {
            viewStartMs.longValue
        }
    }

    fun expandFocusedProgramLeft(program: EpgProgram): Boolean {
        val visStart = displayedViewStart()
        val hiddenMs = visStart - program.startMs
        if (hiddenMs <= 0L) return false
        val shift = if (hiddenMs > TimelineRevealHourMs) TimelineRevealHourMs else hiddenMs
        viewStartMs.longValue = visStart - shift
        onStopFollowingLiveState.value()
        return true
    }

    fun expandFocusedProgramRight(program: EpgProgram): Boolean {
        val visStart = displayedViewStart()
        val visEnd = visStart + visibleDurationMs()
        val hiddenMs = program.endMs - visEnd
        if (hiddenMs <= 0L) return false
        val shift = if (hiddenMs > TimelineRevealHourMs) TimelineRevealHourMs else hiddenMs
        viewStartMs.longValue = visStart + shift
        onStopFollowingLiveState.value()
        return true
    }

    fun nudgeTimeline(program: EpgProgram, pan: TimelinePan) {
        if (pan == TimelinePan.Left) {
            val visStart = displayedViewStart()
            if (program.startMs >= visStart) return
            val duration = (program.endMs - program.startMs).coerceAtLeast(0L)
            val shownMs = if (duration > TimelineRevealHourMs) TimelineRevealHourMs else duration
            val target = (program.endMs - shownMs).coerceAtLeast(program.startMs)
            viewStartMs.longValue = minOf(visStart, target)
            return
        }
        val visStart = displayedViewStart()
        val visible = visibleDurationMs()
        val visEnd = visStart + visible
        if (program.startMs >= visStart && program.endMs <= visEnd) return
        val duration = (program.endMs - program.startMs).coerceAtLeast(0L)
        val target = when {
            duration <= TimelineRevealHourMs && duration <= visible -> program.endMs - visible
            duration <= TimelineRevealHourMs -> program.startMs
            else -> program.startMs + TimelineRevealHourMs - visible
        }
        viewStartMs.longValue = maxOf(visStart, target)
    }

    fun selectProgram(channel: ContentItem, program: EpgProgram, pan: TimelinePan) {
        if (viewStartMs.longValue == 0L) {
            viewStartMs.longValue = windowStartForTime(state.nowMs)
        }
        programHasFocus.value = true
        onProgramFocusChangedState.value(true)
        focusedChannel.value = channel
        focusedProgram.value = program
        focusMemory.channel = channel
        focusMemory.program = program
        pendingFocusChannelId = channel.id
        pendingFocusProgramKey = programKey(program)
        onStopFollowingLiveState.value()
        keepColumnOn(program, pan)
        if (pan != TimelinePan.None) nudgeTimeline(program, pan)
    }

    fun keepFocusedChannelInWindow(channelIndex: Int) {
        scope.launch {
            withFrameNanos { }
            val layout = listState.layoutInfo
            val visible = layout.visibleItemsInfo
            val maxAbove = 3
            if (visible.isEmpty()) {
                listState.scrollToItem((channelIndex - maxAbove).coerceAtLeast(0))
                return@launch
            }
            val viewportStart = layout.viewportStartOffset
            val viewportEnd = layout.viewportEndOffset
            val fullyVisible = visible.count {
                it.offset >= viewportStart && it.offset + it.size <= viewportEnd
            }.coerceAtLeast(1)
            val lastIndex = (layout.totalItemsCount - 1).coerceAtLeast(0)
            val maxFirst = (lastIndex - fullyVisible + 1).coerceAtLeast(0)
            val targetFirst = (channelIndex - maxAbove).coerceIn(0, maxFirst)
            if (targetFirst != listState.firstVisibleItemIndex ||
                listState.firstVisibleItemScrollOffset != 0
            ) {
                listState.scrollToItem(targetFirst)
            }
        }
    }

    fun moveVertical(fromChannel: ContentItem, delta: Int, repeating: Boolean): Boolean {
        val index = channels.indexOfFirst { it.id == fromChannel.id }
        if (index < 0) return false
        val nextIndex = index + delta
        val next = channels.getOrNull(nextIndex)
        if (next == null) {
            if (repeating || channels.size <= 1) return true
            val wrapIndex = if (delta > 0) 0 else channels.lastIndex
            val wrapChannel = channels[wrapIndex]
            val program = programAt(
                listingsAround(state.epgByChannel[wrapChannel.id], columnTime()),
                columnTime(),
            ) ?: return true
            selectProgram(wrapChannel, program, TimelinePan.None)
            keepFocusedChannelInWindow(wrapIndex)
            return true
        }
        val program = programAt(
            listingsAround(state.epgByChannel[next.id], columnTime()),
            columnTime(),
        ) ?: return true
        selectProgram(next, program, TimelinePan.None)
        keepFocusedChannelInWindow(nextIndex)
        return true
    }

    val onProgramFocused = remember<(ContentItem, EpgProgram, FocusRequester) -> Unit> {
        { channel, program, requester ->
            programHasFocus.value = true
            onProgramFocusChangedState.value(true)
            onStopFollowingLiveState.value()
            focusedChannel.value = channel
            focusedProgram.value = program
            focusMemory.channel = channel
            focusMemory.program = program
            armedChannelId = channel.id
            armedProgramKey = programKey(program)
            val column = focusTimeMs.longValue
            if (column == 0L || column < program.startMs || column >= program.endMs) {
                focusTimeMs.longValue = program.startMs
            }
        }
    }
    val onProgramUnfocused = remember<(ContentItem, EpgProgram) -> Unit> {
        { channel, program ->
            val current = focusedProgram.value
            val currentChannel = focusedChannel.value
            if (current?.id == program.id &&
                current.startMs == program.startMs &&
                currentChannel?.id == channel.id
            ) {
                clearProgramFocus()
            }
        }
    }
    val selectProgramHandler = rememberUpdatedState { channel: ContentItem, program: EpgProgram, pan: TimelinePan ->
        selectProgram(channel, program, pan)
    }
    val moveVerticalHandler = rememberUpdatedState { channel: ContentItem, delta: Int, repeating: Boolean ->
        moveVertical(channel, delta, repeating)
    }
    val onSelectProgram = remember<(ContentItem, EpgProgram, TimelinePan) -> Unit> {
        { channel, program, pan -> selectProgramHandler.value(channel, program, pan) }
    }
    val onMoveVertical = remember<(ContentItem, Int, Boolean) -> Boolean> {
        { channel, delta, repeating -> moveVerticalHandler.value(channel, delta, repeating) }
    }

    LaunchedEffect(state.selectedCategoryId) {
        focusedProgram.value = null
        focusedChannel.value = null
    }

    val restoreChannelId = remember { state.focusedChannelId.takeIf { !state.groupsVisible } }
    LaunchedEffect(state.focusedChannelId, state.focusedProgramKey, state.groupsVisible) {
        if (state.groupsVisible || pendingFocusChannelId != null) return@LaunchedEffect
        val channelId = state.focusedChannelId ?: return@LaunchedEffect
        val programKey = state.focusedProgramKey ?: return@LaunchedEffect
        pendingFocusChannelId = channelId
        pendingFocusProgramKey = programKey
    }
    LaunchedEffect(restoreChannelId, channels) {
        val id = restoreChannelId ?: return@LaunchedEffect
        val index = channels.indexOfFirst { it.id == id }
        if (index < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || visible.none { it.index == index }) {
            listState.scrollToItem((index - 3).coerceAtLeast(0))
        }
    }

    LaunchedEffect(focusFirstProgramSerial, channels) {
        if (focusFirstProgramSerial == 0) return@LaunchedEffect
        val first = channels.firstOrNull() ?: return@LaunchedEffect
        listState.scrollToItem(0)
        val listings = listingsAround(state.epgByChannel[first.id], columnTime())
        val program = programAt(listings, columnTime()) ?: listings.firstOrNull() ?: return@LaunchedEffect
        selectProgram(first, program, TimelinePan.None)
    }

    LaunchedEffect(state.epgByChannel, state.loadingEpgIds, channels) {
        val channel = focusedChannel.value ?: return@LaunchedEffect
        val current = focusedProgram.value
        val stillVisible = channels.any { it.id == channel.id }
        if (!stillVisible) {
            val first = channels.firstOrNull() ?: run {
                clearProgramFocus()
                return@LaunchedEffect
            }
            listState.scrollToItem(0)
            val listings = listingsAround(state.epgByChannel[first.id], columnTime())
            val program = programAt(listings, columnTime()) ?: listings.firstOrNull() ?: return@LaunchedEffect
            selectProgram(first, program, TimelinePan.None)
            return@LaunchedEffect
        }
        if (current == null || !current.placeholder) return@LaunchedEffect
        if (state.loadingEpgIds.contains(channel.id)) return@LaunchedEffect
        val next = programAt(
            listingsAround(state.epgByChannel[channel.id], columnTime()),
            columnTime(),
        ) ?: return@LaunchedEffect
        if (next.placeholder) return@LaunchedEffect
        if (next.id == current.id && next.startMs == current.startMs) return@LaunchedEffect
        selectProgram(channel, next, TimelinePan.None)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val timelineViewportPx = with(density) {
            val available = if (maxWidth == Dp.Infinity || !maxWidth.value.isFinite()) {
                LocalConfiguration.current.screenWidthDp.dp
            } else {
                maxWidth
            }
            (available - ChannelColumnWidth - 12.dp).toPx().coerceIn(1f, MaxGuideSpanPx.toFloat())
        }
        viewportPx.floatValue = timelineViewportPx
        val followStart = state.nowMs - (visibleDurationMs() * LiveNowViewportFraction).toLong()
        val visibleStartMs = if (state.followLive) followStart else viewStartMs.longValue
        val visibleEndMs = visibleStartMs + visibleDurationMs()

        LaunchedEffect(state.followLive, state.nowMs, state.liveJumpSerial, timelineViewportPx) {
            if (!state.followLive) return@LaunchedEffect
            focusTimeMs.longValue = 0L
            viewStartMs.longValue = state.nowMs - (visibleDurationMs() * LiveNowViewportFraction).toLong()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TimeHeader(
                categoryName = categoryName,
                nowMs = state.nowMs,
                visibleStartMs = visibleStartMs,
                visibleEndMs = visibleEndMs,
            )
            Spacer(Modifier.height(8.dp))
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoAnimatedBringIntoView) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(channels, key = { it.id }, contentType = { 0 }) { channel ->
                        ChannelRow(
                            channel = channel,
                            programs = state.epgByChannel[channel.id],
                            isLoadingEpg = state.loadingEpgIds.contains(channel.id),
                            timelineStartMs = visibleStartMs - ViewportBufferMs,
                            timelineEndMs = visibleEndMs + ViewportBufferMs,
                            nowMs = state.nowMs,
                            visibleStartMs = visibleStartMs,
                            visibleEndMs = visibleEndMs,
                            pendingFocusKey = pendingFocusProgramKey.takeIf { pendingFocusChannelId == channel.id },
                            armedFocusKey = armedProgramKey.takeIf { armedChannelId == channel.id },
                            onEnsureEpg = onEnsureEpg,
                            onProgramFocused = onProgramFocused,
                            onProgramUnfocused = onProgramUnfocused,
                            onSelectProgram = onSelectProgram,
                            onExpandProgramLeft = { program -> expandFocusedProgramLeft(program) },
                            onExpandProgramRight = { program -> expandFocusedProgramRight(program) },
                            onMoveVertical = onMoveVertical,
                            onPlay = onPlay,
                            onPlayFromStart = onPlayFromStart,
                        )
                    }
                }
            }
            GuideNowStrip(
                programHasFocus = programHasFocus,
                focusedProgram = focusedProgram,
                focusedChannel = focusedChannel,
                nowMs = state.nowMs,
            )
        }
    }
}

@Composable
private fun GuideNowStrip(
    programHasFocus: MutableState<Boolean>,
    focusedProgram: MutableState<EpgProgram?>,
    focusedChannel: MutableState<ContentItem?>,
    nowMs: Long,
) {
    val program = focusedProgram.value
    if (!programHasFocus.value || program == null) return
    val catchup = focusedChannel.value?.hasCatchupFor(program, nowMs) == true
    Spacer(Modifier.height(6.dp))
    GuideInfoStrip(program = program, catchup = catchup)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideInfoStrip(
    program: EpgProgram?,
    catchup: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleStyle = MaterialTheme.typography.titleSmall
    val bodyStyle = MaterialTheme.typography.bodySmall
    val stripHeight = with(LocalDensity.current) {
        InfoStripVerticalPadding * 2 +
            InfoStripGap +
            titleStyle.lineHeight.toDp() +
            bodyStyle.lineHeight.toDp() * 3
    }
    val header = if (program == null) {
        ""
    } else {
        listOfNotNull(
            "${formatTime(program.startMs)} – ${formatTime(program.endMs)}",
            program.title,
            if (catchup) "Catch up" else null,
        ).joinToString("  ·  ")
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(stripHeight)
            .background(NightSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = InfoStripVerticalPadding)
    ) {
        Text(
            text = header,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = program?.description.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = bodyStyle,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = InfoStripGap)
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TimeHeader(
    categoryName: String,
    nowMs: Long,
    visibleStartMs: Long,
    visibleEndMs: Long,
) {
    val ticks = remember(visibleStartMs, visibleEndMs) {
        headerTicks(visibleStartMs, visibleEndMs, visibleStartMs, visibleEndMs).map { slot ->
            slot to slotLabel(slot)
        }
    }
    val slotWidth = DpPerMinute * (GuideViewModel.SLOT_MS / 60_000f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(ChannelColumnWidth)
                .height(28.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (categoryName.isNotBlank()) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleSmall,
                    color = AccentBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(CellShape)
        ) {
            Box(Modifier.fillMaxSize()) {
                ticks.forEach { (slot, label) ->
                    key(slot) {
                        val x = DpPerMinute * ((slot - visibleStartMs) / 60_000f)
                        Box(
                            modifier = Modifier
                                .offset(x = x)
                                .width(slotWidth)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                if (nowMs in (visibleStartMs - GuideViewModel.SLOT_MS)..(visibleEndMs + GuideViewModel.SLOT_MS)) {
                    val nowX = DpPerMinute * ((nowMs - visibleStartMs) / 60_000f)
                    Box(
                        modifier = Modifier
                            .offset(x = nowX)
                            .width(NowLineWidth)
                            .fillMaxHeight()
                            .background(AccentTeal)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(
    channel: ContentItem,
    programs: List<EpgProgram>?,
    isLoadingEpg: Boolean,
    timelineStartMs: Long,
    timelineEndMs: Long,
    nowMs: Long,
    visibleStartMs: Long,
    visibleEndMs: Long,
    pendingFocusKey: String?,
    armedFocusKey: String?,
    onEnsureEpg: (String, Long, Long) -> Unit,
    onProgramFocused: (ContentItem, EpgProgram, FocusRequester) -> Unit,
    onProgramUnfocused: (ContentItem, EpgProgram) -> Unit,
    onSelectProgram: (ContentItem, EpgProgram, TimelinePan) -> Unit,
    onExpandProgramLeft: (EpgProgram) -> Boolean,
    onExpandProgramRight: (EpgProgram) -> Boolean,
    onMoveVertical: (ContentItem, Int, Boolean) -> Boolean,
    onPlay: (ContentItem, EpgProgram?) -> Unit,
    onPlayFromStart: (ContentItem, EpgProgram) -> Unit,
) {
    val channelRequester = remember { FocusRequester() }
    LaunchedEffect(channel.id, visibleStartMs, visibleEndMs) {
        onEnsureEpg(channel.id, visibleStartMs - ViewportBufferMs, visibleEndMs + ViewportBufferMs)
    }
    val listings = remember(
        programs,
        isLoadingEpg,
        visibleStartMs,
        visibleEndMs,
        timelineStartMs,
        timelineEndMs,
    ) {
        val viewStart = maxOf(timelineStartMs, visibleStartMs - ViewportBufferMs)
        val viewEnd = minOf(timelineEndMs, visibleEndMs + ViewportBufferMs)
        withPlaceholders(
            programs = if (isLoadingEpg) null else programs,
            timelineStartMs = viewStart,
            timelineEndMs = viewEnd,
        )
    }
    val drawStartMs = visibleStartMs - ViewportBufferMs
    val drawEndMs = visibleEndMs + ViewportBufferMs
    val visiblePrograms = remember(listings, drawStartMs, drawEndMs, timelineStartMs, timelineEndMs) {
        programsAroundViewport(
            programs = listings,
            viewStartMs = drawStartMs,
            viewEndMs = drawEndMs,
            timelineStartMs = timelineStartMs,
            timelineEndMs = timelineEndMs,
        )
    }
    val density = LocalDensity.current
    val pxPerMinute = with(density) { DpPerMinute.toPx() }
    Row(
        modifier = Modifier.fillMaxWidth().height(RowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(ChannelColumnWidth)
                .fillMaxHeight()
                .padding(1.dp)
                .clip(CellShape)
                .background(GuideCellIdle),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    channel.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                if (channel.tvArchive && channel.tvArchiveDurationDays > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${channel.tvArchiveDurationDays}d",
                        color = AccentTeal,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(CellShape)
                .background(NightSurfaceAlt)
        ) {
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                visiblePrograms.forEach { program ->
                            val sliceStart = maxOf(program.startMs, drawStartMs)
                            val sliceEnd = minOf(program.endMs, drawEndMs)
                            if (sliceEnd <= sliceStart) return@forEach
                            val startPx = timelineOffsetPx(sliceStart, visibleStartMs, pxPerMinute)
                            val endPx = timelineOffsetPx(sliceEnd, visibleStartMs, pxPerMinute)
                            val widthPx = (endPx - startPx).coerceAtLeast(0)
                            if (widthPx <= 0) return@forEach
                            val cellPadPx = with(density) { 1.dp.roundToPx() }
                            val textInsetPx = (-startPx - cellPadPx).coerceAtLeast(0)
                            val pastFraction = slicePastFraction(sliceStart, sliceEnd, nowMs)
                            key(program.id, program.startMs) {
                                val cellKey = programKey(program)
                                val cellModifier = Modifier
                                    .timelineSlot(startPx, widthPx)
                                    .fillMaxHeight()
                                    .clipToBounds()
                                    .padding(1.dp)
                                val watchFromStart = if (
                                    !program.placeholder &&
                                    program.isPlayingAt(nowMs) &&
                                    channel.hasCatchupFor(program, nowMs)
                                ) {
                                    { onPlayFromStart(channel, program) }
                                } else {
                                    null
                                }
                                val onClick = {
                                    if (program.placeholder) {
                                        onPlay(channel, null)
                                    } else if (program.isPlayingAt(nowMs) || channel.hasCatchupFor(program, nowMs)) {
                                        onPlay(channel, program)
                                    } else {
                                        onPlay(channel, null)
                                    }
                                }
                                if (cellKey == pendingFocusKey || cellKey == armedFocusKey) {
                                    ProgramCell(
                                        program = program,
                                        programKey = cellKey,
                                        pendingFocusKey = pendingFocusKey,
                                        pastFraction = pastFraction,
                                        catchup = channel.hasCatchupFor(program, nowMs),
                                        onFocused = { onProgramFocused(channel, program, channelRequester) },
                                        onUnfocused = { onProgramUnfocused(channel, program) },
                                        onMoveLeft = {
                                            if (!onExpandProgramLeft(program)) {
                                                val previous = neighborProgram(listings, program, -1)
                                                if (previous != null) {
                                                    onSelectProgram(channel, previous, TimelinePan.Left)
                                                }
                                            }
                                            true
                                        },
                                        onMoveRight = {
                                            if (!onExpandProgramRight(program)) {
                                                val next = neighborProgram(listings, program, 1)
                                                    ?: programmeAfter(programs, program)
                                                if (next != null) {
                                                    onSelectProgram(channel, next, TimelinePan.Right)
                                                }
                                            }
                                            true
                                        },
                                        onMoveUp = { repeating -> onMoveVertical(channel, -1, repeating) },
                                        onMoveDown = { repeating -> onMoveVertical(channel, 1, repeating) },
                                        onClick = onClick,
                                        onWatchFromStart = watchFromStart,
                                        textInsetPx = textInsetPx,
                                        modifier = cellModifier,
                                    )
                                } else {
                                    StaticProgramCell(
                                        program = program,
                                        pastFraction = pastFraction,
                                        catchup = channel.hasCatchupFor(program, nowMs),
                                        onClick = onClick,
                                        onWatchFromStart = watchFromStart,
                                        textInsetPx = textInsetPx,
                                        modifier = cellModifier,
                                    )
                                }
                            }
                }
                if (nowMs in (visibleStartMs - GuideViewModel.SLOT_MS)..(visibleEndMs + GuideViewModel.SLOT_MS)) {
                    val nowXPx = timelineOffsetPx(nowMs, visibleStartMs, pxPerMinute)
                    val nowWidthPx = with(density) { NowLineWidth.roundToPx() }.coerceAtLeast(1)
                    Box(
                        modifier = Modifier
                            .timelineSlot(nowXPx, nowWidthPx)
                            .fillMaxHeight()
                            .background(AccentTeal)
                    )
                }
                },
            ) { measurables, constraints ->
                val timelineWidth = constraints.maxWidth.coerceAtLeast(0)
                val timelineHeight = constraints.maxHeight.coerceAtLeast(0)
                val placed = measurables.map { measurable ->
                    val slot = measurable.parentData as? TimelineSlot
                    if (slot == null || slot.widthPx <= 0 || timelineHeight <= 0) {
                        measurable.measure(Constraints.fixed(0, timelineHeight.coerceAtLeast(0))) to 0
                    } else {
                        measurable.measure(Constraints.fixed(slot.widthPx, timelineHeight)) to slot.xPx
                    }
                }
                layout(timelineWidth, timelineHeight) {
                    placed.forEach { (placeable, xPx) ->
                        placeable.place(xPx, 0)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StaticProgramCell(
    program: EpgProgram,
    pastFraction: Float,
    catchup: Boolean,
    onClick: () -> Unit,
    onWatchFromStart: (() -> Unit)?,
    textInsetPx: Int = 0,
    modifier: Modifier = Modifier,
) {
    if (onWatchFromStart != null) {
        CatchupStaticCell(
            program = program,
            pastFraction = pastFraction,
            catchup = catchup,
            onClick = onClick,
            onWatchFromStart = onWatchFromStart,
            textInsetPx = textInsetPx,
            modifier = modifier,
        )
    } else {
        PlainProgramCell(
            title = program.title,
            pastFraction = pastFraction,
            catchup = catchup,
            onClick = onClick,
            textInsetPx = textInsetPx,
            modifier = modifier,
        )
    }
}

@Composable
private fun PlainProgramCell(
    title: String,
    pastFraction: Float,
    catchup: Boolean,
    onClick: () -> Unit,
    textInsetPx: Int = 0,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    ProgramFace(
        title = title,
        focused = false,
        pastFraction = pastFraction,
        catchup = catchup,
        textInsetPx = textInsetPx,
        modifier = modifier
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatchupStaticCell(
    program: EpgProgram,
    pastFraction: Float,
    catchup: Boolean,
    onClick: () -> Unit,
    onWatchFromStart: () -> Unit,
    textInsetPx: Int = 0,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    var menuOpen by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }
    val onWatchFromStartState = rememberUpdatedState(onWatchFromStart)
    LaunchedEffect(menuOpen) {
        if (!menuOpen) return@LaunchedEffect
        repeat(4) { attempt ->
            try {
                menuFocus.requestFocus()
                return@LaunchedEffect
            } catch (_: IllegalStateException) {
                delay(16L * (attempt + 1))
            }
        }
    }
    ProgramFace(
        title = program.title,
        focused = false,
        pastFraction = pastFraction,
        catchup = catchup,
        textInsetPx = textInsetPx,
        modifier = modifier
            .focusProperties { canFocus = false }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = { menuOpen = true },
            ),
        overlay = {
            if (menuOpen) {
                ProgramActionMenu(
                    title = program.title,
                    menuFocus = menuFocus,
                    onWatchLive = {
                        menuOpen = false
                        onClick()
                    },
                    onWatchFromStart = {
                        menuOpen = false
                        onWatchFromStartState.value()
                    },
                    onDismiss = { menuOpen = false },
                )
            }
        },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProgramFace(
    title: String,
    focused: Boolean,
    pastFraction: Float,
    catchup: Boolean,
    textInsetPx: Int = 0,
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit = {},
) {
    val base = when {
        focused -> GuideCellFocused
        pastFraction >= 1f -> GuideCellPast
        else -> GuideCellIdle
    }
    Box(
        modifier = modifier
            .clip(CellShape)
            .background(base),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (!focused && pastFraction > 0f && pastFraction < 1f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pastFraction)
                    .background(GuideCellPast)
            )
        }
        val textInset = with(LocalDensity.current) { textInsetPx.toDp() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp + textInset, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (catchup) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AccentTeal)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
        }
        overlay()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ProgramCell(
    program: EpgProgram,
    programKey: String,
    pendingFocusKey: String?,
    pastFraction: Float,
    catchup: Boolean,
    onFocused: () -> Unit,
    onUnfocused: () -> Unit,
    onMoveLeft: () -> Boolean,
    onMoveRight: () -> Boolean,
    onMoveUp: (repeating: Boolean) -> Boolean,
    onMoveDown: (repeating: Boolean) -> Boolean,
    onClick: () -> Unit,
    onWatchFromStart: (() -> Unit)? = null,
    textInsetPx: Int = 0,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val requester = remember { FocusRequester() }
    val onFocusedState = rememberUpdatedState(onFocused)
    val onUnfocusedState = rememberUpdatedState(onUnfocused)
    val onWatchFromStartState = rememberUpdatedState(onWatchFromStart)
    var hadFocus by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }
    LaunchedEffect(pendingFocusKey, programKey) {
        if (pendingFocusKey != programKey) return@LaunchedEffect
        repeat(4) { attempt ->
            try {
                requester.requestFocus()
                return@LaunchedEffect
            } catch (_: IllegalStateException) {
                delay(16L * (attempt + 1))
            }
        }
    }
    LaunchedEffect(focused, program.id, program.startMs) {
        if (focused) {
            hadFocus = true
            onFocusedState.value()
        } else if (hadFocus) {
            hadFocus = false
            onUnfocusedState.value()
        }
    }
    val openMenu = {
        if (onWatchFromStartState.value != null) menuOpen = true
    }
    LaunchedEffect(menuOpen) {
        if (!menuOpen) return@LaunchedEffect
        repeat(4) { attempt ->
            try {
                menuFocus.requestFocus()
                return@LaunchedEffect
            } catch (_: IllegalStateException) {
                delay(16L * (attempt + 1))
            }
        }
    }
    ProgramFace(
        title = program.title,
        focused = focused,
        pastFraction = pastFraction,
        catchup = catchup,
        textInsetPx = textInsetPx,
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocusedState.value() }
            .then(
                if (onWatchFromStart != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                        onLongClick = openMenu,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                }
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> onMoveRight()
                    Key.DirectionLeft -> onMoveLeft()
                    Key.DirectionUp -> onMoveUp(event.isHeldRepeat())
                    Key.DirectionDown -> onMoveDown(event.isHeldRepeat())
                    Key.Menu -> {
                        openMenu()
                        onWatchFromStart != null
                    }
                    else -> false
                }
            },
        overlay = {
            if (menuOpen && onWatchFromStart != null) {
                ProgramActionMenu(
                    title = program.title,
                    menuFocus = menuFocus,
                    onWatchLive = {
                        menuOpen = false
                        onClick()
                    },
                    onWatchFromStart = {
                        menuOpen = false
                        onWatchFromStart()
                    },
                    onDismiss = { menuOpen = false },
                )
            }
        },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProgramActionMenu(
    title: String,
    menuFocus: FocusRequester,
    onWatchLive: () -> Unit,
    onWatchFromStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    var swallowOpeningPress by remember { mutableStateOf(true) }
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, with(density) { 36.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = true,
        ),
    ) {
        BackHandler(onBack = onDismiss)
        Column(
            modifier = Modifier
                .widthIn(min = 220.dp, max = 280.dp)
                .onPreviewKeyEvent { event ->
                    // TV Surface activates on the centre key-up. The long-press that opened
                    // this menu is still held, so that release would select the focused row.
                    val center = event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter ||
                        event.key == Key.Spacebar
                    if (center && swallowOpeningPress) {
                        val freshPress = event.type == KeyEventType.KeyDown &&
                            event.nativeKeyEvent.repeatCount == 0
                        if (freshPress) {
                            swallowOpeningPress = false
                            return@onPreviewKeyEvent false
                        }
                        if (event.type == KeyEventType.KeyUp) swallowOpeningPress = false
                        return@onPreviewKeyEvent true
                    }
                    if (event.key != Key.Back && event.key != Key.Escape) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                }
                .background(NightSurface, RoundedCornerShape(12.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
            Surface(
                onClick = onWatchFromStart,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = CategorySelected,
                    focusedContentColor = MaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                shape = ClickableSurfaceDefaults.shape(CategoryShape),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(menuFocus)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Watch from start", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Surface(
                onClick = onWatchLive,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = CategorySelected,
                    focusedContentColor = MaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                shape = ClickableSurfaceDefaults.shape(CategoryShape),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Watch live", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRail(
    categories: List<Category>,
    selectedId: String,
    focusCategoryId: String,
    listState: LazyListState,
    categoryFocusRequester: FocusRequester? = null,
    onSelect: (String) -> Unit,
    onMoveLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wrapState = rememberListWrapState()
    LazyColumn(
        state = listState,
        modifier = modifier.background(NightSurface, RoundedCornerShape(16.dp)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            CategoryItem(
                category = category,
                selected = category.id == selectedId,
                fillWidth = true,
                focusRequester = if (category.id == focusCategoryId) categoryFocusRequester else null,
                wrapModifier = Modifier.wrapListNavigation(
                    index = index,
                    count = categories.size,
                    state = wrapState,
                    scrollTo = { listState.scrollToItem(it) },
                    wrapVertical = true,
                    wrapHorizontal = false,
                ),
                onMoveLeft = onMoveLeft,
                onMoveRight = { onSelect(category.id) },
            ) { onSelect(category.id) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryChips(
    categories: List<Category>,
    selectedId: String,
    focusCategoryId: String,
    listState: LazyListState,
    categoryFocusRequester: FocusRequester? = null,
    onSelect: (String) -> Unit,
) {
    val wrapState = rememberListWrapState()
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            CategoryItem(
                category = category,
                selected = category.id == selectedId,
                fillWidth = false,
                focusRequester = if (category.id == focusCategoryId) categoryFocusRequester else null,
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
    focusRequester: FocusRequester? = null,
    wrapModifier: Modifier = Modifier,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val background = when {
        focused -> CategoryFocused
        selected -> CategorySelected
        else -> Color.Transparent
    }
    val moveLeft = rememberUpdatedState(onMoveLeft)
    val moveRight = rememberUpdatedState(onMoveRight)
    Box(
        modifier = Modifier
            .then(
                if (onMoveLeft != null || onMoveRight != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        val action = when (event.key) {
                            Key.DirectionLeft -> moveLeft.value
                            Key.DirectionRight -> moveRight.value
                            else -> null
                        }
                        if (action == null) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown && !event.isHeldRepeat()) {
                            action()
                        }
                        true
                    }
                } else {
                    Modifier
                }
            )
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(wrapModifier)
            .clip(CategoryShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
    ) {
        Text(
            text = category.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun over(base: Color, overlay: Color): Color {
    val alpha = overlay.alpha
    if (alpha >= 1f) return overlay
    if (alpha <= 0f) return base
    val keep = 1f - alpha
    return Color(
        red = overlay.red * alpha + base.red * keep,
        green = overlay.green * alpha + base.green * keep,
        blue = overlay.blue * alpha + base.blue * keep,
        alpha = 1f,
    )
}

private fun slicePastFraction(sliceStart: Long, sliceEnd: Long, nowMs: Long): Float {
    if (sliceEnd <= sliceStart) return 0f
    if (nowMs >= sliceEnd) return 1f
    if (nowMs <= sliceStart) return 0f
    return ((nowMs - sliceStart).toFloat() / (sliceEnd - sliceStart).toFloat()).coerceIn(0f, 1f)
}

private fun programKey(program: EpgProgram): String = "${program.id}-${program.startMs}"

private fun withPlaceholders(
    programs: List<EpgProgram>?,
    timelineStartMs: Long,
    timelineEndMs: Long,
): List<EpgProgram> {
    val filled = ArrayList<EpgProgram>()
    var cursor = timelineStartMs
    val sorted = programs.orEmpty().let { list ->
        if (list.size <= 1) list
        else {
            var ordered = true
            for (index in 1 until list.size) {
                if (list[index].startMs < list[index - 1].startMs) {
                    ordered = false
                    break
                }
            }
            if (ordered) list else list.sortedBy { it.startMs }
        }
    }
    val hourCal = Calendar.getInstance()
    for (program in sorted) {
        val start = maxOf(program.startMs, timelineStartMs)
        val end = minOf(program.endMs, timelineEndMs)
        if (end <= start) continue
        if (start > cursor) {
            addHourPlaceholders(filled, cursor, start, hourCal)
        }
        filled += program
        cursor = maxOf(cursor, end)
    }
    if (cursor < timelineEndMs) {
        addHourPlaceholders(filled, cursor, timelineEndMs, hourCal)
    }
    if (filled.isEmpty()) {
        addHourPlaceholders(filled, timelineStartMs, timelineEndMs, hourCal)
    }
    return filled
}

private fun addHourPlaceholders(
    into: MutableList<EpgProgram>,
    fromMs: Long,
    toMs: Long,
    calendar: Calendar,
) {
    var cursor = fromMs
    while (cursor < toMs) {
        val end = minOf(nextLocalHourMs(calendar, cursor), toMs)
        if (end <= cursor) break
        into += placeholderProgram(cursor, end)
        cursor = end
    }
}

private fun nextLocalHourMs(calendar: Calendar, timeMs: Long): Long {
    calendar.timeInMillis = timeMs
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar.add(Calendar.HOUR_OF_DAY, 1)
    return calendar.timeInMillis
}

private fun placeholderProgram(startMs: Long, endMs: Long): EpgProgram {
    return EpgProgram(
        id = "placeholder:$startMs",
        title = "No information",
        description = "",
        startMs = startMs,
        endMs = endMs,
        placeholder = true,
    )
}

private fun programAt(programs: List<EpgProgram>?, timeMs: Long): EpgProgram? {
    if (programs.isNullOrEmpty()) return null
    return programs.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs }
        ?: programs.lastOrNull { it.endMs <= timeMs }
        ?: programs.firstOrNull { it.startMs > timeMs }
}

private fun neighborProgram(programs: List<EpgProgram>?, current: EpgProgram, direction: Int): EpgProgram? {
    if (programs.isNullOrEmpty()) return null
    val index = programs.indexOfFirst { it.id == current.id && it.startMs == current.startMs }
    if (index < 0) return null
    return programs.getOrNull(index + direction)
}

private fun programmeAfter(programs: List<EpgProgram>?, current: EpgProgram): EpgProgram? {
    if (programs.isNullOrEmpty()) return null
    val index = programs.indexOfFirst { it.id == current.id && it.startMs == current.startMs }
    if (index >= 0) return programs.getOrNull(index + 1)
    return programs.firstOrNull { it.startMs >= current.endMs }
}

private fun listingsAround(programs: List<EpgProgram>?, timeMs: Long): List<EpgProgram> {
    val pad = 2L * 60L * 60L * 1000L
    return withPlaceholders(programs, timeMs - pad, timeMs + pad)
}

private data class TimelineSlot(val xPx: Int, val widthPx: Int)

private class TimelineSlotModifier(private val xPx: Int, private val widthPx: Int) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = TimelineSlot(xPx, widthPx)
}

private fun Modifier.timelineSlot(xPx: Int, widthPx: Int): Modifier =
    this.then(TimelineSlotModifier(xPx, widthPx))

private fun timelineOffsetPx(timeMs: Long, visibleStartMs: Long, pxPerMinute: Float): Int {
    val px = (timeMs - visibleStartMs) / 60_000.0 * pxPerMinute
    if (!px.isFinite()) return 0
    return px.roundToInt().coerceIn(-MaxTimelinePx, MaxTimelinePx)
}

private fun programsAroundViewport(
    programs: List<EpgProgram>?,
    viewStartMs: Long,
    viewEndMs: Long,
    timelineStartMs: Long,
    timelineEndMs: Long,
): List<EpgProgram> {
    if (programs.isNullOrEmpty()) return emptyList()
    val startBound = maxOf(viewStartMs, timelineStartMs)
    val endBound = minOf(viewEndMs, timelineEndMs)
    var first = -1
    var last = -1
    for (index in programs.indices) {
        val program = programs[index]
        if (program.endMs > startBound && program.startMs < endBound) {
            if (first < 0) first = index
            last = index
        } else if (first >= 0) {
            break
        }
    }
    if (first < 0) {
        var nearest = 0
        var best = Long.MAX_VALUE
        for (index in programs.indices) {
            val program = programs[index]
            val distance = when {
                program.endMs <= startBound -> startBound - program.endMs
                program.startMs >= endBound -> program.startMs - endBound
                else -> 0L
            }
            if (distance < best) {
                best = distance
                nearest = index
            }
        }
        first = nearest
        last = nearest
    }
    val from = (first - 1).coerceAtLeast(0)
    val to = (last + 1).coerceAtMost(programs.lastIndex)
    return programs.subList(from, to + 1)
}

private fun headerTicks(
    timelineStartMs: Long,
    timelineEndMs: Long,
    visibleStartMs: Long,
    visibleEndMs: Long,
): List<Long> {
    val slot = GuideViewModel.SLOT_MS
    val first = timelineStartMs +
        (((visibleStartMs - slot - timelineStartMs).coerceAtLeast(0L) / slot) * slot)
    val last = (visibleEndMs + slot).coerceAtMost(timelineEndMs)
    val ticks = ArrayList<Long>(20)
    var time = first.coerceAtLeast(timelineStartMs)
    while (time < last) {
        ticks += time
        time += slot
        if (ticks.size >= 64) break
    }
    return ticks
}

private val shortTimeFormat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
private val mediumDateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
private val labelCalendar: Calendar = Calendar.getInstance()
private val labelDate = Date(0)

private fun formatTime(timeMs: Long): String {
    labelDate.time = timeMs
    return shortTimeFormat.format(labelDate)
}

private fun formatDay(timeMs: Long): String {
    labelDate.time = timeMs
    return mediumDateFormat.format(labelDate)
}

private fun slotLabel(timeMs: Long): String {
    val time = formatTime(timeMs)
    val calendar = labelCalendar
    calendar.timeInMillis = timeMs
    val localMidnight = calendar.get(Calendar.HOUR_OF_DAY) == 0 &&
        calendar.get(Calendar.MINUTE) < 30
    return if (localMidnight) {
        "${formatDay(timeMs)}  $time"
    } else {
        time
    }
}


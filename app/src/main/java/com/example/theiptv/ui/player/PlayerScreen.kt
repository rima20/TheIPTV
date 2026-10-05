package com.example.theiptv.ui.player

import android.app.Activity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.theiptv.data.PlaybackRequest
import com.example.theiptv.data.XtreamClient
import com.example.theiptv.ui.components.ErrorBanner
import com.example.theiptv.ui.theme.AccentBlue
import com.example.theiptv.ui.theme.AccentTeal
import com.example.theiptv.ui.theme.AppButtonContentPadding
import com.example.theiptv.ui.theme.AppButtonHeight
import com.example.theiptv.ui.theme.AppButtonShape
import com.example.theiptv.ui.theme.AppChipPadding
import com.example.theiptv.ui.theme.AppControlButtonSize
import com.example.theiptv.ui.theme.Night
import kotlinx.coroutines.delay

private const val SKIP_MS = 10_000L
private const val LARGE_SKIP_MS = 20_000L
private const val OVERLAY_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var fromStart by remember { mutableStateOf(false) }
    val urls = if (fromStart && !request?.fromStartUrls.isNullOrEmpty()) {
        request.fromStartUrls
    } else {
        request?.urls.orEmpty()
    }
    var urlIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var overlayVisible by remember { mutableStateOf(false) }
    var overlayGeneration by remember { mutableIntStateOf(0) }
    var skipHint by remember { mutableStateOf<SkipHint?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(C.TIME_UNSET) }
    var isLive by remember { mutableStateOf(false) }
    var seekable by remember { mutableStateOf(false) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var videoFps by remember { mutableStateOf<Float?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val playerFocus = remember { FocusRequester() }
    val playButtonFocus = remember { FocusRequester() }
    val scrubberFocus = remember { FocusRequester() }
    var suppressCenterClick by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        if (overlayVisible) {
            overlayVisible = false
        } else {
            onBack()
        }
    }

    if (request == null || urls.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorBanner("Nothing to play.")
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    shape = ButtonDefaults.shape(AppButtonShape),
                    contentPadding = AppButtonContentPadding,
                    modifier = Modifier.height(AppButtonHeight),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Text("Back", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        return
    }

    val watchingFromStart = fromStart ||
        (request.fromStartUrls.isEmpty() && request.programmeStartMs != null)

    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(XtreamClient.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(SKIP_MS)
            .setSeekForwardIncrementMs(SKIP_MS)
            .setReleaseTimeoutMs(500)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .build()
            .apply { playWhenReady = true }
    }

    LaunchedEffect(urlIndex, request, fromStart) {
        error = null
        val url = urls.getOrNull(urlIndex) ?: return@LaunchedEffect
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    DisposableEffect(exoPlayer) {
        var active = true
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackException: PlaybackException) {
                if (!active) return
                if (urlIndex < urls.lastIndex) {
                    urlIndex += 1
                } else {
                    error = playbackException.message ?: "This stream couldn't be played."
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                if (!active) return
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (!active) return
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (!active) return
                isLive = player.isCurrentMediaItemLive
                seekable = player.isCurrentMediaItemSeekable
                durationMs = player.duration
                positionMs = player.currentPosition
                val info = player.videoStreamInfo()
                if (info.width > 0) videoWidth = info.width
                if (info.height > 0) videoHeight = info.height
                videoFps = info.fps
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            active = false
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    LaunchedEffect(overlayVisible, exoPlayer) {
        while (overlayVisible) {
            positionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration
            isPlaying = exoPlayer.isPlaying
            isLive = exoPlayer.isCurrentMediaItemLive
            seekable = exoPlayer.isCurrentMediaItemSeekable
            val info = exoPlayer.videoStreamInfo()
            if (info.width > 0) videoWidth = info.width
            if (info.height > 0) videoHeight = info.height
            videoFps = info.fps
            nowMs = System.currentTimeMillis()
            delay(250)
        }
    }

    LaunchedEffect(overlayVisible, overlayGeneration) {
        if (!overlayVisible) return@LaunchedEffect
        delay(OVERLAY_TIMEOUT_MS)
        overlayVisible = false
    }

    LaunchedEffect(skipHint) {
        if (skipHint == null) return@LaunchedEffect
        delay(450)
        skipHint = null
    }

    LaunchedEffect(overlayVisible) {
        runCatching {
            if (overlayVisible) {
                exoPlayer.play()
                scrubberFocus.requestFocus()
            } else {
                playerFocus.requestFocus()
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { playerFocus.requestFocus() }
    }

    fun bumpOverlay() {
        overlayGeneration += 1
    }

    fun showOverlay() {
        exoPlayer.play()
        overlayVisible = true
        bumpOverlay()
    }

    fun programmeDurationMs(): Long? {
        val start = request.programmeStartMs ?: return null
        val end = request.programmeEndMs ?: return null
        return (end - start).takeIf { it > 0L }
    }

    fun liveAvailableMs(): Long? {
        val start = request.programmeStartMs ?: return null
        val duration = programmeDurationMs() ?: return null
        return (nowMs - start).coerceIn(0L, duration)
    }

    fun maxSeekMs(): Long {
        val playerMax = durationMs.takeIf { it > 0L && it != C.TIME_UNSET }
        val liveMax = if (watchingFromStart) liveAvailableMs() else null
        return listOfNotNull(playerMax, liveMax).minOrNull() ?: Long.MAX_VALUE
    }

    fun seekBy(deltaMs: Long): Boolean {
        if (!exoPlayer.isCurrentMediaItemSeekable) {
            skipHint = SkipHint.Unavailable
            return false
        }
        val current = exoPlayer.currentPosition
        var target = current + deltaMs
        if (target < 0L) target = 0L
        val max = maxSeekMs()
        if (max != Long.MAX_VALUE && target > max) target = max
        exoPlayer.seekTo(target)
        positionMs = target
        if (overlayVisible) bumpOverlay()
        return true
    }

    fun skip(forward: Boolean) {
        if (!exoPlayer.isCurrentMediaItemSeekable) {
            skipHint = SkipHint.Unavailable
            return
        }
        if (forward) exoPlayer.seekForward() else exoPlayer.seekBack()
        val max = maxSeekMs()
        if (max != Long.MAX_VALUE && exoPlayer.currentPosition > max) {
            exoPlayer.seekTo(max)
        }
        positionMs = exoPlayer.currentPosition
        skipHint = SkipHint.Seek(forward)
        if (overlayVisible) bumpOverlay()
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        isPlaying = exoPlayer.isPlaying
        if (overlayVisible) bumpOverlay()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val isCenter = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (isCenter && event.type == KeyEventType.KeyUp && suppressCenterClick) {
                    suppressCenterClick = false
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.repeatCount > 0) {
                    return@onPreviewKeyEvent !overlayVisible
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (overlayVisible) {
                            false
                        } else {
                            suppressCenterClick = true
                            showOverlay()
                            true
                        }
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        if (overlayVisible) {
                            bumpOverlay()
                            false
                        } else {
                            skip(forward = false)
                            true
                        }
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (overlayVisible) {
                            bumpOverlay()
                            false
                        } else {
                            skip(forward = true)
                            true
                        }
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                            exoPlayer.pause()
                        } else if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                            exoPlayer.play()
                        } else {
                            togglePlay()
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    controllerAutoShow = false
                    keepScreenOn = true
                    isClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = exoPlayer },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = skipHint != null && !overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        ) {
            when (val hint = skipHint) {
                is SkipHint.Seek -> SkipSeekBadge(forward = hint.forward)
                SkipHint.Unavailable -> {
                    Text(
                        text = "Skip isn’t available on this stream",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .background(Night.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                null -> Unit
            }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.46f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Night.copy(alpha = 0.92f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (fromStart) request.fromStartTitle ?: request.title else request.title,
                                style = MaterialTheme.typography.titleLarge
                            )
                            val tech = formatVideoTech(videoWidth, videoHeight, videoFps)
                            if (tech != null) {
                                Text(
                                    text = tech,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                        if (isLive && !watchingFromStart) {
                            Text(
                                text = "LIVE",
                                color = Night,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .background(AccentBlue, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    PlaybackScrubber(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isLive = isLive && !watchingFromStart,
                        seekable = seekable && !(isLive && !watchingFromStart),
                        liveEdgeFraction = liveEdgeFraction(watchingFromStart, request, nowMs),
                        programmeDurationMs = if (watchingFromStart) programmeDurationMs() else null,
                        focusRequester = scrubberFocus,
                        onSeekBy = { delta -> seekBy(delta) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlButton(
                            onClick = {
                                skip(forward = false)
                                bumpOverlay()
                            }
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds")
                        }
                        ControlButton(
                            onClick = { togglePlay() },
                            modifier = Modifier.focusRequester(playButtonFocus)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        ControlButton(
                            onClick = {
                                skip(forward = true)
                                bumpOverlay()
                            }
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds")
                        }
                        if (request.canWatchFromStart && !fromStart) {
                            Surface(
                                onClick = {
                                    fromStart = true
                                    urlIndex = 0
                                    error = null
                                    bumpOverlay()
                                },
                                shape = ClickableSurfaceDefaults.shape(AppButtonShape),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    focusedContainerColor = AccentBlue,
                                    pressedContainerColor = AccentBlue.copy(alpha = 0.8f),
                                    contentColor = Color.White,
                                    focusedContentColor = Night
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(AppChipPadding),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Replay,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("From start", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        ControlButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            }
        }

        if (error != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(request.title, style = MaterialTheme.typography.titleLarge)
                ErrorBanner(error!!)
                Button(
                    onClick = onBack,
                    shape = ButtonDefaults.shape(AppButtonShape),
                    contentPadding = AppButtonContentPadding,
                    modifier = Modifier.height(AppButtonHeight),
                ) { Text("Back to library") }
            }
        }
    }
}

private sealed class SkipHint {
    data class Seek(val forward: Boolean) : SkipHint()
    data object Unavailable : SkipHint()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SkipSeekBadge(forward: Boolean) {
    val spin = rememberInfiniteTransition(label = "skip-spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = if (forward) 360f else -360f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "skip-angle"
    )
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(28.dp)
                .rotate(angle)
        ) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            drawArc(
                color = AccentBlue,
                startAngle = -90f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke.width, size.height - stroke.width),
                style = stroke
            )
        }
        Text(
            text = "10s",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            color = Color.White
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaybackScrubber(
    positionMs: Long,
    durationMs: Long,
    isLive: Boolean,
    seekable: Boolean,
    liveEdgeFraction: Float?,
    programmeDurationMs: Long?,
    focusRequester: FocusRequester,
    onSeekBy: (Long) -> Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val hasDuration = durationMs > 0L && durationMs != C.TIME_UNSET
    val pinToLive = isLive
    val timelineMs = programmeDurationMs?.takeIf { it > 0L }
    val progress = when {
        pinToLive -> 1f
        timelineMs != null -> (positionMs.toFloat() / timelineMs.toFloat()).coerceIn(0f, liveEdgeFraction ?: 1f)
        hasDuration -> (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    val catchupEnd = liveEdgeFraction
    val behindLiveMs = if (timelineMs != null && catchupEnd != null) {
        (catchupEnd * timelineMs - positionMs).toLong()
    } else {
        null
    }
    val showThumb = focused && !pinToLive && (hasDuration || timelineMs != null)
    val barHeight = if (focused) 12.dp else 6.dp
    val thumbSize = if (focused) 18.dp else 0.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (!seekable) return@onKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.MediaRewind -> onSeekBy(-LARGE_SKIP_MS)
                    Key.DirectionRight, Key.MediaFastForward -> onSeekBy(LARGE_SKIP_MS)
                    else -> false
                }
            }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (focused) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.18f)
                    )
            ) {
                if (catchupEnd != null && catchupEnd > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(catchupEnd)
                            .fillMaxHeight()
                            .background(AccentTeal.copy(alpha = 0.28f))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(AccentBlue)
                )
            }
            if (catchupEnd != null && catchupEnd in 0.02f..0.98f) {
                Box(
                    modifier = Modifier
                        .offset(x = (maxWidth * catchupEnd) - 1.dp)
                        .width(2.dp)
                        .height(if (focused) 16.dp else 12.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(AccentTeal)
                )
            }
            if (showThumb) {
                Box(
                    modifier = Modifier
                        .offset(x = ((maxWidth - thumbSize) * progress))
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    pinToLive -> "Live"
                    else -> formatPlaybackTime(positionMs)
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    focused && seekable -> "Left / Right  ·  20 seconds"
                    pinToLive -> "Live"
                    behindLiveMs != null && behindLiveMs > 1_500L ->
                        "−${formatPlaybackTime(behindLiveMs)} to live"
                    catchupEnd != null -> "Live"
                    else -> formatPlaybackTime(if (timelineMs != null) timelineMs else durationMs)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (catchupEnd != null && !pinToLive) AccentTeal else Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

private fun liveEdgeFraction(watchingFromStart: Boolean, request: PlaybackRequest, nowMs: Long): Float? {
    if (!watchingFromStart) return null
    val start = request.programmeStartMs ?: return null
    val end = request.programmeEndMs ?: return null
    if (nowMs >= end) return null
    val duration = end - start
    if (duration <= 0L) return null
    return ((nowMs - start).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(AppControlButtonSize),
        shape = ClickableSurfaceDefaults.shape(AppButtonShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = AccentBlue,
            pressedContainerColor = AccentBlue.copy(alpha = 0.8f),
            contentColor = Color.White,
            focusedContentColor = Night
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

private data class VideoStreamInfo(
    val width: Int,
    val height: Int,
    val fps: Float?,
)

private fun Player.videoStreamInfo(): VideoStreamInfo {
    var width = videoSize.width
    var height = videoSize.height
    var fps: Float? = null
    currentTracks.groups.forEach { group ->
        if (group.type != C.TRACK_TYPE_VIDEO) return@forEach
        for (index in 0 until group.length) {
            if (!group.isTrackSelected(index)) continue
            val format = group.getTrackFormat(index)
            if (width <= 0) width = format.width
            if (height <= 0) height = format.height
            val rate = format.frameRate
            if (rate > 0f) fps = rate
        }
    }
    return VideoStreamInfo(width.coerceAtLeast(0), height.coerceAtLeast(0), fps)
}

private fun formatVideoTech(width: Int, height: Int, fps: Float?): String? {
    val resolution = if (width > 0 && height > 0) "${width}×${height}" else null
    val frameRate = fps?.takeIf { it > 0f }?.let { rate ->
        val rounded = kotlin.math.round(rate)
        if (kotlin.math.abs(rate - rounded) < 0.15f) {
            "${rounded.toInt()} fps"
        } else {
            "%.2f fps".format(rate)
        }
    }
    return listOfNotNull(resolution, frameRate).takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

private fun formatPlaybackTime(ms: Long): String {
    if (ms < 0L || ms == C.TIME_UNSET) return "--:--"
    val total = (ms / 1000L).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

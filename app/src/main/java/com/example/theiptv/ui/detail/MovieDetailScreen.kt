package com.example.theiptv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.ItemDetails
import com.example.theiptv.data.PlaybackRequest
import com.example.theiptv.ui.components.EmptyState
import com.example.theiptv.ui.components.ErrorBanner
import com.example.theiptv.ui.components.FavoriteToggleButton
import com.example.theiptv.ui.components.LoadingBox
import com.example.theiptv.ui.components.RemoteImage
import com.example.theiptv.ui.theme.AccentAmber
import com.example.theiptv.ui.theme.AppButtonContentPadding
import com.example.theiptv.ui.theme.AppButtonHeight
import com.example.theiptv.ui.theme.AppButtonShape
import com.example.theiptv.ui.theme.AppButtonWithIconContentPadding
import com.example.theiptv.ui.theme.AppIconButtonSize
import com.example.theiptv.ui.theme.Night
import com.example.theiptv.ui.theme.NightSurface
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    viewModel: MovieDetailViewModel,
    onPlay: (PlaybackRequest) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val playFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    LaunchedEffect(state.item?.id, state.isLoading) {
        if (state.item != null && !state.isLoading) {
            delay(50)
            try {
                playFocus.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val backdropUrl = state.details?.backdropUrl
        if (!backdropUrl.isNullOrBlank()) {
            RemoteImage(
                url = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                requestWidth = 1280.dp,
                requestHeight = 720.dp,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Night.copy(alpha = 0.72f),
                            0.55f to Night.copy(alpha = 0.82f),
                            1f to Night.copy(alpha = 0.92f),
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Night.copy(alpha = 0.28f))
            )
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                shape = IconButtonDefaults.shape(AppButtonShape),
                modifier = Modifier.size(AppIconButtonSize),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Text(state.item?.name ?: "Movie", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))
        when {
            state.isLoading && state.item == null -> LoadingBox("Loading movie…")
            state.error != null && state.item == null -> {
                ErrorBanner(state.error!!)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::refresh,
                    shape = ButtonDefaults.shape(AppButtonShape),
                    contentPadding = AppButtonContentPadding,
                    modifier = Modifier.height(AppButtonHeight),
                ) { Text("Try again") }
            }
            state.item == null -> EmptyState("Movie unavailable", "Go back and pick another title.")
            else -> {
                val item = state.item!!
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        MoviePoster(item)
                        MovieInfo(
                            item = item,
                            details = state.details,
                            playFocus = playFocus,
                            isFavorite = state.isFavorite,
                            onPlay = { viewModel.play()?.let(onPlay) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MoviePoster(
                            item,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            posterWidth = 120.dp,
                        )
                        MovieInfo(
                            item = item,
                            details = state.details,
                            playFocus = playFocus,
                            isFavorite = state.isFavorite,
                            onPlay = { viewModel.play()?.let(onPlay) },
                            onToggleFavorite = viewModel::toggleFavorite,
                        )
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MoviePoster(
    item: ContentItem,
    modifier: Modifier = Modifier,
    posterWidth: Dp = 180.dp,
) {
    Box(
        modifier = modifier
            .width(posterWidth)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(18.dp))
            .background(NightSurface),
        contentAlignment = Alignment.Center
    ) {
        if (item.posterUrl.isBlank()) {
            Text(
                text = item.name.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            RemoteImage(
                url = item.posterUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                requestWidth = posterWidth,
                requestHeight = posterWidth * 3f / 2f,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MovieInfo(
    item: ContentItem,
    details: ItemDetails?,
    playFocus: FocusRequester,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val director = details?.director.orEmpty()
    val cast = details?.cast.orEmpty()
    val country = details?.country.orEmpty()
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(item.name, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        MovieMetaLine(item, country)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPlay,
                shape = ButtonDefaults.shape(AppButtonShape),
                contentPadding = AppButtonWithIconContentPadding,
                modifier = Modifier
                    .height(AppButtonHeight)
                    .focusRequester(playFocus)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play")
            }
            FavoriteToggleButton(
                isFavorite = isFavorite,
                onClick = onToggleFavorite,
            )
        }
        if (!item.plot.isNullOrBlank()) {
            Text(item.plot!!, style = MaterialTheme.typography.bodyLarge)
        }
        if (director.isNotBlank()) {
            DetailBlock("Director", director)
        }
        if (cast.isNotBlank()) {
            DetailBlock("Cast", cast)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MovieMetaLine(item: ContentItem, country: String) {
    val parts = listOfNotNull(
        item.year,
        formatDuration(item.duration),
        item.genre?.takeIf { it.isNotBlank() },
        country.takeIf { it.isNotBlank() },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString("  ·  "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        val rating = item.rating
        if (!rating.isNullOrBlank()) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.Star, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(rating, style = MaterialTheme.typography.titleMedium, color = AccentAmber)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDuration(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty() || value == "0") return null
    if (value.contains(':')) return value
    val minutes = value.toIntOrNull() ?: return value
    if (minutes <= 0) return null
    val normalized = if (minutes > 300) minutes / 60 else minutes
    return "$normalized min"
}

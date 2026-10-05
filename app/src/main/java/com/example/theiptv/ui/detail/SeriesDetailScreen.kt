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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.example.theiptv.data.PlaybackRequest
import com.example.theiptv.data.SeriesDetails
import com.example.theiptv.data.SeriesEpisode
import com.example.theiptv.ui.components.EmptyState
import com.example.theiptv.ui.components.FavoriteToggleButton
import com.example.theiptv.ui.components.ErrorBanner
import com.example.theiptv.ui.components.LoadingBox
import com.example.theiptv.ui.components.RemoteImage
import com.example.theiptv.ui.theme.AccentAmber
import com.example.theiptv.ui.theme.AccentBlue
import com.example.theiptv.ui.theme.AppButtonContentPadding
import com.example.theiptv.ui.theme.AppButtonHeight
import com.example.theiptv.ui.components.rememberListWrapState
import com.example.theiptv.ui.components.wrapListNavigation
import com.example.theiptv.ui.theme.AppButtonShape
import com.example.theiptv.ui.theme.AppChipPadding
import com.example.theiptv.ui.theme.AppIconButtonSize
import com.example.theiptv.ui.theme.Night
import com.example.theiptv.ui.theme.NightSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    viewModel: SeriesDetailViewModel,
    onPlay: (PlaybackRequest) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    BackHandler(onBack = onBack)

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
                Text(state.details?.title ?: "Series", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            when {
                state.isLoading -> LoadingBox("Loading series…")
                state.error != null -> {
                    ErrorBanner(state.error!!)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::refresh,
                        shape = ButtonDefaults.shape(AppButtonShape),
                        contentPadding = AppButtonContentPadding,
                        modifier = Modifier.height(AppButtonHeight),
                    ) { Text("Try again") }
                }
                state.details == null -> EmptyState("Series unavailable", "Go back and pick another title.")
                else -> {
                    val details = state.details!!
                    val selected = details.seasons.firstOrNull { it.number == state.selectedSeason }
                        ?: details.seasons.firstOrNull()
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            SeriesPoster(
                                cover = details.cover,
                                title = details.title,
                                modifier = Modifier.fillMaxHeight(),
                                maxWidth = if (wide) 180.dp else 120.dp,
                            )
                            SeriesInfo(
                                details = details,
                                isFavorite = state.isFavorite,
                                onToggleFavorite = viewModel::toggleFavorite,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        SeasonRow(
                            seasons = details.seasons.map { it.number },
                            selectedSeason = selected?.number,
                            onSelectSeason = viewModel::selectSeason
                        )
                        Spacer(Modifier.height(10.dp))
                        EpisodeRow(
                            seriesTitle = details.title,
                            episodes = selected?.episodes.orEmpty(),
                            onPlayEpisode = { episode -> viewModel.play(episode)?.let(onPlay) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesPoster(
    cover: String,
    title: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 180.dp,
) {
    Box(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxHeight()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(18.dp))
            .background(NightSurface),
        contentAlignment = Alignment.Center
    ) {
        if (cover.isBlank()) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            RemoteImage(
                url = cover,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                requestWidth = maxWidth,
                requestHeight = maxWidth * 3f / 2f,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesInfo(
    details: SeriesDetails,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(details.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        SeriesMetaLine(details)
        FavoriteToggleButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
        )
        if (details.plot.isNotBlank()) {
            Text(details.plot, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        if (details.director.isNotBlank()) {
            DetailBlock("Director", details.director)
        }
        if (details.cast.isNotBlank()) {
            DetailBlock("Cast", details.cast)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesMetaLine(details: SeriesDetails) {
    val parts = listOfNotNull(
        details.genre.takeIf { it.isNotBlank() },
        "${details.seasons.size} season".let { label ->
            if (details.seasons.size == 1) label else "${label}s"
        }.takeIf { details.seasons.isNotEmpty() },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString("  ·  "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (details.rating.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.Star, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(details.rating, style = MaterialTheme.typography.titleMedium, color = AccentAmber)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonRow(
    seasons: List<Int>,
    selectedSeason: Int?,
    onSelectSeason: (Int) -> Unit,
) {
    if (seasons.isEmpty()) return
    val listState = rememberLazyListState()
    val wrapState = rememberListWrapState()
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(seasons) { index, season ->
            val selected = season == selectedSeason
            Surface(
                onClick = { onSelectSeason(season) },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (selected) AccentBlue else NightSurface.copy(alpha = 0.82f),
                    contentColor = if (selected) Night else MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = AccentBlue.copy(alpha = 0.85f),
                    focusedContentColor = Night
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                shape = ClickableSurfaceDefaults.shape(AppButtonShape),
                modifier = Modifier.wrapListNavigation(
                    index = index,
                    count = seasons.size,
                    state = wrapState,
                    scrollTo = { listState.scrollToItem(it) },
                    wrapVertical = false,
                    wrapHorizontal = true,
                ),
            ) {
                Text(
                    text = "Season $season",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(AppChipPadding)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeRow(
    seriesTitle: String,
    episodes: List<SeriesEpisode>,
    onPlayEpisode: (SeriesEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (episodes.isEmpty()) {
        Text(
            text = "No episodes in this season.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
        return
    }
    val listState = rememberLazyListState()
    val wrapState = rememberListWrapState()
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
            EpisodeCard(
                seriesTitle = seriesTitle,
                episode = episode,
                onClick = { onPlayEpisode(episode) },
                wrapModifier = Modifier.wrapListNavigation(
                    index = index,
                    count = episodes.size,
                    state = wrapState,
                    scrollTo = { listState.scrollToItem(it) },
                    wrapVertical = false,
                    wrapHorizontal = true,
                ),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(
    seriesTitle: String,
    episode: SeriesEpisode,
    onClick: () -> Unit,
    wrapModifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NightSurface.copy(alpha = 0.88f),
            focusedContainerColor = AccentBlue.copy(alpha = 0.22f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        modifier = Modifier
            .width(176.dp)
            .fillMaxHeight()
            .then(wrapModifier)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Night),
                contentAlignment = Alignment.Center
            ) {
                if (episode.image.isNotBlank()) {
                    RemoteImage(
                        url = episode.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        requestWidth = 176.dp,
                        requestHeight = 99.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(28.dp))
                }
            }
            Text(
                text = episode.displayTitle(seriesTitle),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

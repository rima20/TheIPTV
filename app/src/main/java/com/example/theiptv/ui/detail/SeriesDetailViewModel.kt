package com.example.theiptv.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.theiptv.app
import com.example.theiptv.data.ApiResult
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.ContentType
import com.example.theiptv.data.PlaybackRequest
import com.example.theiptv.data.SeriesDetails
import com.example.theiptv.data.SeriesEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val details: SeriesDetails? = null,
    val selectedSeason: Int? = null,
    val isFavorite: Boolean = false,
)

class SeriesDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository = application.app().repository
    private val credentialsStore = application.app().credentialsStore
    private val favoritesStore = application.app().favoritesStore
    private val seriesId: String = savedStateHandle.get<String>("seriesId").orEmpty()

    private val _state = MutableStateFlow(SeriesDetailUiState())
    val state: StateFlow<SeriesDetailUiState> = _state

    init {
        refresh()
        viewModelScope.launch {
            val providerId = credentialsStore.loadAll().active?.id ?: return@launch
            favoritesStore.observe().collect { favorites ->
                _state.update {
                    it.copy(
                        isFavorite = favorites.any { favorite ->
                            favorite.providerId == providerId &&
                                favorite.type == ContentType.Series &&
                                favorite.id == seriesId
                        }
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.seriesDetails(seriesId)) {
                is ApiResult.Ok -> {
                    val firstSeason = result.data.seasons.firstOrNull()?.number
                    _state.update {
                        it.copy(
                            isLoading = false,
                            details = result.data,
                            selectedSeason = it.selectedSeason ?: firstSeason,
                        )
                    }
                }
                is ApiResult.Err -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun selectSeason(number: Int) {
        _state.update { it.copy(selectedSeason = number) }
    }

    fun play(episode: SeriesEpisode): PlaybackRequest? {
        val seriesTitle = _state.value.details?.title ?: "Episode"
        return repository.play(seriesTitle, episode.copy(title = episode.displayTitle(seriesTitle)))
    }

    fun toggleFavorite() {
        val item = favoriteItem() ?: return
        viewModelScope.launch {
            val providerId = credentialsStore.loadAll().active?.id ?: return@launch
            favoritesStore.toggle(providerId, item)
        }
    }

    private fun favoriteItem(): ContentItem? {
        val details = _state.value.details ?: return null
        val cached = repository.cachedItem(ContentType.Series, seriesId)
        if (cached != null) {
            return cached.copy(
                name = details.title.ifBlank { cached.name },
                posterUrl = details.cover.ifBlank { cached.posterUrl },
                plot = details.plot.takeIf { it.isNotBlank() } ?: cached.plot,
                genre = details.genre.takeIf { it.isNotBlank() } ?: cached.genre,
                rating = details.rating.takeIf { it.isNotBlank() } ?: cached.rating,
            )
        }
        return ContentItem(
            id = seriesId,
            name = details.title,
            posterUrl = details.cover,
            categoryIds = emptyList(),
            type = ContentType.Series,
            containerExtension = null,
            rating = details.rating.takeIf { it.isNotBlank() },
            plot = details.plot.takeIf { it.isNotBlank() },
            genre = details.genre.takeIf { it.isNotBlank() },
        )
    }
}

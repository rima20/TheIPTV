package com.example.theiptv.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.theiptv.app
import com.example.theiptv.data.ApiResult
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.ContentType
import com.example.theiptv.data.ItemDetails
import com.example.theiptv.data.PlaybackRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovieDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val item: ContentItem? = null,
    val details: ItemDetails? = null,
    val isFavorite: Boolean = false,
)

class MovieDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository = application.app().repository
    private val credentialsStore = application.app().credentialsStore
    private val favoritesStore = application.app().favoritesStore
    private val movieId: String = savedStateHandle.get<String>("movieId").orEmpty()

    private val _state = MutableStateFlow(MovieDetailUiState())
    val state: StateFlow<MovieDetailUiState> = _state

    init {
        refresh()
        viewModelScope.launch {
            val providerId = credentialsStore.loadAll().active?.id ?: return@launch
            favoritesStore.observe().collect { favorites ->
                _state.update {
                    it.copy(
                        isFavorite = favorites.any { favorite ->
                            favorite.providerId == providerId &&
                                favorite.type == ContentType.Movies &&
                                favorite.id == movieId
                        }
                    )
                }
            }
        }
    }

    fun refresh() {
        val catalog = repository.cachedItem(ContentType.Movies, movieId)
        val cachedDetails = repository.cachedVodDetails(movieId)
        val preview = catalog?.let { item ->
            cachedDetails?.let { item.withDetails(it) } ?: item
        }
        _state.update {
            it.copy(
                isLoading = preview == null,
                error = null,
                item = preview,
                details = cachedDetails,
            )
        }
        viewModelScope.launch {
            when (val result = repository.vodDetails(movieId)) {
                is ApiResult.Ok -> {
                    val details = result.data
                    val item = (catalog ?: stubItem(details)).withDetails(details)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            item = item,
                            details = details,
                        )
                    }
                }
                is ApiResult.Err -> {
                    if (preview != null) {
                        _state.update { it.copy(isLoading = false) }
                    } else {
                        _state.update { it.copy(isLoading = false, error = result.message) }
                    }
                }
            }
        }
    }

    fun play(): PlaybackRequest? {
        val item = _state.value.item ?: return null
        return repository.play(item)
    }

    fun toggleFavorite() {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val providerId = credentialsStore.loadAll().active?.id ?: return@launch
            favoritesStore.toggle(providerId, item)
        }
    }

    private fun stubItem(details: ItemDetails): ContentItem {
        return ContentItem(
            id = movieId,
            name = details.title?.takeIf { it.isNotBlank() } ?: "Movie",
            posterUrl = details.posterUrl.orEmpty(),
            categoryIds = emptyList(),
            type = ContentType.Movies,
            containerExtension = details.containerExtension,
            rating = details.rating,
            plot = details.plot,
            year = details.year,
            genre = details.genre,
            duration = details.duration,
        )
    }
}

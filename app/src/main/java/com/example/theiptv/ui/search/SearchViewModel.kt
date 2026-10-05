package com.example.theiptv.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.theiptv.app
import com.example.theiptv.data.ApiResult
import com.example.theiptv.data.ContentItem
import com.example.theiptv.data.ContentType
import com.example.theiptv.data.PlaybackRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val loadingMessage: String? = null,
    val error: String? = null,
    val results: List<ContentItem> = emptyList(),
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.app().repository

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, error = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            runSearch(query)
        }
    }

    fun play(item: ContentItem): PlaybackRequest? {
        if (item.type == ContentType.Series) return null
        return repository.play(item)
    }

    private suspend fun runSearch(raw: String) {
        val query = raw.trim()
        if (query.length < 2) {
            _state.update {
                it.copy(
                    isSearching = false,
                    loadingMessage = null,
                    results = emptyList(),
                )
            }
            return
        }

        emitCachedResults(query)

        val errors = mutableListOf<String>()
        for (type in SEARCH_ORDER) {
            if (repository.hasCachedItems(type)) continue
            if (_state.value.query.trim() != query) return
            _state.update { it.copy(isSearching = true, loadingMessage = "Searching ${type.label.lowercase()}…") }
            val result = withContext(Dispatchers.IO) {
                try {
                    repository.items(type)
                } catch (_: OutOfMemoryError) {
                    System.gc()
                    ApiResult.Err("${type.label} is too large to search on this device.")
                }
            }
            if (result is ApiResult.Err) errors += result.message
            emitCachedResults(query)
        }

        _state.update {
            it.copy(
                isSearching = false,
                loadingMessage = null,
                error = errors.takeIf { list -> list.isNotEmpty() }?.joinToString("\n"),
            )
        }
    }

    private suspend fun emitCachedResults(query: String) {
        val results = withContext(Dispatchers.Default) {
            repository.searchCached(query, limit = MAX_RESULTS)
        }
        _state.update { it.copy(results = results) }
    }

    private companion object {
        const val MAX_RESULTS = 200
        val SEARCH_ORDER = listOf(ContentType.Live, ContentType.Series, ContentType.Movies)
    }
}

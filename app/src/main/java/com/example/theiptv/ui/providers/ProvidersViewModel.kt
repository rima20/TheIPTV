package com.example.theiptv.ui.providers

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.theiptv.app
import com.example.theiptv.data.ApiResult
import com.example.theiptv.data.IptvProvider
import com.example.theiptv.data.ProviderSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProvidersUiState(
    val providers: List<IptvProvider> = emptyList(),
    val activeId: String? = null,
    val connectingId: String? = null,
    val error: String? = null,
    val connected: Boolean = false,
)

class ProvidersViewModel(application: Application) : AndroidViewModel(application) {
    private val store = application.app().credentialsStore
    private val repository = application.app().repository

    private val _state = MutableStateFlow(ProvidersUiState())
    val state: StateFlow<ProvidersUiState> = _state

    init {
        viewModelScope.launch { refresh() }
    }

    fun connect(provider: IptvProvider) {
        viewModelScope.launch {
            val current = repository.session?.credentials
            if (current != null &&
                current.server == provider.server &&
                current.username == provider.username &&
                current.password == provider.password
            ) {
                store.setActive(provider.id)
                _state.update { it.copy(connected = true, error = null, activeId = provider.id) }
                return@launch
            }
            _state.update { it.copy(connectingId = provider.id, error = null) }
            repository.logout()
            when (val result = repository.login(provider.credentials)) {
                is ApiResult.Ok -> {
                    store.setActive(provider.id)
                    _state.update {
                        it.copy(
                            connectingId = null,
                            connected = true,
                            activeId = provider.id,
                        )
                    }
                }
                is ApiResult.Err -> {
                    _state.update {
                        it.copy(connectingId = null, error = result.message)
                    }
                }
            }
        }
    }

    fun delete(provider: IptvProvider) {
        viewModelScope.launch {
            val next = store.remove(provider.id)
            val activeGone = repository.session?.credentials?.let { creds ->
                creds.server == provider.server && creds.username == provider.username
            } == true
            if (activeGone) repository.logout()
            applySnapshot(next)
        }
    }

    fun consumeConnected() {
        _state.update { it.copy(connected = false) }
    }

    fun reload() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        applySnapshot(store.loadAll())
    }

    private fun applySnapshot(snapshot: ProviderSnapshot) {
        _state.update {
            it.copy(
                providers = snapshot.providers,
                activeId = snapshot.activeId,
                error = null,
            )
        }
    }
}

fun IptvProvider.hostLabel(): String {
    val host = Uri.parse(server).host
    return host?.ifBlank { server } ?: server
}

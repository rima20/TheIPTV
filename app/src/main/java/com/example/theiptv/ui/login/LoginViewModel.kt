package com.example.theiptv.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.theiptv.app
import com.example.theiptv.data.ApiResult
import com.example.theiptv.data.XtreamClient
import com.example.theiptv.data.providerFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val name: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
    val hasProviders: Boolean = false,
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val store = application.app().credentialsStore
    private val repository = application.app().repository

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    init {
        viewModelScope.launch {
            val snapshot = store.loadAll()
            _state.update { it.copy(hasProviders = snapshot.providers.isNotEmpty()) }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onServerChange(value: String) = _state.update { it.copy(server = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun connect() {
        val current = _state.value
        val parsed = XtreamClient.parseCredentials(current.server, current.username, current.password)
        if (parsed == null) {
            _state.update { it.copy(error = "Enter server address, username, and password.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.logout()
            when (val result = repository.login(parsed)) {
                is ApiResult.Ok -> {
                    store.upsert(providerFrom(parsed, name = current.name), makeActive = true)
                    _state.update { it.copy(isLoading = false, loggedIn = true) }
                }
                is ApiResult.Err -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun consumeLoggedIn() {
        _state.update { it.copy(loggedIn = false) }
    }

    fun resetForm() {
        _state.update {
            LoginUiState(hasProviders = it.hasProviders)
        }
    }
}

package com.etoken.ui.username

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.etoken.EtokenApplication
import com.etoken.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UsernameViewModel(private val preferences: UserPreferences) : ViewModel() {

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    init {
        // Prefill with the last username so a returning user just taps through.
        viewModelScope.launch { _username.value = preferences.username.first() }
    }

    fun onUsernameChange(value: String) {
        _username.value = value
    }

    fun remember(username: String) {
        viewModelScope.launch { preferences.setUsername(username) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as EtokenApplication
                UsernameViewModel(app.container.userPreferences)
            }
        }
    }
}

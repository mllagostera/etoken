package com.etoken.ui.username

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.etoken.EtokenApplication
import com.etoken.data.AppLanguageStore
import com.etoken.data.UserPreferences
import com.etoken.domain.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UsernameViewModel(
    private val preferences: UserPreferences,
    private val languages: AppLanguageStore,
) : ViewModel() {

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    /**
     * The language picker's state. It is the store's own flow: writing to it
     * is what makes [com.etoken.MainActivity] recreate itself with the new
     * strings, so there is nothing here to keep a copy of.
     */
    val language: StateFlow<AppLanguage> = languages.language

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

    fun onLanguageChange(language: AppLanguage) {
        languages.set(language)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as EtokenApplication
                UsernameViewModel(app.container.userPreferences, app.container.appLanguage)
            }
        }
    }
}

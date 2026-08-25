package com.etoken.ui.decks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.etoken.EtokenApplication
import com.etoken.data.MoxfieldRepository
import com.etoken.domain.model.DeckSummary
import com.etoken.ui.common.LoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface DecksUiState {
    data object Loading : DecksUiState
    data object Empty : DecksUiState
    data class Failed(val error: LoadError) : DecksUiState
    data class Ready(val decks: List<DeckSummary>) : DecksUiState
}

class DecksViewModel(
    private val repository: MoxfieldRepository,
    val username: String,
) : ViewModel() {

    private val _state = MutableStateFlow<DecksUiState>(DecksUiState.Loading)
    val state: StateFlow<DecksUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DecksUiState.Loading
            try {
                val decks = repository.listDecks(username)
                if (decks.isEmpty()) {
                    _state.value = DecksUiState.Empty
                } else {
                    _state.value = DecksUiState.Ready(decks)
                    // Names and covers stream in afterwards, so the grid is
                    // interactive immediately instead of waiting on N fetches.
                    hydrate(decks)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DecksUiState.Failed(LoadError.from(e))
            }
        }
    }

    /**
     * Fills in missing deck names and cover art, a few decks at a time.
     *
     * The concurrency cap matters: a large collection would otherwise fire a
     * hundred simultaneous requests at Moxfield and get rate limited.
     */
    private fun CoroutineScope.hydrate(decks: List<DeckSummary>) {
        val gate = Semaphore(MAX_CONCURRENT_HYDRATIONS)

        decks.forEach { deck ->
            launch {
                gate.withPermit {
                    // One deck failing to hydrate must not blank the grid; it
                    // just keeps whatever search gave us.
                    val hydrated = try {
                        repository.hydrate(deck)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        return@withPermit
                    }

                    _state.update { current ->
                        if (current !is DecksUiState.Ready) return@update current
                        DecksUiState.Ready(
                            current.decks.map { if (it.publicId == hydrated.publicId) hydrated else it },
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_CONCURRENT_HYDRATIONS = 4

        /** Route argument carrying the Moxfield username. */
        const val ARG_USERNAME = "username"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as EtokenApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                DecksViewModel(
                    repository = app.container.repository,
                    username = checkNotNull(handle.get<String>(ARG_USERNAME)) { "missing $ARG_USERNAME" },
                )
            }
        }
    }
}

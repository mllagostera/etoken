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
import com.etoken.domain.DeckFilter
import com.etoken.domain.DeckSource
import com.etoken.domain.model.DeckSummary
import com.etoken.ui.common.LoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface DecksUiState {
    data object Loading : DecksUiState
    data object Empty : DecksUiState
    data class Failed(val error: LoadError) : DecksUiState

    data class Ready(
        /** Decks left after applying [query]. */
        val decks: List<DeckSummary>,
        /** How many the user has in total, for the "3 de 27" counter. */
        val total: Int,
        val query: String,
        val isRefreshing: Boolean,
        /** Set when a refresh failed but the list already on screen is still good. */
        val refreshError: LoadError?,
    ) : DecksUiState
}

class DecksViewModel(
    private val repository: MoxfieldRepository,
    private val savedState: SavedStateHandle,
    /** Whose decks these are: a Moxfield user, or the preconstructed listing. */
    val source: DeckSource,
) : ViewModel() {

    private val decks = MutableStateFlow<List<DeckSummary>>(emptyList())

    // In the SavedStateHandle rather than a plain flow: Android kills backgrounded
    // processes freely, and coming back to a grid that silently un-filtered itself
    // reads as the app having lost your place.
    private val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")
    private val phase = MutableStateFlow<Phase>(Phase.Loading)
    private val refreshError = MutableStateFlow<LoadError?>(null)

    private var fetchJob: Job? = null

    /**
     * Derived rather than assigned, so that hydration filling in commander
     * names re-runs the filter on its own. Assigning the visible list by hand
     * would mean remembering to re-filter at every one of those updates.
     */
    val state: StateFlow<DecksUiState> =
        combine(phase, decks, query, refreshError) { current, all, text, error ->
            when {
                current is Phase.Failed -> DecksUiState.Failed(current.error)
                current is Phase.Loading -> DecksUiState.Loading
                all.isEmpty() -> DecksUiState.Empty
                else -> DecksUiState.Ready(
                    decks = DeckFilter.apply(all, text),
                    total = all.size,
                    query = text,
                    isRefreshing = current is Phase.Refreshing,
                    refreshError = error,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecksUiState.Loading)

    init {
        load()
    }

    fun load() = fetch(isRefresh = false)

    /** Drops the caches and goes back to Moxfield. */
    fun refresh() = fetch(isRefresh = true)

    fun onQueryChange(value: String) {
        savedState[KEY_QUERY] = value
    }

    fun clearQuery() {
        savedState[KEY_QUERY] = ""
    }

    fun dismissRefreshError() {
        refreshError.value = null
    }

    private fun fetch(isRefresh: Boolean) {
        // Cancels the previous fetch and, with it, any hydration still in
        // flight — those are children of that coroutine.
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val hasSomethingOnScreen = decks.value.isNotEmpty()
            refreshError.value = null
            phase.value = if (isRefresh && hasSomethingOnScreen) Phase.Refreshing else Phase.Loading

            try {
                if (isRefresh) repository.invalidate()

                val fresh = repository.listDecks(source)
                decks.value = fresh
                phase.value = Phase.Ready
                if (fresh.isNotEmpty()) hydrate(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = LoadError.from(e)
                if (isRefresh && hasSomethingOnScreen) {
                    // A failed refresh must not take away a list that is still
                    // perfectly usable; it just says so and leaves it alone.
                    refreshError.value = error
                    phase.value = Phase.Ready
                } else {
                    phase.value = Phase.Failed(error)
                }
            }
        }
    }

    /**
     * Fills in missing deck names and cover art, a few decks at a time.
     *
     * The concurrency cap matters: a large collection would otherwise fire a
     * hundred simultaneous requests at Moxfield and get rate limited.
     */
    private fun CoroutineScope.hydrate(pending: List<DeckSummary>) {
        val gate = Semaphore(MAX_CONCURRENT_HYDRATIONS)

        pending.forEach { deck ->
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

                    decks.update { current ->
                        current.map { if (it.publicId == hydrated.publicId) hydrated else it }
                    }
                }
            }
        }
    }

    private sealed interface Phase {
        data object Loading : Phase
        data object Ready : Phase
        data object Refreshing : Phase
        data class Failed(val error: LoadError) : Phase
    }

    companion object {
        private const val MAX_CONCURRENT_HYDRATIONS = 4
        private const val KEY_QUERY = "deck_query"

        /** Route argument carrying the Moxfield username. */
        const val ARG_USERNAME = "username"

        /**
         * Route argument carrying Moxfield's `fmt` filter. Optional, and empty
         * for every route but the precons one — a route argument cannot be
         * absent once the screen has it, so "no filter" is the empty string
         * and [DeckSource.of] turns that back into null.
         */
        const val ARG_FORMAT = "fmt"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as EtokenApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                DecksViewModel(
                    repository = app.container.repository,
                    savedState = handle,
                    source = DeckSource.of(
                        username = checkNotNull(handle.get<String>(ARG_USERNAME)) { "missing $ARG_USERNAME" },
                        format = handle.get<String>(ARG_FORMAT),
                    ),
                )
            }
        }
    }
}

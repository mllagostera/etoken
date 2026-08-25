package com.etoken.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.etoken.R
import com.etoken.domain.model.DeckSummary
import com.etoken.ui.common.ActionButton
import com.etoken.ui.common.BackButton
import com.etoken.ui.common.ErrorView
import com.etoken.ui.common.LoadError
import com.etoken.ui.common.LoadingView
import com.etoken.ui.common.MessageView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(
    onDeckClick: (DeckSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DecksViewModel = viewModel(factory = DecksViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready = state as? DecksUiState.Ready

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.username,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Only worth saying while a filter is actually hiding something.
                        if (ready != null && ready.query.isNotBlank()) {
                            Text(
                                text = stringResource(
                                    R.string.decks_filtered_count,
                                    ready.decks.size,
                                    ready.total,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    ActionButton(
                        icon = R.drawable.ic_refresh,
                        description = stringResource(R.string.action_refresh),
                        onClick = viewModel::refresh,
                        // Nothing to refresh mid-refresh, or before the first load.
                        enabled = ready != null && !ready.isRefreshing,
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val current = state) {
                DecksUiState.Loading -> LoadingView()
                DecksUiState.Empty -> MessageView(stringResource(R.string.decks_empty))
                is DecksUiState.Failed -> ErrorView(current.error, onRetry = viewModel::load)
                is DecksUiState.Ready -> DecksReady(current, viewModel, onDeckClick)
            }
        }
    }
}

@Composable
private fun DecksReady(
    state: DecksUiState.Ready,
    viewModel: DecksViewModel,
    onDeckClick: (DeckSummary) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (state.isRefreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        state.refreshError?.let { error ->
            RefreshErrorBanner(error, onDismiss = viewModel::dismissRefreshError)
        }

        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onClear = viewModel::clearQuery,
        )

        // weight(1f) rather than fillMaxSize: the grid takes what is left after
        // the search field and the banner, whatever height those end up being.
        Box(Modifier.weight(1f)) {
            if (state.decks.isEmpty()) {
                MessageView(stringResource(R.string.decks_no_matches, state.query))
            } else {
                DeckGrid(state.decks, onDeckClick)
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.decks_search_hint)) },
        singleLine = true,
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                ActionButton(
                    icon = R.drawable.ic_close,
                    description = stringResource(R.string.decks_search_clear),
                    onClick = onClear,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun RefreshErrorBanner(error: LoadError, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // What happened to the list, which is the part the user cares
                // about here: it is still the last good one.
                Text(
                    text = stringResource(R.string.refresh_failed),
                    style = MaterialTheme.typography.bodySmall,
                )
                // And why, which is the part they can act on.
                Text(
                    text = stringResource(error.messageRes),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}

@Composable
private fun DeckGrid(decks: List<DeckSummary>, onDeckClick: (DeckSummary) -> Unit) {
    LazyVerticalGrid(
        // Adaptive rather than a fixed count so the grid reflows sensibly on
        // tablets and in landscape.
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(decks, key = { it.publicId }) { deck ->
            DeckCard(deck, onClick = { onDeckClick(deck) })
        }
    }
}

@Composable
private fun DeckCard(deck: DeckSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Scryfall/Moxfield art crops are consistently 626x457.
                    .aspectRatio(626f / 457f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (deck.imageUrl != null) {
                    AsyncImage(
                        model = deck.imageUrl,
                        contentDescription = deck.commander,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(Modifier.padding(10.dp)) {
                Text(
                    text = deck.name.ifBlank { stringResource(R.string.deck_untitled) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!deck.commander.isNullOrBlank()) {
                    Text(
                        text = deck.commander,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

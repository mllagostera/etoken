package com.etoken.ui.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.etoken.R
import com.etoken.domain.model.TokenCard
import com.etoken.ui.common.ActionButton
import com.etoken.ui.common.BackButton
import com.etoken.ui.common.ErrorView
import com.etoken.ui.common.LoadingView
import com.etoken.ui.common.MessageView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokensScreen(
    onTokenClick: (TokenCard) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TokensViewModel = viewModel(factory = TokensViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inPlay by viewModel.inPlay.collectAsStateWithLifecycle()
    var confirmingNewGame by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.deckName.ifBlank { stringResource(R.string.tokens_title) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val current = state
                        if (current is TokensUiState.Ready) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.tokens_count,
                                    current.tokens.size,
                                    current.tokens.size,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    // Only offered when there is actually something on the table.
                    if (inPlay.isNotEmpty()) {
                        ActionButton(
                            icon = R.drawable.ic_new_game,
                            description = stringResource(R.string.new_game),
                            onClick = { confirmingNewGame = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val current = state) {
                TokensUiState.Loading -> LoadingView()
                TokensUiState.Empty -> MessageView(stringResource(R.string.tokens_empty))
                is TokensUiState.Failed -> ErrorView(current.error, onRetry = viewModel::load)
                is TokensUiState.Ready -> TokenGrid(current.tokens, inPlay, onTokenClick)
            }
        }
    }

    if (confirmingNewGame) {
        // Destructive and global, so it asks first.
        AlertDialog(
            onDismissRequest = { confirmingNewGame = false },
            title = { Text(stringResource(R.string.new_game_title)) },
            text = { Text(stringResource(R.string.new_game_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startNewGame()
                        confirmingNewGame = false
                    },
                ) {
                    Text(stringResource(R.string.action_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingNewGame = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TokenGrid(
    tokens: List<TokenCard>,
    inPlay: Map<String, Int>,
    onTokenClick: (TokenCard) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tokens, key = { it.id }) { token ->
            TokenCell(
                token = token,
                inPlay = inPlay[token.id] ?: 0,
                onClick = { onTokenClick(token) },
            )
        }
    }
}

@Composable
private fun TokenCell(token: TokenCard, inPlay: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Magic card proportions, as Scryfall serves them.
                .aspectRatio(488f / 680f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (token.imageUrl != null) {
                AsyncImage(
                    model = token.imageUrl,
                    contentDescription = token.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Scryfall has a handful of tokens with no artwork on file.
                Text(
                    text = token.name,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                )
            }

            if (inPlay > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(bottomStart = 10.dp),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        text = "×$inPlay",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        Text(
            text = token.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (token.typeLine.isNotBlank()) {
            Text(
                text = token.typeLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (token.createdBy.isNotEmpty()) {
            Text(
                text = stringResource(R.string.tokens_created_by, token.createdBy.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

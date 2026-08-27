package com.etoken.ui.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.etoken.R
import com.etoken.domain.model.SummoningSickness
import com.etoken.domain.model.TokenBoard
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
    /**
     * Marked in the grid, so that beside an open board pane it is visible which
     * token the board belongs to. Always null on a phone, where the board is a
     * screen of its own and the grid is not on show at the same time.
     */
    selectedTokenId: String? = null,
    viewModel: TokensViewModel = viewModel(factory = TokensViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inPlay by viewModel.inPlay.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    // Saveable, not remember: a rotation with the dialog open would otherwise
    // dismiss it, which on a destructive confirmation is the worst way to lose it.
    var confirmingNewGame by rememberSaveable { mutableStateOf(false) }
    val tokensInPlay = inPlay.values.sumOf { it.total }

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
                                // "3 de 12" while the filter is hiding something,
                                // and the plain count otherwise: the deck's total
                                // is only worth naming when it is not what is on
                                // screen.
                                text = if (current.onlyInPlay) {
                                    stringResource(
                                        R.string.tokens_filtered_count,
                                        current.tokens.size,
                                        current.total,
                                    )
                                } else {
                                    pluralStringResource(
                                        R.plurals.tokens_count,
                                        current.tokens.size,
                                        current.tokens.size,
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    // Offered whenever there is a change to take back, which
                    // after a new game is the whole table.
                    if (canUndo) {
                        ActionButton(
                            icon = R.drawable.ic_undo,
                            description = stringResource(R.string.action_undo),
                            onClick = viewModel::undo,
                        )
                    }
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
                is TokensUiState.Ready -> TokensReady(
                    state = current,
                    inPlay = inPlay,
                    selectedTokenId = selectedTokenId,
                    onToggleFilter = viewModel::toggleOnlyInPlay,
                    onTokenClick = onTokenClick,
                )
            }
        }
    }

    if (confirmingNewGame) {
        // Destructive and global, so it asks first.
        AlertDialog(
            onDismissRequest = { confirmingNewGame = false },
            title = { Text(stringResource(R.string.new_game_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Naming the number makes the cost concrete: "12 tokens"
                    // lands where "todos los tokens" does not.
                    Text(
                        text = pluralStringResource(
                            R.plurals.new_game_body,
                            tokensInPlay,
                            tokensInPlay,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.new_game_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
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
private fun TokensReady(
    state: TokensUiState.Ready,
    inPlay: Map<String, TokenBoard>,
    selectedTokenId: String?,
    onToggleFilter: () -> Unit,
    onTokenClick: (TokenCard) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // A filter with nothing to filter is noise, so the chip arrives with the
        // first token on the table. It stays while it is on even after the table
        // empties -- taking it away then would leave the grid filtered down to
        // nothing with no way to say otherwise.
        if (inPlay.isNotEmpty() || state.onlyInPlay) {
            InPlayFilter(selected = state.onlyInPlay, onToggle = onToggleFilter)
        }

        // weight(1f) rather than fillMaxSize: the grid takes what is left below
        // the chip, and the whole height when there is no chip.
        Box(Modifier.weight(1f)) {
            if (state.tokens.isEmpty()) {
                // Reachable only with the filter on -- a deck that creates no
                // tokens at all is TokensUiState.Empty, one screen up.
                MessageView(stringResource(R.string.tokens_none_in_play))
            } else {
                TokenGrid(state.tokens, inPlay, selectedTokenId, onTokenClick)
            }
        }
    }
}

@Composable
private fun InPlayFilter(selected: Boolean, onToggle: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(stringResource(R.string.tokens_filter_in_play)) },
        leadingIcon = if (selected) {
            {
                Icon(
                    // Decorative: the label beside it already says what is on, and
                    // the chip reports its own selected state to TalkBack.
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        // The same gutter the deck grid's search field sits in, so the two
        // screens line up when one is a pane beside the other.
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun TokenGrid(
    tokens: List<TokenCard>,
    inPlay: Map<String, TokenBoard>,
    selectedTokenId: String?,
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
                board = inPlay[token.id] ?: TokenBoard(),
                selected = token.id == selectedTokenId,
                onClick = { onTokenClick(token) },
            )
        }
    }
}

@Composable
private fun TokenCell(
    token: TokenCard,
    board: TokenBoard,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val inPlay = board.total
    // Only a board with a single stack can name the counters its copies carry;
    // with two the grid says nothing and leaves the answer to the board screen,
    // which has room to show a stack at a time. Zero counters is not worth a
    // badge either -- it is what an untouched token already looks like.
    val counters = board.uniformPlusOneCounters?.takeIf { it > 0 }
    // Whether the copies can attack is the other half of "what have I got
    // out?", and it used to need the board screen to answer. Nothing waiting
    // draws nothing: a token that can attack is what a cell already looks like.
    val sick = board.summoningSickness

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
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
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
                CornerBadge(
                    text = "×$inPlay",
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(bottomStart = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }

            // The bottom two badges share the row in halves rather than being
            // aligned to their corners: a cell is about 150dp wide and the
            // sickness label is a full word in every language -- German's is
            // 19 characters -- so at their own corners the two would meet in
            // the middle and overlap. A half each means the long one
            // truncates, which is legible; overlapping is not.
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomStart) {
                    SummoningSicknessBadge(sick)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomEnd) {
                    if (counters != null) {
                        // Bottom-right, where the art's own power/toughness box
                        // sits: a Magic player already looks there for a
                        // creature's size.
                        CornerBadge(
                            text = stringResource(R.string.stack_counters_chip, counters),
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(topStart = 10.dp),
                        )
                    }
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

/**
 * The grid's summoning-sickness badge, or nothing at all.
 *
 * Names the state alone when the whole table is waiting, and adds a count —
 * "Sick ×2" in English — when only part of it is: the number is worth the room
 * only when it is not the total already in the opposite corner. A token that can attack draws nothing: the absence is
 * the common case, and a badge on every cell would say nothing by being on all
 * of them.
 *
 * The word is [R.string.stack_sick], the same one the board screen's chip uses,
 * so the two cannot end up naming one state differently.
 */
@Composable
private fun SummoningSicknessBadge(sickness: SummoningSickness, modifier: Modifier = Modifier) {
    val label = when (sickness) {
        SummoningSickness.None -> null
        SummoningSickness.All -> stringResource(R.string.stack_sick)
        is SummoningSickness.Some -> stringResource(
            R.string.tokens_sick_some,
            stringResource(R.string.stack_sick),
            sickness.count,
        )
    }

    if (label != null) {
        CornerBadge(
            text = label,
            // The same tertiary container the board screen's sick chip uses, so
            // one state is one colour wherever it is shown.
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(topEnd = 10.dp),
            modifier = modifier,
        )
    }
}

/** One badge sitting in a corner of a cell's artwork. */
@Composable
private fun CornerBadge(
    text: String,
    container: Color,
    content: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Surface(color = container, contentColor = content, shape = shape, modifier = modifier) {
        Text(
            text = text,
            style = style,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

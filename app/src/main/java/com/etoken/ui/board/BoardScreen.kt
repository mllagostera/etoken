package com.etoken.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.etoken.R
import com.etoken.domain.PowerToughness
import com.etoken.ui.common.ActionButton
import com.etoken.ui.common.BackButton
import com.etoken.ui.common.ErrorView
import com.etoken.ui.common.LoadingView
import com.etoken.ui.common.MessageView
import com.etoken.ui.tokens.AddTokenDialog
import com.etoken.ui.tokens.TokenPicker

/**
 * The battlefield: every entry the player has put into play, and the "+" that
 * makes more.
 *
 * The deck opens onto this rather than onto a grid of what it could create,
 * because what a player looks at between turns is the table. What the deck can
 * create is one tap away, behind the "+" — a modal sheet on a phone, and past
 * [TWO_PANE_WIDTH] a pane that stays open beside the table, since a tablet has
 * the room to keep both on screen.
 *
 * Every entry is its own cell. A tap turns it — tapping and untapping is what
 * happens most at a table, and it wants to be one gesture — and a long press
 * opens everything else about it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BoardViewModel = viewModel(factory = BoardViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready = state as? BoardUiState.Ready

    // Saveable, not remember: a rotation with a dialog open would otherwise
    // dismiss it, which on a destructive confirmation is the worst way to lose
    // it. The entry ones hold an *id* rather than the entry: ids survive being
    // written to a Bundle, and looking the entry up again means a dialog can
    // never act on something that left the table while it was open.
    var picking by rememberSaveable { mutableStateOf(false) }
    var addingTokenId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var countersForEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmingNewGame by rememberSaveable { mutableStateOf(false) }

    val adding = addingTokenId?.let { id -> ready?.deckTokens?.firstOrNull { it.id == id } }
    val detail = detailEntryId?.let { id -> ready?.entries?.firstOrNull { it.entry.id == id } }
    val countersTarget =
        countersForEntryId?.let { id -> ready?.entries?.firstOrNull { it.entry.id == id } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TWO_PANE_WIDTH

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = viewModel.deckName.ifBlank {
                                    stringResource(R.string.board_title)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (ready != null && !ready.isEmpty) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.board_in_play_count,
                                        ready.total,
                                        ready.total,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        // Offered whenever there is a change to take back, which
                        // after a new game is the whole table.
                        if (ready?.canUndo == true) {
                            ActionButton(
                                icon = R.drawable.ic_undo,
                                description = stringResource(R.string.action_undo),
                                onClick = viewModel::undo,
                            )
                        }
                        // Only offered when there is actually something on the table.
                        if (ready != null && !ready.isEmpty) {
                            ActionButton(
                                icon = R.drawable.ic_new_game,
                                description = stringResource(R.string.new_game),
                                onClick = { confirmingNewGame = true },
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                // On a tablet the picker is already on screen, so a button to
                // open it would open nothing.
                if (ready != null && !twoPane) {
                    ExtendedFloatingActionButton(
                        onClick = { picking = true },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(R.string.board_add_token)) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (val current = state) {
                    BoardUiState.Loading -> LoadingView()
                    is BoardUiState.Failed -> ErrorView(current.error, onRetry = viewModel::load)
                    is BoardUiState.Ready -> if (twoPane) {
                        Row(Modifier.fillMaxSize()) {
                            TokenPicker(
                                tokens = current.deckTokens,
                                board = current.board,
                                onPick = { addingTokenId = it.id },
                                modifier = Modifier.weight(1f),
                            )
                            VerticalDivider()
                            BoardPane(
                                state = current,
                                viewModel = viewModel,
                                onOpenEntry = { detailEntryId = it },
                                modifier = Modifier.weight(1.15f),
                            )
                        }
                    } else {
                        BoardPane(
                            state = current,
                            viewModel = viewModel,
                            onOpenEntry = { detailEntryId = it },
                            // Room for the button that floats over the grid.
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 12.dp,
                                bottom = 88.dp,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (picking && ready != null) {
        ModalBottomSheet(
            onDismissRequest = { picking = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.board_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                TokenPicker(
                    tokens = ready.deckTokens,
                    board = ready.board,
                    onPick = { addingTokenId = it.id },
                    contentPadding = PaddingValues(12.dp),
                )
            }
        }
    }

    adding?.let { token ->
        AddTokenDialog(
            token = token,
            onDismiss = { addingTokenId = null },
            onConfirm = { quantity, copying, tapped ->
                viewModel.add(token, quantity, copying, tapped)
                addingTokenId = null
                // The sheet has done its job; the table is what you want to see
                // once the tokens are on it.
                picking = false
            },
        )
    }

    detail?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { detailEntryId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            EntryDetail(
                on = entry,
                onQuantity = { delta -> viewModel.changeQuantity(entry.entry.id, delta) },
                onCounters = { delta -> viewModel.changeCounters(entry.entry.id, delta) },
                onToggleSick = {
                    viewModel.setSummoningSick(entry.entry.id, !entry.entry.summoningSick)
                },
                onToggleTapped = { viewModel.setTapped(entry.entry.id, !entry.entry.tapped) },
                onCountersForSome = { countersForEntryId = entry.entry.id },
                onRemove = {
                    viewModel.remove(entry.entry.id)
                    detailEntryId = null
                },
            )
        }
    }

    countersTarget?.let { target ->
        NumberDialog(
            title = stringResource(R.string.dialog_how_many_of, target.entry.quantity),
            initial = 1,
            max = target.entry.quantity,
            onDismiss = { countersForEntryId = null },
            onConfirm = { amount ->
                viewModel.changeCounters(target.entry.id, delta = 1, appliesTo = amount)
                countersForEntryId = null
            },
        )
    }

    if (confirmingNewGame && ready != null) {
        // Destructive and global, so it asks first.
        AlertDialog(
            onDismissRequest = { confirmingNewGame = false },
            title = { Text(stringResource(R.string.new_game_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Naming the number makes the cost concrete: "12 tokens"
                    // lands where "everything" does not.
                    Text(
                        text = pluralStringResource(
                            R.plurals.new_game_body,
                            ready.total,
                            ready.total,
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
                        viewModel.newGame()
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
private fun BoardPane(
    state: BoardUiState.Ready,
    viewModel: BoardViewModel,
    onOpenEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
    Column(modifier.fillMaxSize()) {
        if (!state.isEmpty) {
            TurnActions(
                sickCount = state.board.summoningSickCount,
                onBeginTurn = viewModel::beginTurn,
                onCounterAll = viewModel::addCounterToAll,
            )
        }

        // weight(1f) rather than fillMaxSize: the grid takes what is left below
        // the actions, and the whole height when there are none.
        Box(Modifier.weight(1f)) {
            if (state.isEmpty) {
                MessageView(
                    message = stringResource(R.string.board_empty),
                    detail = stringResource(
                        if (state.deckTokens.isEmpty()) {
                            R.string.tokens_empty
                        } else {
                            R.string.board_empty_detail
                        },
                    ),
                )
            } else {
                LazyVerticalGrid(
                    // Tighter than the picker's: an entry cell carries art and
                    // badges, not a type line and a list of what makes it, and
                    // a table can hold a lot of them.
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.entries, key = { it.entry.id }) { entry ->
                        EntryCell(
                            on = entry,
                            onTap = { viewModel.setTapped(entry.entry.id, !entry.entry.tapped) },
                            onOpen = { onOpenEntry(entry.entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TurnActions(sickCount: Int, onBeginTurn: () -> Unit, onCounterAll: () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onBeginTurn, enabled = sickCount > 0) {
            Text(stringResource(R.string.board_begin_turn), maxLines = 1)
        }
        OutlinedButton(onClick = onCounterAll) {
            Text(stringResource(R.string.board_counter_all), maxLines = 1)
        }
    }
}

/**
 * One entry, drawn as the card it is.
 *
 * A tap turns it, which is the gesture a player makes most; everything else —
 * counters, sickness, the count, taking it off the table — is a long press
 * away, in [EntryDetail]. A tapped entry is dimmed as well as labelled, so a
 * table half-way through combat reads at a glance rather than by badge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCell(on: EntryOnBoard, onTap: () -> Unit, onOpen: () -> Unit) {
    val entry = on.entry
    val token = on.token

    Column(
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(488f / 680f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val art = Modifier.fillMaxSize().alpha(if (entry.tapped) TAPPED_ALPHA else 1f)
            if (token.imageUrl != null) {
                AsyncImage(
                    model = token.imageUrl,
                    contentDescription = token.name,
                    contentScale = ContentScale.Fit,
                    modifier = art,
                )
            } else {
                // Scryfall has a handful of tokens with no artwork on file.
                Text(
                    text = token.name,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(6.dp),
                )
            }

            if (entry.quantity > 1) {
                CornerBadge(
                    text = "×${entry.quantity}",
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }

            if (entry.tapped) {
                CornerBadge(
                    text = stringResource(R.string.entry_tapped),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }

            // The bottom two badges share the row in halves rather than being
            // aligned to their corners: a cell is narrow and the sickness label
            // is a full word in every language -- German's is 19 characters --
            // so at their own corners the two would meet in the middle and
            // overlap. A half each means the long one truncates, which is
            // legible; overlapping is not.
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomStart) {
                    if (entry.summoningSick && token.isCreature) {
                        CornerBadge(
                            text = stringResource(R.string.entry_sick),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = RoundedCornerShape(topEnd = 8.dp),
                        )
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomEnd) {
                    if (entry.plusOneCounters > 0) {
                        // Bottom-right, where the art's own power/toughness box
                        // sits: a Magic player already looks there for a
                        // creature's size.
                        CornerBadge(
                            text = stringResource(
                                R.string.entry_counters_chip,
                                entry.plusOneCounters,
                            ),
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(topStart = 8.dp),
                        )
                    }
                }
            }
        }

        Text(
            text = entry.copying ?: token.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PowerToughness.display(token.power, token.toughness, entry.plusOneCounters)?.let { size ->
            Text(
                text = size,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Everything about one entry that a tap is too blunt to say. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryDetail(
    on: EntryOnBoard,
    onQuantity: (Int) -> Unit,
    onCounters: (Int) -> Unit,
    onToggleSick: () -> Unit,
    onToggleTapped: () -> Unit,
    onCountersForSome: () -> Unit,
    onRemove: () -> Unit,
) {
    val entry = on.entry
    val token = on.token

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .aspectRatio(488f / 680f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                if (token.imageUrl != null) {
                    AsyncImage(
                        model = token.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = token.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.copying?.let { copied ->
                    Text(
                        text = copied,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(token.typeLine, style = MaterialTheme.typography.bodySmall)
                PowerToughness.display(token.power, token.toughness, entry.plusOneCounters)
                    ?.let { size ->
                        Text(size, style = MaterialTheme.typography.titleSmall)
                    }
                if (token.hasHaste) {
                    // Said out loud, because the absence of a sickness badge is
                    // otherwise indistinguishable from the app getting it wrong.
                    Text(
                        text = stringResource(R.string.board_haste_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Non-creature tokens are never summoning sick, so there is nothing
            // here for that chip to say or to toggle.
            if (token.isCreature) {
                StateChip(
                    text = if (entry.summoningSick) {
                        stringResource(R.string.entry_sick)
                    } else {
                        stringResource(R.string.entry_ready)
                    },
                    highlighted = entry.summoningSick,
                    onClick = onToggleSick,
                )
            }
            StateChip(
                text = if (entry.tapped) {
                    stringResource(R.string.entry_tapped)
                } else {
                    stringResource(R.string.entry_untapped)
                },
                highlighted = entry.tapped,
                onClick = onToggleTapped,
            )
        }

        HorizontalDivider()

        Stepper(
            label = stringResource(R.string.entry_quantity),
            value = entry.quantity.toString(),
            onMinus = { onQuantity(-1) },
            onPlus = { onQuantity(1) },
            minusDescription = stringResource(R.string.entry_remove_one),
            plusDescription = stringResource(R.string.entry_add_one),
        )
        Stepper(
            label = stringResource(R.string.entry_counters),
            value = entry.plusOneCounters.toString(),
            onMinus = { onCounters(-1) },
            onPlus = { onCounters(1) },
            minusDescription = stringResource(R.string.entry_counter_remove),
            plusDescription = stringResource(R.string.entry_counter_add),
        )

        if (entry.quantity > 1) {
            TextButton(onClick = onCountersForSome, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.entry_counter_some))
            }
        }

        TextButton(onClick = onRemove, contentPadding = PaddingValues(0.dp)) {
            Text(
                text = stringResource(R.string.entry_remove),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusDescription: String,
    plusDescription: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(32.dp),
        )
        FilledTonalIconButton(onClick = onPlus) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StateChip(text: String, highlighted: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (highlighted) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    Surface(color = container, contentColor = content, shape = shape, modifier = modifier) {
        Text(
            text = text,
            style = style,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun NumberDialog(
    title: String,
    initial: Int,
    max: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 1..max

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() }.take(3) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Dimmed rather than redrawn sideways: a rotated card would leave its cell. */
private const val TAPPED_ALPHA = 0.45f

/**
 * Material's "expanded" width, and the point where two panes stop being a
 * squeeze: below it the picker's art and the table's cells have nowhere to go.
 * A tablet in portrait is usually around 800dp, so it stays on one pane —
 * deliberately, since half of that is narrower than the phone layout.
 *
 * Read from the layout's own constraints rather than through
 * `material3-window-size-class`: it is the same 840dp threshold, and this app
 * has one place that asks the question.
 */
private val TWO_PANE_WIDTH = 840.dp

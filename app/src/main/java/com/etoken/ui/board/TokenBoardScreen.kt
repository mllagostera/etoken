package com.etoken.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.etoken.R
import com.etoken.domain.PowerToughness
import com.etoken.domain.model.TokenBoard
import com.etoken.domain.model.TokenCard
import com.etoken.domain.model.TokenStack
import com.etoken.ui.common.ActionButton
import com.etoken.ui.common.BackButton
import com.etoken.ui.common.ErrorView
import com.etoken.ui.common.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenBoardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TokenBoardViewModel = viewModel(factory = TokenBoardViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? TokenBoardUiState.Ready)?.token?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    // In the bar rather than among the quick actions: it undoes
                    // the last change to any board, which is not one of this
                    // board's edits.
                    ActionButton(
                        icon = R.drawable.ic_undo,
                        description = stringResource(R.string.action_undo),
                        onClick = viewModel::undo,
                        enabled = (state as? TokenBoardUiState.Ready)?.canUndo == true,
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val current = state) {
                TokenBoardUiState.Loading -> LoadingView()
                is TokenBoardUiState.Failed -> ErrorView(current.error, onRetry = viewModel::load)
                is TokenBoardUiState.Ready -> BoardContent(current.token, current.board, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardContent(token: TokenCard, board: TokenBoard, viewModel: TokenBoardViewModel) {
    // Saveable so a rotation doesn't throw away a half-answered dialog. The
    // second one holds a stack *id* rather than the stack: ids survive being
    // written to a Bundle, and looking the stack up again means the dialog can
    // never act on a copy that merged or emptied while it was open.
    var askingHowMany by rememberSaveable { mutableStateOf(false) }
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    var countersForStackId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Read at the moment tokens are added, so it never applies retroactively
    // to what is already on the battlefield.
    var enterTapped by rememberSaveable { mutableStateOf(false) }
    // A copy token means nothing without saying what it copies, so the amount
    // (and whether it enters tapped) waits here until the name arrives.
    var pendingCopyQuantity by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingCopyTapped by rememberSaveable { mutableStateOf(false) }
    val countersTarget = countersForStackId?.let { id -> board.stacks.firstOrNull { it.id == id } }

    val addTokens: (Int) -> Unit = { amount ->
        if (token.isCopy) {
            pendingCopyQuantity = amount
            pendingCopyTapped = enterTapped
        } else {
            viewModel.add(amount, tapped = enterTapped)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TokenHeader(token) }
        item { BoardSummary(token, board) }
        item {
            QuickActions(
                board = board,
                viewModel = viewModel,
                onAdd = addTokens,
                onAskHowMany = { askingHowMany = true },
                onAskClear = { confirmingClear = true },
                enterTapped = enterTapped,
                onEnterTappedChange = { enterTapped = it },
            )
        }

        if (board.isEmpty) {
            item {
                Text(
                    text = stringResource(R.string.board_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        items(board.stacks, key = { it.id }) { stack ->
            StackCard(
                stack = stack,
                token = token,
                onQuantity = { delta -> viewModel.changeQuantity(stack.id, delta) },
                onCounters = { delta -> viewModel.changeCounters(stack.id, delta) },
                onToggleSick = { viewModel.setSummoningSick(stack.id, !stack.summoningSick) },
                onToggleTapped = { viewModel.setTapped(stack.id, !stack.tapped) },
                onCountersForSome = { countersForStackId = stack.id },
            )
        }
    }

    if (askingHowMany) {
        NumberDialog(
            title = stringResource(R.string.dialog_how_many),
            initial = 1,
            max = 999,
            onDismiss = { askingHowMany = false },
            onConfirm = { amount ->
                askingHowMany = false
                addTokens(amount)
            },
        )
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.clear_title)) },
            text = {
                Text(pluralStringResource(R.plurals.clear_body, board.total, board.total))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clear()
                        confirmingClear = false
                    },
                ) {
                    Text(stringResource(R.string.board_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingCopyQuantity?.let { amount ->
        NameDialog(
            title = stringResource(R.string.copy_of_title),
            label = stringResource(R.string.copy_of_hint),
            onDismiss = { pendingCopyQuantity = null },
            onConfirm = { name ->
                viewModel.add(amount, copying = name, tapped = pendingCopyTapped)
                pendingCopyQuantity = null
            },
        )
    }

    countersTarget?.let { stack ->
        NumberDialog(
            title = stringResource(R.string.dialog_how_many_of, stack.quantity),
            initial = 1,
            max = stack.quantity,
            onDismiss = { countersForStackId = null },
            onConfirm = { amount ->
                viewModel.changeCounters(stack.id, delta = 1, appliesTo = amount)
                countersForStackId = null
            },
        )
    }
}

@Composable
private fun TokenHeader(token: TokenCard) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(488f / 680f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            if (token.imageUrl != null) {
                AsyncImage(
                    model = token.imageUrl,
                    contentDescription = token.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(token.typeLine, style = MaterialTheme.typography.bodyMedium)

            PowerToughness.display(token.power, token.toughness, counters = 0)?.let { base ->
                Text(
                    text = stringResource(R.string.board_base, base),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (token.hasHaste) {
                StateBadge(
                    text = stringResource(R.string.board_haste),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                // Said out loud, because the absence of "Mareo" on a stack is
                // otherwise indistinguishable from the app getting it wrong.
                Text(
                    text = stringResource(R.string.board_haste_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (token.createdBy.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.tokens_created_by,
                        token.createdBy.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BoardSummary(token: TokenCard, board: TokenBoard) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = board.total.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Column {
                Text(
                    text = stringResource(R.string.board_in_play),
                    style = MaterialTheme.typography.labelLarge,
                )
                // A non-creature token can never be summoning sick, so the line
                // would only ever read "None" -- true, but not worth a row.
                if (token.isCreature) {
                    Text(
                        text = if (board.summoningSickCount == 0) {
                            stringResource(R.string.board_none_sick)
                        } else {
                            stringResource(R.string.board_sick, board.summoningSickCount)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(
    board: TokenBoard,
    viewModel: TokenBoardViewModel,
    onAdd: (Int) -> Unit,
    onAskHowMany: () -> Unit,
    onAskClear: () -> Unit,
    enterTapped: Boolean,
    onEnterTappedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The row itself is the toggle target, Switch style: without it the
        // click action sits on the Switch alone, and tapping the label (where
        // Material's own switches respond) does nothing.
        Row(
            modifier = Modifier.toggleable(
                value = enterTapped,
                onValueChange = onEnterTappedChange,
                role = Role.Switch,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.board_enter_tapped),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = enterTapped, onCheckedChange = null)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Fixed steps cover the common triggers; the dialog covers Krenko,
            // where the count is whatever is on the battlefield right now.
            listOf(1, 2, 5).forEach { amount ->
                FilledTonalButton(
                    onClick = { onAdd(amount) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+$amount")
                }
            }
            FilledTonalButton(onClick = onAskHowMany, modifier = Modifier.weight(1.4f)) {
                Text(stringResource(R.string.board_add_other), maxLines = 1)
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::beginTurn,
                enabled = board.summoningSickCount > 0,
            ) {
                Text(stringResource(R.string.board_begin_turn))
            }
            OutlinedButton(onClick = viewModel::addCounterToAll, enabled = !board.isEmpty) {
                Text(stringResource(R.string.board_counter_all))
            }
            // Destructive, so it asks -- the same courtesy "Nueva partida" gets.
            TextButton(onClick = onAskClear, enabled = !board.isEmpty) {
                Text(stringResource(R.string.board_clear))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackCard(
    stack: TokenStack,
    token: TokenCard,
    onQuantity: (Int) -> Unit,
    onCounters: (Int) -> Unit,
    onToggleSick: () -> Unit,
    onToggleTapped: () -> Unit,
    onCountersForSome: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "${stack.quantity} ×",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                PowerToughness.display(token.power, token.toughness, stack.plusOneCounters)
                    ?.let { size ->
                        Text(size, style = MaterialTheme.typography.titleMedium)
                    }
            }

            stack.copying?.let { copied ->
                Text(
                    text = copied,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stack.plusOneCounters > 0) {
                    StateBadge(
                        text = stringResource(R.string.stack_counters_chip, stack.plusOneCounters),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                // Non-creature tokens are never summoning sick, so there is
                // nothing here for that chip to say or to toggle.
                if (token.isCreature) {
                    StateBadge(
                        text = if (stack.summoningSick) {
                            stringResource(R.string.stack_sick)
                        } else {
                            stringResource(R.string.stack_ready)
                        },
                        container = if (stack.summoningSick) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        content = if (stack.summoningSick) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = onToggleSick,
                    )
                }
                StateBadge(
                    text = if (stack.tapped) {
                        stringResource(R.string.stack_tapped)
                    } else {
                        stringResource(R.string.stack_untapped)
                    },
                    container = if (stack.tapped) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    content = if (stack.tapped) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = onToggleTapped,
                )
            }

            HorizontalDivider()

            Stepper(
                label = stringResource(R.string.stack_quantity),
                value = stack.quantity.toString(),
                onMinus = { onQuantity(-1) },
                onPlus = { onQuantity(1) },
                minusDescription = stringResource(R.string.stack_remove),
                plusDescription = stringResource(R.string.stack_add),
            )
            Stepper(
                label = stringResource(R.string.stack_counters),
                value = stack.plusOneCounters.toString(),
                onMinus = { onCounters(-1) },
                onPlus = { onCounters(1) },
                minusDescription = stringResource(R.string.stack_counter_remove),
                plusDescription = stringResource(R.string.stack_counter_add),
            )

            if (stack.quantity > 1) {
                TextButton(onClick = onCountersForSome, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.stack_counter_some))
                }
            }
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
private fun StateBadge(
    text: String,
    container: Color,
    content: Color,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    val body: @Composable () -> Unit = {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }

    Surface(
        color = container,
        contentColor = content,
        shape = shape,
        modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
    ) {
        body()
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val trimmed = text.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = trimmed.isNotEmpty()) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
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

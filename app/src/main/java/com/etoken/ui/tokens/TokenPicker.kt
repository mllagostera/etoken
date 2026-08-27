package com.etoken.ui.tokens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.etoken.R
import com.etoken.domain.model.GameBoard
import com.etoken.domain.model.TokenCard
import com.etoken.ui.common.MessageView

/**
 * Everything the open deck can create, as the thing you add from.
 *
 * This grid used to be the screen a deck opened onto, with the battlefield one
 * tap further in. It is now the other way round: the table is the screen, and
 * this is what the "+" opens — a modal sheet on a phone, the left-hand pane on
 * a tablet. Picking a token here never changes the board on its own; it asks
 * [AddTokenDialog] how many first.
 */
@Composable
fun TokenPicker(
    tokens: List<TokenCard>,
    board: GameBoard,
    onPick: (TokenCard) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
    if (tokens.isEmpty()) {
        MessageView(stringResource(R.string.tokens_empty), modifier = modifier)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tokens, key = { it.id }) { token ->
            PickerCell(
                token = token,
                inPlay = board.countOf(token.id),
                onClick = { onPick(token) },
            )
        }
    }
}

@Composable
private fun PickerCell(token: TokenCard, inPlay: Int, onClick: () -> Unit) {
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

            // What is already out, so the picker answers "have I made these
            // yet?" without going back to the table.
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
                text = stringResource(
                    R.string.tokens_created_by,
                    token.createdBy.joinToString(", "),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * How many copies of the picked token to make, and in what state.
 *
 * One dialog rather than the chain of them this replaced: the amount, whether
 * they enter tapped, and — for a Copy token — what they are copies of are all
 * answered before anything reaches the board. A press of "OK" is exactly one
 * new entry, and one step of undo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTokenDialog(
    token: TokenCard,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Int, copying: String?, tapped: Boolean) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("1") }
    var tapped by rememberSaveable { mutableStateOf(false) }
    var copying by rememberSaveable { mutableStateOf("") }

    val quantity = amount.toIntOrNull()
    val copyName = copying.trim()
    // A copy token means nothing without saying what it copies, so that is the
    // one field that can hold the dialog shut.
    val valid = quantity != null && quantity in 1..MAX_AT_ONCE &&
        (!token.isCopy || copyName.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(token.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.dialog_how_many),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Fixed steps cover the common triggers; the field covers
                // Krenko, where the count is whatever is on the table already.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_AMOUNTS.forEach { quick ->
                        FilterChip(
                            selected = quantity == quick,
                            onClick = { amount = quick.toString() },
                            label = { Text("+$quick") },
                        )
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input -> amount = input.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                if (token.isCopy) {
                    OutlinedTextField(
                        value = copying,
                        onValueChange = { copying = it },
                        label = { Text(stringResource(R.string.copy_of_hint)) },
                        singleLine = true,
                    )
                }

                // The row itself is the toggle target, Switch style: without it
                // the click action sits on the Switch alone, and tapping the
                // label -- where Material's own switches respond -- does nothing.
                Row(
                    modifier = Modifier.toggleable(
                        value = tapped,
                        onValueChange = { tapped = it },
                        role = Role.Switch,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.board_enter_tapped),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = tapped, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    quantity?.let { onConfirm(it, copyName.takeIf { name -> name.isNotEmpty() }, tapped) }
                },
                enabled = valid,
            ) {
                Text(stringResource(R.string.board_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val QUICK_AMOUNTS = listOf(1, 2, 3, 5)

/** Three digits is what the field accepts, and no trigger asks for more. */
private const val MAX_AT_ONCE = 999

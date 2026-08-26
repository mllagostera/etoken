package com.etoken.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.etoken.R
import com.etoken.domain.AppLanguage

/**
 * The language button and the dialog behind it.
 *
 * It lives here rather than in a screen package because it belongs to no
 * screen in particular: today it sits in the corner of the username screen —
 * the one screen every launch passes through — and a settings screen, if one
 * ever exists, would want the same thing.
 */
@Composable
fun LanguageButton(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable and not remember: choosing a language recreates the activity,
    // and so does a rotation. Neither should reopen this dialog.
    var open by rememberSaveable { mutableStateOf(false) }

    Row(modifier) {
        ActionButton(
            icon = R.drawable.ic_language,
            description = stringResource(R.string.action_language),
            onClick = { open = true },
        )
    }

    if (open) {
        LanguageDialog(
            current = current,
            onSelect = {
                open = false
                onSelect(it)
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            // Eight rows do not fit under a title on a short phone in
            // landscape, and an AlertDialog does not scroll its body for you.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                AppLanguage.entries.forEach { language ->
                    LanguageRow(
                        language = language,
                        selected = language == current,
                        onClick = { onSelect(language) },
                    )
                }
            }
        },
        // No confirm button: picking a row is the confirmation, and there is
        // nothing to accept afterwards.
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun LanguageRow(language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row, not just the radio button: the tap target for a
            // list of eight is the line of text people aim at.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Null: the row carries the click and the selected state for
        // accessibility, so the button repeating both would announce twice.
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(labelOf(language)))
    }
}

@StringRes
private fun labelOf(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_en
    AppLanguage.SPANISH -> R.string.language_es
    AppLanguage.CATALAN -> R.string.language_ca
    AppLanguage.FRENCH -> R.string.language_fr
    AppLanguage.GERMAN -> R.string.language_de
    AppLanguage.ITALIAN -> R.string.language_it
    AppLanguage.JAPANESE -> R.string.language_ja
}

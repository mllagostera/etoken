package com.etoken.ui.username

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etoken.R

@Composable
fun UsernameScreen(
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsernameViewModel = viewModel(factory = UsernameViewModel.Factory),
) {
    val username by viewModel.username.collectAsStateWithLifecycle()
    val trimmed = username.trim()

    fun submit() {
        if (trimmed.isEmpty()) return
        viewModel.remember(trimmed)
        onSubmit(trimmed)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // This screen has no Scaffold, so nothing else consumes the insets
            // that enableEdgeToEdge() opened up: without this the field sits
            // under the status bar, and the button under the gesture bar.
            .systemBarsPadding()
            .imePadding()
            // Landscape leaves barely any height once the keyboard is up, and
            // the submit button is the last thing in the column — it has to be
            // reachable by scrolling rather than clipped off the bottom.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.username_prompt),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        OutlinedTextField(
            value = username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text(stringResource(R.string.username_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )

        Button(
            onClick = { submit() },
            enabled = trimmed.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_load_decks))
        }

        // Under the button rather than above it: the prompt already says
        // "public decks", and this is the small print that explains what that
        // leaves out. Said here so a missing deck later reads as a limit of
        // the app rather than as a bug.
        Text(
            text = stringResource(R.string.public_decks_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

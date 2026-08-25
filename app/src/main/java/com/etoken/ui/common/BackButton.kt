package com.etoken.ui.common

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.etoken.R

/**
 * A local vector rather than `Icons.AutoMirrored.Filled.ArrowBack`: the
 * material-icons artifacts are an extra dependency this app would otherwise
 * not need, and commander-companion's app deliberately avoids them too.
 */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.action_back),
        )
    }
}

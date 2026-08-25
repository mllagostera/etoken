package com.etoken.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.etoken.R

/**
 * Local vectors rather than `androidx.compose.material:material-icons-*`: those
 * artifacts are a dependency this app would otherwise not need, and
 * commander-companion's app deliberately avoids them too.
 */
@Composable
fun BackButton(onClick: () -> Unit) {
    ActionButton(
        icon = R.drawable.ic_arrow_back,
        description = stringResource(R.string.action_back),
        onClick = onClick,
    )
}

@Composable
fun ActionButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painter = painterResource(icon), contentDescription = description)
    }
}

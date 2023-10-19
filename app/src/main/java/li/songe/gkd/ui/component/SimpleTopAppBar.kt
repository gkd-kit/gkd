package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopAppBar(
    onClickIcon: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {},
    title: String,
) {
    TopAppBar(navigationIcon = {
        IconButton(onClick = {
            onClickIcon?.invoke()
        }) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        }
    }, title = { Text(text = title) }, actions = actions
    )
}
package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import li.gkd.app.resources.Res
import li.gkd.app.resources.more_label
import li.gkd.app.resources.selection_all
import li.gkd.app.resources.selection_invert
import org.jetbrains.compose.resources.stringResource

@Composable
fun <K> RowScope.GkMultiSelectionActions(
    selectionState: MultiSelectionState<K>,
    keys: Set<K>,
    enabled: Boolean = true,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedKeys = selectionState.selectedKeys intersect keys
    LaunchedEffect(enabled, selectedKeys.isEmpty()) {
        if (!enabled || selectedKeys.isEmpty()) expanded = false
    }
    GkIconButton(
        imageVector = Icons.Outlined.SelectAll,
        contentDescription = stringResource(Res.string.selection_all),
        enabled = enabled && keys.isNotEmpty() && selectedKeys != keys,
        onClick = { selectionState.selectAll(keys) },
    )
    GkIconButton(
        imageVector = Icons.Outlined.FlipToBack,
        contentDescription = stringResource(Res.string.selection_invert),
        enabled = enabled && keys.isNotEmpty(),
        onClick = { selectionState.invert(keys) },
    )
    Box {
        GkIconButton(
            imageVector = GkIcons.MoreVert,
            contentDescription = stringResource(Res.string.more_label),
            enabled = enabled && selectedKeys.isNotEmpty(),
            onClick = { expanded = true },
        )
        if (expanded && enabled && selectedKeys.isNotEmpty()) {
            LocalOverlayBackHandler.current { expanded = false }
        }
        DropdownMenu(
            expanded = expanded && enabled && selectedKeys.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            menuContent { expanded = false }
        }
    }
}

@Composable
fun GkBatchActionMenuItem(
    text: String,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    DropdownMenuItem(
        text = { Text(text) },
        enabled = enabled,
        colors = if (destructive) {
            MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
        } else {
            MenuDefaults.itemColors()
        },
        onClick = {
            onDismiss()
            onClick()
        },
    )
}


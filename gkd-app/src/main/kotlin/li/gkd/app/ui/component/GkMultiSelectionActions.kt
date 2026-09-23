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
import li.gkd.app.text.UiStrings
import li.gkd.app.domain.rule.RuleSetting

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
        contentDescription = UiStrings.selection_all,
        enabled = enabled && keys.isNotEmpty() && selectedKeys != keys,
        onClick = { selectionState.selectAll(keys) },
    )
    GkIconButton(
        imageVector = Icons.Outlined.FlipToBack,
        contentDescription = UiStrings.selection_invert,
        enabled = enabled && keys.isNotEmpty(),
        onClick = { selectionState.invert(keys) },
    )
    Box {
        GkIconButton(
            imageVector = GkIcons.MoreVert,
            contentDescription = UiStrings.more_label,
            enabled = enabled && selectedKeys.isNotEmpty(),
            onClick = { expanded = true },
        )
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

@Composable
fun GkRuleBatchMenuItems(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (RuleSetting) -> Unit,
) {
    GkBatchActionMenuItem(UiStrings.action_turn_on, onDismiss, { onUpdate(RuleSetting.Enabled) }, enabled)
    GkBatchActionMenuItem(UiStrings.action_close, onDismiss, { onUpdate(RuleSetting.Disabled) }, enabled)
    GkBatchActionMenuItem(UiStrings.settings_reset_default, onDismiss, { onUpdate(RuleSetting.FollowDefault) }, enabled)
}

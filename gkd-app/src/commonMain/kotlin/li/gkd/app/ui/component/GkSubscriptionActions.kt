package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_rule_add
import li.gkd.app.resources.disabled
import li.gkd.app.resources.enabled
import li.gkd.app.resources.global_rule_add
import li.gkd.app.resources.more_actions
import li.gkd.app.resources.rule_matching_label
import li.gkd.app.resources.settings_dialog_open
import li.gkd.app.resources.subscription_settings
import li.gkd.app.resources.switch_toggle
import org.jetbrains.compose.resources.stringResource

@Composable
fun RowScope.GkSubscriptionActions(
    matching: Boolean, onToggleMatching: () -> Unit, onSettings: () -> Unit,
    onAddAppRule: () -> Unit, onAddGlobalRule: () -> Unit, onMenuOpen: () -> Boolean = { true },
) {
    var expanded by remember { mutableStateOf(false) }
    if (expanded) LocalOverlayBackHandler.current { expanded = false }
    GkIconButton(
        imageVector = if (matching) GkIcons.FlashOn else GkIcons.FlashOff, animateMorph = true,
        colors = IconButtonDefaults.iconButtonColors(contentColor = if (!matching) CheckboxDefaults.colors().checkedBoxColor else LocalContentColor.current),
        contentDescription = stringResource(Res.string.rule_matching_label) + stringResource(if (matching) Res.string.enabled else Res.string.disabled),
        onClickLabel = stringResource(Res.string.switch_toggle), onClick = onToggleMatching
    )
    GkIconButton(
        GkIcons.PageInfo, contentDescription = stringResource(Res.string.subscription_settings),
        onClickLabel = stringResource(Res.string.settings_dialog_open), onClick = onSettings
    )
    Box {
        GkIconButton(
            GkIcons.MoreVert,
            contentDescription = stringResource(Res.string.more_actions),
            onClick = { expanded = onMenuOpen() })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.app_rule_add)) },
                onClick = { expanded = false; onAddAppRule() })
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.global_rule_add)) },
                onClick = { expanded = false; onAddGlobalRule() })
        }
    }
}

package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import li.gkd.app.resources.*
import li.gkd.app.ui.option.AppGroupOption
import li.gkd.app.app.AppSort
import li.gkd.app.model.findOption
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkAppFilterContent(
    sort: Int,
    groupType: Int,
    showBlockApps: Boolean,
    onSort: (AppSort) -> Unit,
    onAppGroup: (Int) -> Unit,
    onToggleBlock: () -> Unit,
    includeUninstalled: Boolean = true,
    allowEmptyGroups: Boolean = false,
) {
    GkMenuGroupCard(inTop = true, title = stringResource(Res.string.sort_title)) {
        AppSort.options.forEach { option ->
            GkMenuItemRadioButton(
                text = stringResource(option.labelResource),
                selected = AppSort.options.findOption(sort) == option,
                onClick = { onSort(option) })
        }
    }
    GkMenuGroupCard(title = stringResource(Res.string.group_title)) {
        val options =
            if (includeUninstalled) AppGroupOption.allObjects else AppGroupOption.normalObjects
        options.forEach { option ->
            val next = option.invert(groupType)
            GkMenuItemCheckbox(
                text = stringResource(option.labelResource), checked = option.include(groupType),
                enabled = allowEmptyGroups || next != 0, onClick = { onAppGroup(next) })
        }
    }
    GkMenuGroupCard(title = stringResource(Res.string.display_title)) {
        GkMenuItemCheckbox(
            text = stringResource(Res.string.whitelist_title), checked = showBlockApps,
            onClick = onToggleBlock
        )
    }
}

package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import li.gkd.app.resources.Res
import li.gkd.app.resources.filter_title
import li.gkd.app.resources.group_title
import li.gkd.app.resources.sort_title
import li.gkd.app.resources.whitelist_title
import li.gkd.app.ui.option.AppGroupOption
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.option.findOption
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkAppFilterContent(
    sort: Int,
    groupType: Int,
    showBlockApps: Boolean,
    onSort: (AppSortOption) -> Unit,
    onAppGroup: (Int) -> Unit,
    onToggleBlock: () -> Unit,
    includeUninstalled: Boolean = true,
    allowEmptyGroups: Boolean = false,
) {
    GkMenuGroupCard(inTop = true, title = stringResource(Res.string.sort_title)) {
        AppSortOption.objects.forEach { option ->
            GkMenuItemRadioButton(
                text = option.label,
                selected = AppSortOption.objects.findOption(sort) == option,
                onClick = { onSort(option) })
        }
    }
    GkMenuGroupCard(title = stringResource(Res.string.group_title)) {
        val options =
            if (includeUninstalled) AppGroupOption.allObjects else AppGroupOption.normalObjects
        options.forEach { option ->
            val next = option.invert(groupType)
            GkMenuItemCheckbox(
                text = option.label, checked = option.include(groupType),
                enabled = allowEmptyGroups || next != 0, onClick = { onAppGroup(next) })
        }
    }
    GkMenuGroupCard(title = stringResource(Res.string.filter_title)) {
        GkMenuItemCheckbox(
            text = stringResource(Res.string.whitelist_title), checked = showBlockApps,
            onClick = onToggleBlock
        )
    }
}

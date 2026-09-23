package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import li.gkd.app.text.UiStrings
import li.gkd.app.util.AppGroupOption
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.findOption

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
    GkMenuGroupCard(inTop = true, title = UiStrings.sort_title) {
        AppSortOption.objects.forEach { option ->
            GkMenuItemRadioButton(text = option.label,
                selected = AppSortOption.objects.findOption(sort) == option,
                onClick = { onSort(option) })
        }
    }
    GkMenuGroupCard(title = UiStrings.group_title) {
        val options = if (includeUninstalled) AppGroupOption.allObjects else AppGroupOption.normalObjects
        options.forEach { option ->
            val next = option.invert(groupType)
            GkMenuItemCheckbox(text = option.label, checked = option.include(groupType),
                enabled = allowEmptyGroups || next != 0, onClick = { onAppGroup(next) })
        }
    }
    GkMenuGroupCard(title = UiStrings.filter_title) {
        GkMenuItemCheckbox(text = UiStrings.whitelist_title, checked = showBlockApps,
            onClick = onToggleBlock)
    }
}

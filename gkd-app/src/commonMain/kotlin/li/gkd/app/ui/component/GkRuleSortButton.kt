package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import li.gkd.app.resources.*
import li.gkd.app.rule.RuleSort
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleSortButton(sort: RuleSort, onChange: (RuleSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (expanded) LocalOverlayBackHandler.current { expanded = false }
    Box {
        GkFilterIconButton(
            filtered = sort != RuleSort.ByDefault,
            contentDescription = stringResource(Res.string.sort_title),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            RuleSort.entries.forEach { option ->
                GkMenuItemRadioButton(
                    text = stringResource(option.labelResource),
                    selected = sort == option,
                    onClick = { onChange(option); expanded = false },
                )
            }
        }
    }
}

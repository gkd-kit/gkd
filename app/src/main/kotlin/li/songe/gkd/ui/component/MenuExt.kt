package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import li.songe.gkd.util.throttle

@Composable
inline fun MenuGroupCard(inTop: Boolean = false, title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        modifier = Modifier
            .padding(MenuDefaults.DropdownMenuItemContentPadding)
            .padding(top = if (inTop) 0.dp else 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    content()
}

@Composable
fun MenuItemCheckbox(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val actualOnClick = throttle(onClick)
    DropdownMenuItem(
        text = { Text(text = text) },
        trailingIcon = {
            Checkbox(
                checked = checked,
                onCheckedChange = { actualOnClick() },
                enabled = enabled,
            )
        },
        onClick = actualOnClick,
        enabled = enabled,
    )
}

@Composable
fun MenuItemCheckbox(
    text: String,
    stateFlow: MutableStateFlow<Boolean>,
    enabled: Boolean = true,
) = MenuItemCheckbox(
    text = text,
    checked = stateFlow.collectAsState().value,
    onClick = { stateFlow.update { !it } },
    enabled = enabled,
)

@Composable
fun MenuItemRadioButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val actualOnClick = throttle(onClick)
    DropdownMenuItem(
        text = {
            Text(text = text)
        },
        trailingIcon = {
            RadioButton(
                selected = selected,
                onClick = actualOnClick,
                enabled = enabled,
            )
        },
        onClick = actualOnClick,
        enabled = enabled,
    )
}
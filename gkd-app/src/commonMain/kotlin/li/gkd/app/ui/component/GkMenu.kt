package li.gkd.app.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
inline fun GkMenuGroupCard(inTop: Boolean = false, title: String, content: @Composable () -> Unit) {
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
fun GkMenuItemCheckbox(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { Text(text = text) },
        trailingIcon = {
            Checkbox(
                checked = checked,
                onCheckedChange = { onClick() },
                enabled = enabled,
            )
        },
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
fun GkMenuItemRadioButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = {
            Text(text = text)
        },
        trailingIcon = {
            RadioButton(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
            )
        },
        onClick = onClick,
        enabled = enabled,
    )
}

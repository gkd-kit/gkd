package li.songe.gkd.ui.component

import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun DropdownMenuCheckboxItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(text = text)
        },
        trailingIcon = {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

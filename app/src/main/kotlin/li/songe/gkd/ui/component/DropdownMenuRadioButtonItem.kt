package li.songe.gkd.ui.component

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun DropdownMenuRadioButtonItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(text = text)
        },
        trailingIcon = {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        },
        onClick = onClick,
    )
}
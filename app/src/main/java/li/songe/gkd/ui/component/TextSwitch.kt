package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun TextSwitch(
    text: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val animatedColor = (
                Color(
                    0,
                    0,
                    0,
                    (0xFF * (if (checked) 1f else .3f)).toInt()
                )
                )
        Text(
            text,
            color = animatedColor
        )
        Switch(
            checked,
            onCheckedChange,
        )
    }
}

@Preview
@Composable
fun PreviewTextSwitch() {
    Surface {
        TextSwitch("text", true)
    }
}
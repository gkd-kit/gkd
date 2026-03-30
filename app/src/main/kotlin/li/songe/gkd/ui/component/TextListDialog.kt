package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import li.songe.gkd.util.throttle

@Composable
fun TextListDialog(
    onDismiss: () -> Unit,
    textList: List<Pair<String, () -> Unit>>
) {
    val textModifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            textList.forEach { (text, onClickItem) ->
                Text(
                    text = text, modifier = Modifier
                        .clickable(onClick = throttle {
                            onDismiss()
                            onClickItem()
                        })
                        .then(textModifier)
                )
            }
        }
    }
}
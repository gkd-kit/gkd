package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TextSwitch(
    name: String = "",
    desc: String = "",
    checked: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.padding(10.dp, 5.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name, fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                desc, fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked,
            onCheckedChange,
        )
    }
}

@Preview
@Composable
fun PreviewTextSwitch() {
    TextSwitch("隐藏后台", "在最近任务列表中隐藏", true)
}
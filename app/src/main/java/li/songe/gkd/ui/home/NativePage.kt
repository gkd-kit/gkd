package li.songe.gkd.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import li.songe.gkd.R
import li.songe.gkd.utils.SafeR

@Composable
fun NativePage() {
    Column(
        modifier = Modifier
            .padding(20.dp, 0.dp)
    ) {
        Row(
            modifier = Modifier.height(40.dp)
        ) {
            Image(
                painter = painterResource(SafeR.ic_app_2),
                contentDescription = "",
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(CircleShape)
            )
            Column {
                Text(text = "应用名称")
                Text(text = "8/10")
            }
            val checkedState = remember { mutableStateOf(true) }
            Switch(checked = checkedState.value,
                onCheckedChange = {
                    checkedState.value = it
                }
            )
        }
    }
}

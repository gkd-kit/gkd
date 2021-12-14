package li.songe.gkd.ui.page

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPage() {
    Column(
        modifier = Modifier
            .verticalScroll(
                state = rememberScrollState()
            )
            .padding(20.dp)
    ) {
        Row {
            val checkedState = remember { mutableStateOf(true) }
            val animatedColor = animateColorAsState(
                Color(
                    0,
                    0,
                    0,
                    (0xFF * (if (checkedState.value) 1f else .3f)).toInt()
                )
            )
            Text(
                text = "服务已${(if (checkedState.value) "开启" else "关闭")}",
                color = animatedColor.value
            )
            Switch(checked = checkedState.value,
                onCheckedChange = {
                    checkedState.value = it
                }
            )
        }
        Row {
            val checkedState = remember { mutableStateOf(true) }
            Text(text = "在[最近任务]界面中隐藏本应用")
            Switch(checked = checkedState.value,
                onCheckedChange = { checkedState.value = it }
            )
        }
        Row {
            val checkedState = remember { mutableStateOf(true) }
            Text(text = "通知栏显示")
            Switch(checked = checkedState.value,
                onCheckedChange = { checkedState.value = it }
            )
        }
    }
}
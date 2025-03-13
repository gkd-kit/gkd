package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Switch
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.throttle

@Composable
fun InnerDisableSwitch(
    modifier: Modifier = Modifier,
    valid: Boolean = true,
    isSelectedMode: Boolean = false,
) {
    val mainVm = LocalMainViewModel.current
    val onClick = {
        if (valid) {
            mainVm.dialogFlow.updateDialogOptions(
                title = "内置禁用",
                text = "此规则组已经在其 apps 字段中配置对当前应用的禁用, 因此无法手动开启规则组\n\n提示: 这种情况一般在此全局规则无法适配/跳过适配/单独适配当前应用时出现",
            )
        } else {
            mainVm.dialogFlow.updateDialogOptions(
                title = "非法规则",
                text = "规则存在错误, 无法启用",
            )
        }
    }
    Switch(
        checked = false,
        enabled = false,
        onCheckedChange = null,
        modifier = modifier
            .minimumInteractiveComponentSize().run {
                if (isSelectedMode) {
                    this
                } else {
                    clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Switch,
                        onClick = throttle(onClick)
                    )
                }
            }
    )
}

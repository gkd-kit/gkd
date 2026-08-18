package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle

@Composable
fun InnerDisableSwitch(
    modifier: Modifier = Modifier,
    valid: Boolean = true,
    isSelectedMode: Boolean = false,
) {
    val mainVm = LocalMainViewModel.current
    val onClick = mainVm.scope.launchAsFn {
        mainVm.dialogRequests.showMessage(
            title = if (valid) "内置禁用" else "非法规则",
            text = if (valid) {
                "此规则已经在内部配置对当前应用的禁用，就算强制开启规则也是无意义或不生效的\n\n提示: 这种情况一般在此全局规则无法适配/跳过适配/单独适配当前应用时出现"
            } else {
                "规则存在错误, 无法启用"
            },
        )
    }
    PerfSwitch(
        checked = false,
        enabled = false,
        onCheckedChange = null,
        modifier = modifier.semantics {
            stateDescription = "已禁用"
        }
            .minimumInteractiveComponentSize().run {
                if (isSelectedMode) {
                    this
                } else {
                    clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Switch,
                        onClick = throttle(onClick),
                        onClickLabel = "打开规则禁用说明",
                    )
                }
            }
    )
}

package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_turn_on
import li.gkd.app.resources.settings_reset_default
import li.gkd.app.rule.RuleSetting
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleBatchMenuItems(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (RuleSetting) -> Unit,
) {
    GkBatchActionMenuItem(
        stringResource(Res.string.action_turn_on),
        onDismiss,
        { onUpdate(RuleSetting.Enabled) },
        enabled
    )
    GkBatchActionMenuItem(
        stringResource(Res.string.action_close),
        onDismiss,
        { onUpdate(RuleSetting.Disabled) },
        enabled
    )
    GkBatchActionMenuItem(
        stringResource(Res.string.settings_reset_default),
        onDismiss,
        { onUpdate(RuleSetting.FollowDefault) },
        enabled
    )
}

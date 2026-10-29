package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import li.gkd.app.resources.*
import li.gkd.app.rule.RuleSetting
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleBatchMenuItems(
    onDismiss: () -> Unit,
    onUpdate: (RuleSetting) -> Unit,
) {
    GkBatchActionMenuItem(
        stringResource(Res.string.action_turn_on),
        onDismiss,
        { onUpdate(RuleSetting.Enabled) },
    )
    GkBatchActionMenuItem(
        stringResource(Res.string.action_close),
        onDismiss,
        { onUpdate(RuleSetting.Disabled) },
    )
    GkBatchActionMenuItem(
        stringResource(Res.string.settings_reset_default),
        onDismiss,
        { onUpdate(RuleSetting.FollowDefault) },
    )
}

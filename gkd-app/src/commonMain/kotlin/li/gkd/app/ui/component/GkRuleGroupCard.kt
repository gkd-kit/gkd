package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.*
import li.gkd.app.rule.RuleControlState
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.toRuleGroupTarget
import li.gkd.app.rule.toSwitchTarget
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.RuleValidationError
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleGroupCard(
    modifier: Modifier = Modifier,
    subs: RawSubscription,
    appId: String?,
    group: RawSubscription.RawGroupProps,
    control: RuleControlState,
    triggerSummary: li.gkd.app.record.TriggerSummary? = null,
    onOpen: () -> Unit,
    onSettingChange: (RuleSetting) -> Unit,
    highlighted: Boolean = false,
    hideCategoryPrefix: Boolean = false,
    isSelectedMode: Boolean = false,
    isSelected: Boolean = false,
    selectionEnabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    handlesLongPress: Boolean = true,
    onLongClick: () -> Unit = {},
    onSelectedChange: () -> Unit = {},
) {
    GkRuleListItem(
        modifier = modifier, onClick = onOpen,
        selectedMode = isSelectedMode, selected = isSelected, highlighted = highlighted,
        selectable = control.canEnable,
        selectionEnabled = selectionEnabled,
        interactionSource = interactionSource,
        handlesLongPress = handlesLongPress,
        onLongClick = onLongClick, onSelect = onSelectedChange,
        trailingInfo = {
            val count = triggerSummary?.count ?: 0L
            val hasIndicators = control.limitations.hasPersonalExclusions
            if (count > 0 || hasIndicators) {
                Row(
                    Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GkTriggerCount(count, lastTriggerTime = triggerSummary?.lastTime)
                    if (hasIndicators) GkRulePropertyIndicators(control)
                }
            }
        },
        trailing = {
            GkRuleEnableControl(
                control, onSettingChange, modifier = it,
                identity = group.toRuleGroupTarget(subs.id, appId).toSwitchTarget()
            )
        },
    ) {
        val description = if (!group.valid) {
            when (val error = group.validationError) {
                is RuleValidationError.SelectorError -> stringResource(
                    Res.string.selector_invalid_detail, error.source, error.detail,
                )

                is RuleValidationError.InvalidPosition -> stringResource(
                    Res.string.rule_invalid_position,
                    error.value
                )

                null -> stringResource(Res.string.rule_format_error)
            }
        } else group.desc?.takeIf { it.isNotBlank() }

        GkGroupNameText(
            text = group.name, modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            isGlobal = group is RawSubscription.RawGlobalGroup, maxLines = 2,
            categoryName = if (group is RawSubscription.RawAppGroup) subs.getCategory(group.name)?.name else null,
            hideCategoryPrefix = hideCategoryPrefix,
            overflow = TextOverflow.Ellipsis
        )
        if (description != null) {
            Text(
                text = description,
                modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (group.valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

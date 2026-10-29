package li.gkd.app.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.error.messageResource
import li.gkd.app.record.TriggerSummary
import li.gkd.app.resources.*
import li.gkd.app.rule.RuleControlState
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.toRuleGroupTarget
import li.gkd.app.subscription.RawSubscription
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleGroupCard(
    modifier: Modifier = Modifier,
    subs: RawSubscription,
    appId: String?,
    group: RawSubscription.RawGroupProps,
    control: RuleControlState,
    triggerSummary: TriggerSummary? = null,
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
    var showError by remember(subs.id, appId, group.key) { mutableStateOf(false) }
    val error = group.validationError
    GkRuleListItem(
        modifier = modifier,
        onClick = onOpen,
        selectedMode = isSelectedMode,
        selected = isSelected,
        highlighted = highlighted,
        selectionEnabled = selectionEnabled,
        interactionSource = interactionSource,
        handlesLongPress = handlesLongPress,
        onLongClick = onLongClick,
        onSelect = onSelectedChange,
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
            Box(it, contentAlignment = Alignment.Center) {
                // Both controls contribute exactly the same size to the row's intrinsic
                // measurement.
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    if (error != null) {
                        GkIconButton(
                            imageVector = GkIcons.WarningAmber,
                            contentDescription =
                                stringResource(Res.string.rule_unavailable_reason_view),
                            modifier = Modifier.fillMaxSize(),
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            onClick = { showError = true },
                        )
                    } else {
                        GkRuleEnableControl(
                            control,
                            onSettingChange,
                            modifier = Modifier.fillMaxSize(),
                            identity = group.toRuleGroupTarget(subs.id, appId).toSwitchTarget(),
                        )
                    }
                }
            }
        },
    ) {
        val description =
            if (error != null) {
                error.message.messageResource()
            } else group.desc?.takeIf { it.isNotBlank() }

        GkGroupNameText(
            text = group.name,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            isGlobal = group is RawSubscription.RawGlobalGroup,
            maxLines = 2,
            categoryName =
                if (group is RawSubscription.RawAppGroup) subs.getCategory(group.name)?.name
                else null,
            hideCategoryPrefix = hideCategoryPrefix,
            overflow = TextOverflow.Ellipsis,
        )
        if (description != null) {
            Text(
                text = description,
                modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (error == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (showError && error != null) {
        GkAlertDialog(
            onDismissRequest = { showError = false },
            title = { Text(stringResource(Res.string.rule_unavailable)) },
            text = {
                Text(
                    error.message.messageResource() +
                        "\n\n" +
                        stringResource(Res.string.rule_invalid_setting_explanation),
                )
            },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text(stringResource(Res.string.action_got_it))
                }
            },
        )
    }
}

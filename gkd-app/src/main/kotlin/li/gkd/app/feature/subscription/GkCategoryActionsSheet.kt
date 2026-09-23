package li.gkd.app.feature.subscription

import androidx.compose.animation.animateContentSize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import li.gkd.app.data.RawSubscription
import li.gkd.app.domain.rule.CategorySetting
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkRuleSettingsSheet
import li.gkd.app.ui.component.GkSwitch
import li.gkd.app.ui.component.GkRulePropertyIcon
import li.gkd.app.ui.component.RuleProperty

@Composable
fun GkCategoryActionsSheet(
    category: RawSubscription.RawCategory,
    setting: CategorySetting,
    overrideCount: Int,
    editable: Boolean,
    busy: Boolean,
    onDismissRequest: () -> Unit,
    onSetting: (CategorySetting) -> Unit,
    onClearOverrides: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    GkRuleSettingsSheet(
        title = category.name,
        headerBottomPadding = 0.dp,
        subtitleContent = {
            Column(Modifier.fillMaxWidth().animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                category.desc?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (overrideCount > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            GkRulePropertyIcon(
                                property = RuleProperty.CustomSetting,
                                modifier = Modifier.padding(end = 12.dp).size(24.dp),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f).padding(end = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(UiStrings.category_override_count(overrideCount),
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(UiStrings.category_override_unaffected,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                enabled = !busy,
                                onClick = onClearOverrides,
                                modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(UiStrings.action_clear)
                            }
                        }
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
    ) {
        CategoryDefaultContent(category, setting, onSetting)
        if (editable) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GkIconButton(
                    imageVector = GkIcons.Edit,
                    contentDescription = UiStrings.category_edit,
                    onClick = onEdit,
                )
                GkIconButton(
                    imageVector = GkIcons.Delete,
                    contentDescription = UiStrings.category_delete,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun CategoryDefaultContent(
    category: RawSubscription.RawCategory,
    setting: CategorySetting,
    onSelect: (CategorySetting) -> Unit,
) {
    val subscriptionDefault = when (category.enable) {
        true -> UiStrings.category_follow_subscription_enabled
        false -> UiStrings.category_follow_subscription_disabled
        null -> UiStrings.category_follow_subscription_unspecified
    }
    val customSelected = setting == CategorySetting.Enabled || setting == CategorySetting.Disabled
    val customEnabled = when (setting) {
        CategorySetting.Enabled -> true
        CategorySetting.Disabled -> false
        CategorySetting.FollowSubscription -> category.enable ?: true
        CategorySetting.GroupDefault -> true
    }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryChoice.entries.forEach { choice ->
            val selected = when (choice) {
                CategoryChoice.Subscription -> setting == CategorySetting.FollowSubscription
                CategoryChoice.Custom -> customSelected
                CategoryChoice.GroupDefault -> setting == CategorySetting.GroupDefault
            }
            val label = when (choice) {
                CategoryChoice.Subscription -> UiStrings.category_follow_subscription
                CategoryChoice.Custom -> UiStrings.category_custom
                CategoryChoice.GroupDefault -> UiStrings.category_use_group_default
            }
            val description = when (choice) {
                CategoryChoice.Subscription -> subscriptionDefault
                CategoryChoice.Custom -> if (customEnabled) UiStrings.category_enabled_description else UiStrings.category_disabled_description
                CategoryChoice.GroupDefault -> UiStrings.category_use_group_default_description
            }
            val interaction = if (choice == CategoryChoice.Custom && customSelected) {
                Modifier.toggleable(value = customEnabled, role = Role.Switch, onValueChange = {
                    onSelect(if (it) CategorySetting.Enabled else CategorySetting.Disabled)
                })
            } else {
                Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = {
                    onSelect(when (choice) {
                        CategoryChoice.Subscription -> CategorySetting.FollowSubscription
                        CategoryChoice.Custom -> if (customEnabled) CategorySetting.Enabled else CategorySetting.Disabled
                        CategoryChoice.GroupDefault -> CategorySetting.GroupDefault
                    })
                })
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().then(interaction)
                        .heightIn(min = 72.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f).padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val iconSetting = when (choice) {
                                CategoryChoice.Subscription -> CategorySetting.FollowSubscription
                                CategoryChoice.Custom -> if (customEnabled) CategorySetting.Enabled else CategorySetting.Disabled
                                CategoryChoice.GroupDefault -> CategorySetting.GroupDefault
                            }
                            GkIcon(
                                imageVector = iconSetting.icon,
                                animateMorph = choice == CategoryChoice.Custom,
                                modifier = Modifier.size(20.dp),
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(width = 56.dp, height = 48.dp), contentAlignment = Alignment.Center) {
                        if (choice == CategoryChoice.Custom) {
                            GkSwitch(checked = customEnabled, onCheckedChange = null,
                                enabled = customSelected)
                        } else {
                            RadioButton(selected = selected, onClick = null)
                        }
                    }
                }
            }
        }
    }
}

private enum class CategoryChoice { Subscription, Custom, GroupDefault }

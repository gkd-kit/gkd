package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.gkd.app.text.UiStrings

@Composable
fun GkAppRuleRestrictionCard(
    whitelisted: Boolean,
    partialDisabled: Boolean,
    partialFollowsWhitelist: Boolean,
    onRemoveWhitelist: () -> Unit,
    onRemovePartialDisable: () -> Unit,
) {
    if (whitelisted || partialDisabled) {
        Card(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            if (whitelisted) {
                RestrictionRow(UiStrings.whitelist_member,
                    if (partialFollowsWhitelist) UiStrings.app_rule_whitelist_follow_notice else UiStrings.app_rule_whitelist_notice,
                    UiStrings.whitelist_remove, onRemoveWhitelist)
            }
            if (partialDisabled) {
                RestrictionRow(UiStrings.app_rule_partial_disable_title, UiStrings.app_rule_partial_disable_notice,
                    UiStrings.app_rule_partial_disable_remove, onRemovePartialDisable)
            }
        }
    }
}

@Composable
private fun RestrictionRow(title: String, description: String, action: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GkIcon(GkIcons.Info, Modifier.size(24.dp), contentDescription = null)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        GkIconButton(GkIcons.RemoveCircleOutline, onClick = onRemove, contentDescription = action)
    }
}

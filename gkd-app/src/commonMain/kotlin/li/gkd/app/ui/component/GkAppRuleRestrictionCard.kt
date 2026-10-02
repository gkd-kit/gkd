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
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_rule_partial_disable_notice
import li.gkd.app.resources.app_rule_partial_disable_remove
import li.gkd.app.resources.app_rule_partial_disable_title
import li.gkd.app.resources.app_rule_whitelist_follow_notice
import li.gkd.app.resources.app_rule_whitelist_notice
import li.gkd.app.resources.whitelist_member
import li.gkd.app.resources.whitelist_remove
import org.jetbrains.compose.resources.stringResource

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
                RestrictionRow(
                    stringResource(Res.string.whitelist_member),
                    if (partialFollowsWhitelist) stringResource(Res.string.app_rule_whitelist_follow_notice) else stringResource(
                        Res.string.app_rule_whitelist_notice
                    ),
                    stringResource(Res.string.whitelist_remove), onRemoveWhitelist
                )
            }
            if (partialDisabled) {
                RestrictionRow(
                    stringResource(Res.string.app_rule_partial_disable_title),
                    stringResource(Res.string.app_rule_partial_disable_notice),
                    stringResource(Res.string.app_rule_partial_disable_remove),
                    onRemovePartialDisable
                )
            }
        }
    }
}

@Composable
private fun RestrictionRow(
    title: String,
    description: String,
    action: String,
    onRemove: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GkIcon(GkIcons.Info, Modifier.size(24.dp), contentDescription = null)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        GkIconButton(GkIcons.RemoveCircleOutline, onClick = onRemove, contentDescription = action)
    }
}

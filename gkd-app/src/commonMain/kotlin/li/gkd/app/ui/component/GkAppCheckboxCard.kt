package li.gkd.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.model.AppInfo
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_name_description
import li.gkd.app.resources.list_add
import li.gkd.app.resources.list_member
import li.gkd.app.resources.list_not_member
import li.gkd.app.resources.list_remove
import li.gkd.app.ui.style.itemPadding
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkAppCheckboxCard(
    appInfo: AppInfo,
    checked: Boolean,
    onCheckedChange: (() -> Unit),
    appIcon: @Composable () -> Unit,
    appName: @Composable () -> Unit,
) {
    val description = stringResource(Res.string.app_name_description, appInfo.name)
    val membership =
        stringResource(if (checked) Res.string.list_member else Res.string.list_not_member)
    val actionLabel = stringResource(if (checked) Res.string.list_remove else Res.string.list_add)
    Row(
        modifier = Modifier
            .clickable(onClick = onCheckedChange)
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription =
                    membership
                onClick(
                    label = actionLabel,
                    action = null
                )
            }
            .itemPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        appIcon()
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            appName()
            Text(
                text = appInfo.id,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
        GkCheckbox(
            key = appInfo.id,
            checked = checked,
        )
    }
}

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
import li.gkd.app.text.UiStrings
import li.gkd.app.data.AppInfo
import li.gkd.app.ui.style.appItemPadding
import li.gkd.app.util.TimeUtils.throttle

@Composable
fun GkAppCheckboxCard(
    appInfo: AppInfo,
    checked: Boolean,
    onCheckedChange: (() -> Unit),
) {
    Row(
        modifier = Modifier
            .clickable(onClick = throttle(onCheckedChange))
            .clearAndSetSemantics {
                contentDescription = UiStrings.app_name_description(appInfo.name)
                stateDescription = if (checked) UiStrings.list_member else UiStrings.list_not_member
                onClick(
                    label = if (checked) UiStrings.list_remove else UiStrings.list_add,
                    action = null
                )
            }
            .appItemPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GkAppIcon(appId = appInfo.id)
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            GkAppNameText(appInfo = appInfo)
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

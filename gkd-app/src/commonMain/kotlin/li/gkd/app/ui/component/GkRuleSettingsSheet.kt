package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import li.gkd.app.model.ExcludeData
import li.gkd.app.resources.*
import li.gkd.app.rule.RuleControlState
import li.gkd.app.rule.RuleSetting
import li.gkd.app.ui.icon.ResetSettings
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleSettingsSheet(
    title: String,
    onDismissRequest: () -> Unit,
    titleContent: @Composable () -> Unit = {
        Text(
            title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
        )
    },
    subtitle: String? = null,
    subtitleContent: @Composable () -> Unit = {
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    },
    footerLeadingContent: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    headerBottomPadding: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GkModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = headerBottomPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                titleContent()
                subtitleContent()
            }
            content()
            if (footerLeadingContent != null || actions != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).padding(end = 8.dp)) {
                        footerLeadingContent?.invoke()
                    }
                    actions?.invoke()
                }
            }
        }
    }
}

@Composable
fun GkRuleSettingsContent(
    state: RuleControlState,
    onSettingChange: (RuleSetting) -> Unit,
    title: String = stringResource(Res.string.rule_enable),
    onViewControl: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier =
                    Modifier.weight(1f)
                        .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            if (onViewControl != null) {
                GkIconButton(
                    imageVector = GkIcons.Flowchart,
                    contentDescription = stringResource(Res.string.rule_control_view),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    onClick = onViewControl,
                )
            }
            GkIconButton(
                imageVector = ResetSettings,
                contentDescription = stringResource(Res.string.settings_reset_default),
                enabled = state.hasCustomSetting,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                onClick = { onSettingChange(RuleSetting.FollowDefault) },
            )
            GkRuleEnableControl(
                state,
                onSettingChange,
                showCustomSettingIcon = false,
                modifier = Modifier.width(84.dp).fillMaxHeight(),
            )
        }
    }
}

@Composable
fun GkRuleExclusionsCard(
    exclude: ExcludeData,
    appId: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (appId == null) RuleProperty.Personal.label
                else stringResource(Res.string.page_exclusion),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            val apps = if (appId == null) exclude.appIds.size else 0
            val pages = exclude.activityIds.count { appId == null || it.first == appId }
            if (apps > 0) {
                ExclusionCount(
                    GkIcons.Apps,
                    apps,
                    stringResource(Res.string.app_count, apps.toString()),
                )
            }
            if (pages > 0) {
                ExclusionCount(
                    GkIcons.Layers,
                    pages,
                    stringResource(Res.string.page_count, pages.toString()),
                )
            }
            GkRulePropertyIcon(
                RuleProperty.Personal,
                modifier = Modifier.size(24.dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExclusionCount(icon: ImageVector, count: Int, description: String) {
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GkIcon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

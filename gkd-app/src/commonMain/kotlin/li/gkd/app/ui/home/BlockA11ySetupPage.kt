package li.gkd.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_back
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_enable
import li.gkd.app.resources.app_details_open
import li.gkd.app.resources.autostart_allow
import li.gkd.app.resources.battery_optimization_settings_open
import li.gkd.app.resources.battery_strategy_unrestricted
import li.gkd.app.resources.optional_label
import li.gkd.app.resources.partial_disable_background_detail
import li.gkd.app.resources.partial_disable_background_suggestions
import li.gkd.app.resources.partial_disable_background_title
import li.gkd.app.resources.partial_disable_description
import li.gkd.app.resources.partial_disable_intro
import li.gkd.app.resources.partial_disable_limits_detail
import li.gkd.app.resources.partial_disable_limits_title
import li.gkd.app.resources.partial_disable_ready
import li.gkd.app.resources.partial_disable_remaining
import li.gkd.app.resources.partial_disable_requirement_ready
import li.gkd.app.resources.partial_disable_touch_detail
import li.gkd.app.resources.partial_disable_touch_title
import li.gkd.app.resources.persistent_notification
import li.gkd.app.resources.persistent_notification_enable
import li.gkd.app.resources.privilege_service
import li.gkd.app.resources.privilege_service_open
import li.gkd.app.resources.recents_lock
import li.gkd.app.resources.recents_lock_manual_hint
import li.gkd.app.resources.recents_open
import li.gkd.app.resources.service_partial_disable
import li.gkd.app.resources.usage_notice
import li.gkd.app.resources.usage_requirements
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.navigation.ignoresBatteryOptimizations
import li.gkd.app.ui.navigation.openAppDetails
import li.gkd.app.ui.navigation.openRecents
import li.gkd.app.ui.navigation.requestIgnoreBatteryOptimizations
import li.gkd.app.ui.navigation.setStatusServiceEnabled
import li.gkd.app.ui.style.itemHorizontalPadding
import org.jetbrains.compose.resources.stringResource

@Composable
fun BlockA11ySetupPage(window: AppWindow, onBack: () -> Unit, onNavigate: (AppRoute) -> Unit) {
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val platform = window.dashboardPlatformState()
    val ignoreBatteryOptimizations: Boolean = window.ignoresBatteryOptimizations()

    val scrollState = rememberScrollState()
    val remainingRequirements =
        listOf(
                platform.privilegeAvailable,
                platform.statusRunning,
                ignoreBatteryOptimizations,
            )
            .count { !it }
    GkScaffold(
        topBar = {
            GkTopAppBar(
                actions = {
                    GkIconButton(
                        imageVector = GkIcons.Close,
                        onClickLabel = stringResource(Res.string.action_close),
                        onClick = onBack,
                    )
                },
                title = {
                    Text(text = stringResource(Res.string.service_partial_disable))
                },
            )
        },
        bottomBar = {
            BottomAppBar(
                windowInsets =
                    LocalHomeNavigationInsets.current ?: BottomAppBarDefaults.windowInsets
            ) {
                Text(
                    text =
                        if (remainingRequirements == 0)
                            stringResource(Res.string.partial_disable_ready)
                        else
                            stringResource(
                                Res.string.partial_disable_remaining,
                                remainingRequirements,
                            ),
                    modifier = Modifier.weight(1f).padding(horizontal = itemHorizontalPadding),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    enabled = store.enableBlockA11yAppList || remainingRequirements == 0,
                    onClick = {
                        SettingsRepository.updateSettings { it.copy(enableBlockA11yAppList = true) }
                        onBack()
                    },
                ) {
                    Text(
                        text =
                            if (store.enableBlockA11yAppList) stringResource(Res.string.action_back)
                            else stringResource(Res.string.action_enable)
                    )
                }
                Spacer(modifier = Modifier.width(itemHorizontalPadding))
            }
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = itemHorizontalPadding)
        ) {
            Text(
                text = stringResource(Res.string.partial_disable_intro),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.partial_disable_description),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BlockA11ySectionTitle(text = stringResource(Res.string.usage_requirements))
            BlockA11ySettingsCard {
                BlockA11yRequirementItem(
                    text = stringResource(Res.string.privilege_service),
                    onClickLabel = stringResource(Res.string.privilege_service_open),
                    satisfied = platform.privilegeAvailable,
                    imageVector =
                        if (platform.privilegeAvailable) GkIcons.Check
                        else GkIcons.KeyboardArrowRight,
                    onClick = { onNavigate(PrivilegeServiceRoute) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BlockA11yRequirementItem(
                    text = stringResource(Res.string.persistent_notification),
                    satisfied = platform.statusRunning,
                    onClickLabel = stringResource(Res.string.persistent_notification_enable),
                    imageVector = if (platform.statusRunning) GkIcons.Check else GkIcons.PlayArrow,
                    onClick = { window.setStatusServiceEnabled(true) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BlockA11yRequirementItem(
                    text = stringResource(Res.string.battery_strategy_unrestricted),
                    satisfied = ignoreBatteryOptimizations,
                    imageVector =
                        if (ignoreBatteryOptimizations) GkIcons.Check else GkIcons.OpenInNew,
                    onClickLabel = stringResource(Res.string.battery_optimization_settings_open),
                    onClick = window::requestIgnoreBatteryOptimizations,
                )
            }
            BlockA11ySectionTitle(
                text = stringResource(Res.string.partial_disable_background_suggestions),
                optional = true,
            )
            BlockA11ySettingsCard {
                BlockA11yRequirementItem(
                    text = stringResource(Res.string.autostart_allow),
                    imageVector = GkIcons.OpenInNew,
                    onClickLabel = stringResource(Res.string.app_details_open),
                    onClick = window::openAppDetails,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BlockA11yRequirementItem(
                    text = stringResource(Res.string.recents_lock),
                    imageVector = GkIcons.OpenInNew,
                    onClickLabel =
                        if (platform.privilegeAvailable) stringResource(Res.string.recents_open)
                        else stringResource(Res.string.recents_lock_manual_hint),
                    onClick = window::openRecents,
                )
            }
            BlockA11ySectionTitle(text = stringResource(Res.string.usage_notice))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BlockA11yNoticeItem(
                    title = stringResource(Res.string.partial_disable_touch_title),
                    text = stringResource(Res.string.partial_disable_touch_detail),
                )
                BlockA11yNoticeItem(
                    title = stringResource(Res.string.partial_disable_background_title),
                    text = stringResource(Res.string.partial_disable_background_detail),
                )
                BlockA11yNoticeItem(
                    title = stringResource(Res.string.partial_disable_limits_title),
                    text = stringResource(Res.string.partial_disable_limits_detail),
                )
            }
            GkPageBottomSpace()
        }
    }
}

@Composable
private fun BlockA11ySectionTitle(text: String, optional: Boolean = false) {
    Row(
        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleSmall)
        if (optional) {
            Text(
                text = stringResource(Res.string.optional_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlockA11ySettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun BlockA11yNoticeItem(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlockA11yRequirementItem(
    text: String,
    imageVector: ImageVector,
    onClick: () -> Unit,
    satisfied: Boolean = false,
    onClickLabel: String,
) {
    val readyDescription = stringResource(Res.string.partial_disable_requirement_ready)
    val statusColor =
        if (satisfied) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.primary
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    if (satisfied) stateDescription = readyDescription
                }
                .clickable(
                    enabled = !satisfied,
                    onClick = onClick,
                    onClickLabel = onClickLabel,
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        GkIcon(
            imageVector = imageVector,
            modifier = Modifier.size(24.dp),
            contentDescription = null,
            animateMorph = true,
            tint = statusColor,
        )
    }
}

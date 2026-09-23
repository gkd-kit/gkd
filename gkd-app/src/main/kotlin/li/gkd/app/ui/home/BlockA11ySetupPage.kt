package li.gkd.app.ui.home

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import android.view.KeyEvent
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.coroutines.delay
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.service.StatusService
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.ToastUtils.toast

@Serializable
data object BlockA11ySetupRoute : NavKey

@Composable
fun BlockA11ySetupPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel<SettingsVm>()
    val store by storeFlow.collectAsStateWithLifecycle()
    val statusRunning by StatusService.isRunning.collectAsStateWithLifecycle()
    val privilegeContext by privilegeContextFlow.collectAsStateWithLifecycle()
    val ignoreBatteryOptimizations by PermissionStates.ignoreBatteryOptimizations.stateFlow.collectAsStateWithLifecycle()
    val actionScope = vm.scope
    val scrollState = rememberScrollState()
    val remainingRequirements = listOf(
        privilegeContext != null,
        statusRunning,
        ignoreBatteryOptimizations,
    ).count { !it }
    Scaffold(
        topBar = {
            GkTopAppBar(
                actions = {
                    GkIconButton(
                        imageVector = GkIcons.Close,
                        onClickLabel = UiStrings.action_close,
                        onClick = mainVm::popPage,
                    )
                },
                title = {
                    Text(text = UiStrings.service_partial_disable)
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Text(
                    text = if (remainingRequirements == 0) UiStrings.partial_disable_ready
                    else UiStrings.partial_disable_remaining(remainingRequirements),
                    modifier = Modifier.weight(1f).padding(horizontal = itemHorizontalPadding),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    enabled = store.enableBlockA11yAppList || remainingRequirements == 0,
                    onClick = {
                        mainVm.popPage()
                        if (!store.enableBlockA11yAppList) {
                            mainVm.scope.launchUi {
                                delay(200L)
                                vm.setBlockA11yAppListEnabled(true)
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (store.enableBlockA11yAppList) UiStrings.action_back
                        else UiStrings.action_enable,
                    )
                }
                Spacer(modifier = Modifier.width(itemHorizontalPadding))
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = itemHorizontalPadding)
        ) {
            Text(
                text = UiStrings.partial_disable_intro,
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = UiStrings.partial_disable_description,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BlockA11ySectionTitle(text = UiStrings.usage_requirements)
            BlockA11ySettingsCard {
                BlockA11yRequirementItem(
                    text = UiStrings.privilege_service,
                    onClickLabel = UiStrings.privilege_service_open,
                    satisfied = privilegeContext != null,
                    imageVector = if (privilegeContext != null) GkIcons.Check else GkIcons.KeyboardArrowRight,
                    onClick = {
                        mainVm.navigatePage(PrivilegeServiceRoute)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BlockA11yRequirementItem(
                    text = UiStrings.persistent_notification,
                    satisfied = statusRunning,
                    onClickLabel = UiStrings.persistent_notification_enable,
                    imageVector = if (statusRunning) GkIcons.Check else GkIcons.PlayArrow,
                    onClick = {
                        actionScope.launchUi {
                            mainVm.enableStatusService()
                        }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BlockA11yRequirementItem(
                    text = UiStrings.battery_strategy_unrestricted,
                    satisfied = ignoreBatteryOptimizations,
                    imageVector = if (ignoreBatteryOptimizations) GkIcons.Check else GkIcons.OpenInNew,
                    onClickLabel = UiStrings.battery_optimization_settings_open,
                    onClick = {
                        actionScope.launchUi {
                            mainVm.permissionRequests.ensurePermissions(
                                PermissionStates.ignoreBatteryOptimizations,
                            )
                        }
                    },
                )
            }
            BlockA11ySectionTitle(
                text = UiStrings.partial_disable_background_suggestions,
                optional = true,
            )
            BlockA11ySettingsCard {
                BlockA11yRequirementItem(
                    text = UiStrings.autostart_allow,
                    imageVector = GkIcons.OpenInNew,
                    onClickLabel = UiStrings.app_details_open,
                    onClick = {
                        IntentUtils.openAppDetailsSettings()
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BlockA11yRequirementItem(
                    text = UiStrings.recents_lock,
                    imageVector = GkIcons.OpenInNew,
                    onClickLabel = if (privilegeContext != null) UiStrings.recents_open
                    else UiStrings.recents_lock_manual_hint,
                    onClick = {
                        val privilegeContext = privilegeContextFlow.value
                        if (privilegeContext == null) {
                            toast(UiStrings.recents_lock_manual_hint)
                        } else {
                            privilegeContext.keyevent(KeyEvent.KEYCODE_APP_SWITCH)
                        }
                    },
                )
            }
            BlockA11ySectionTitle(text = UiStrings.usage_notice)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BlockA11yNoticeItem(
                    title = UiStrings.partial_disable_touch_title,
                    text = UiStrings.partial_disable_touch_detail,
                )
                BlockA11yNoticeItem(
                    title = UiStrings.partial_disable_background_title,
                    text = UiStrings.partial_disable_background_detail,
                )
                BlockA11yNoticeItem(
                    title = UiStrings.partial_disable_limits_title,
                    text = UiStrings.partial_disable_limits_detail,
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
                text = UiStrings.optional_label,
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
    val statusColor = if (satisfied) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (satisfied) stateDescription = UiStrings.partial_disable_requirement_ready
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

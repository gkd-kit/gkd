package li.gkd.app.feature.settings

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.META
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.service.A11yService
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.ui.A11YScopeAppListRoute
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.surfaceCardColors
import li.gkd.app.util.AutomatorModeOption
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.ui.component.GkAnimatedBooleanContent
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar

@Serializable
data object WorkModeRoute : NavKey

@Composable
fun WorkModePage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel<WorkModeVm>()
    val writeSecureSettings by PermissionStates.writeSecureSettings.stateFlow.collectAsStateWithLifecycle()
    val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
    val privilegeContext by privilegeContextFlow.collectAsStateWithLifecycle()
    val automatorMode by mainVm.automatorModeFlow.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        GkTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            GkIconButton(
                imageVector = GkIcons.ArrowBack,
                onClick = {
                    mainVm.popPage()
                })
        }, title = {
            Text(text = UiStrings.work_mode_title)
        })
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = itemHorizontalPadding)
                    .fillMaxWidth(),
                onClick = throttle { mainVm.updateAutomatorMode(AutomatorModeOption.A11yMode) },
                colors = surfaceCardColors,
            ) {
                Row(
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = automatorMode == AutomatorModeOption.A11yMode,
                        onClick = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = AutomatorModeOption.A11yMode.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    modifier = Modifier
                        .padding(horizontal = 20.dp),
                    text = UiStrings.work_mode_basic,
                    style = MaterialTheme.typography.titleSmall
                )
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        UiStrings.a11y_permission_grant,
                        UiStrings.a11y_permission_regrant_description
                    ),
                )
                GkAnimatedBooleanContent(
                    targetState = writeSecureSettings || a11yRunning,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 8.dp),
                            text = UiStrings.a11y_permission_ready,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    contentFalse = {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(
                                onClick = throttle { IntentUtils.openA11ySettings() },
                            ) {
                                Text(
                                    text = UiStrings.a11y_enable,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            TextButton(
                                onClick = throttle {
                                    mainVm.navigateWebPage(ShortUrlSet.URL2)
                                },
                            ) {
                                Text(
                                    text = UiStrings.help_view,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                )
                Text(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp),
                    text = UiStrings.work_mode_enhanced,
                    style = MaterialTheme.typography.titleSmall,
                )
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        UiStrings.secure_settings_permission_grant,
                        UiStrings.secure_settings_permission_description,
                    ),
                )
                GkAnimatedBooleanContent(
                    targetState = writeSecureSettings,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 8.dp),
                            text = UiStrings.secure_settings_permission_granted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    contentFalse = {},
                )
                FlowRow(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!writeSecureSettings) {
                        PrivilegeAuthButton()
                    }
                    TextButton(
                        onClick = throttle(vm.scope.launchUiAction {
                            val tutorialText = UiStrings.keep_alive_tile_description(META.appName) +
                                    UiStrings.keep_alive_setup_heading +
                                    UiStrings.keep_alive_setup_open_tiles +
                                    UiStrings.keep_alive_setup_add_tile(META.appName) +
                                    UiStrings.keep_alive_setup_place_tile
                            if (writeSecureSettings) {
                                mainVm.dialogRequests.showMessage(
                                    title = UiStrings.keep_alive_title,
                                    text = tutorialText,
                                )
                            } else if (mainVm.dialogRequests.confirm(
                                    title = UiStrings.keep_alive_title,
                                    text = tutorialText + UiStrings.keep_alive_permission_missing +
                                            UiStrings.keep_alive_permission_description,
                                    confirmText = UiStrings.settings_go_to,
                                    dismissText = UiStrings.action_close,
                                    dismissOnRequest = true,
                                )
                            ) {
                                mainVm.navigatePage(PrivilegeServiceRoute)
                            }
                        })
                    ) {
                        Text(
                            text = UiStrings.keep_alive_title,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .padding(horizontal = itemHorizontalPadding)
                    .fillMaxWidth(),
                onClick = vm.scope.launchUiAction {
                    if (privilegeContext == null) {
                        if (mainVm.dialogRequests.confirm(
                                title = UiStrings.privilege_service_required,
                                text = UiStrings.automation_privilege_required_description,
                                confirmText = UiStrings.settings_go_to,
                                dismissOnRequest = true,
                            )
                        ) {
                            mainVm.navigatePage(PrivilegeServiceRoute)
                        }
                    } else {
                        mainVm.updateAutomatorMode(AutomatorModeOption.AutomationMode)
                    }
                },
                colors = surfaceCardColors,
            ) {
                Row(
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = automatorMode == AutomatorModeOption.AutomationMode,
                        onClick = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = AutomatorModeOption.AutomationMode.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        UiStrings.automation_a11y_description,
                        UiStrings.automation_no_display_issues,
                        UiStrings.automation_undetectable_a11y,
                        UiStrings.automation_scope_compatibility_hint,
                    ),
                )
                GkAnimatedBooleanContent(
                    targetState = privilegeContext != null,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 8.dp),
                            text = UiStrings.privilege_service_connected,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    contentFalse = {},
                )
                FlowRow(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = throttle {
                            mainVm.navigatePage(A11YScopeAppListRoute)
                        },
                    ) {
                        Text(
                            text = UiStrings.a11y_scoped,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            GkPageBottomSpace()
        }
    }

}

@Composable
private fun PrivilegeAuthButton(
    modifier: Modifier = Modifier,
) {
    val mainVm = MainViewModel.requireCurrent()
    TextButton(
        modifier = modifier,
        onClick = throttle {
            mainVm.navigatePage(PrivilegeServiceRoute)
        },
    ) {
        Text(
            text = UiStrings.permission_grant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun TextListItem(
    list: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val lineHeightDp = LocalDensity.current.run { style.lineHeight.toDp() }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        list.forEach { text ->
            Row {
                Spacer(
                    modifier = Modifier
                        .padding(vertical = (lineHeightDp - 4.dp) / 2)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .size(4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, style = style)
            }
        }
    }
}

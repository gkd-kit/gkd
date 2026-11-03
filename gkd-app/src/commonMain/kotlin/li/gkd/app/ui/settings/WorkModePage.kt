package li.gkd.app.ui.settings

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.model.findOption
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.*
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAnimatedBooleanContent
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkPermissionRestrictionDialog
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.gkTopAppBarScroll
import li.gkd.app.ui.home.dashboardPlatformState
import li.gkd.app.ui.navigation.CompanionScopeAppListRoute
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.platform.RefreshPermissions
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.surfaceCardColors
import org.jetbrains.compose.resources.stringResource

@Composable
fun WorkModePage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { WorkModeViewModel() }
    RefreshPermissions()
    val platform = dashboardPlatformState()
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val automatorMode: AutomatorMode = AutomatorMode.entries.findOption(store.automatorMode)

    val onPrivilege: () -> Unit = { mainVm.navigator.navigate(PrivilegeServiceRoute) }

    val automationAvailable =
        platform.privilegeAvailable && platform.privilegeCapabilities?.restricted != true
    val privilegeAction by vm.privilegeAction.collectAsStateWithLifecycle()
    if (privilegeAction != null)
        GkPermissionRestrictionDialog(
            privilegeAvailable = platform.privilegeAvailable,
            capabilities = platform.privilegeCapabilities,
            appRestrictions = platform.appRestrictions,
            onDismiss = { vm.setPrivilegeAction(null) },
            onPrivilege = onPrivilege,
            description = Res.string.automation_mode_privilege_notice,
            alternativeButton = {
                TextButton(
                    onClick = {
                        vm.setPrivilegeAction(null)
                        SettingsRepository.updateAutomatorMode(AutomatorMode.Automation.value)
                    },
                ) {
                    Text(
                        stringResource(Res.string.action_switch_only),
                    )
                }
            },
        )
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = mainVm.navigator::pop,
                    )
                },
                title = {
                    Text(text = stringResource(Res.string.work_mode_title))
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
        ) {
            Card(
                modifier = Modifier.padding(horizontal = itemHorizontalPadding).fillMaxWidth(),
                onClick = { SettingsRepository.updateAutomatorMode(AutomatorMode.A11y.value) },
                colors = surfaceCardColors,
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            start = 20.dp,
                            top = 16.dp,
                            end = 20.dp,
                            bottom = 12.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = automatorMode == AutomatorMode.A11y,
                        onClick = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = stringResource(AutomatorMode.A11y.labelResource),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (!platform.writeSecureSettings) {
                    WorkModeSectionTitle(
                        text = stringResource(Res.string.work_mode_basic),
                        ready = platform.a11yEnabled,
                    )
                    TextListItem(
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        list =
                            listOf(
                                stringResource(Res.string.a11y_permission_grant),
                                stringResource(Res.string.a11y_permission_regrant_description),
                            ),
                    )
                    GkAnimatedBooleanContent(
                        targetState = platform.a11yRunning,
                        contentTrue = { Spacer(modifier = Modifier.height(20.dp)) },
                        contentFalse = {
                            Column {
                                FlowRow(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                            .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    TextButton(onClick = vm::requestA11yEnable) {
                                        Text(
                                            text = stringResource(Res.string.a11y_enable),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            mainVm.navigator.openWebPage(AppLinks.WorkModeHelp)
                                        },
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.help_view),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        },
                    )
                }
                WorkModeSectionTitle(
                    text = stringResource(Res.string.work_mode_enhanced),
                    ready = platform.writeSecureSettings,
                )
                TextListItem(
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list =
                        listOf(
                            stringResource(Res.string.secure_settings_permission_grant),
                            stringResource(Res.string.secure_settings_permission_description),
                        ),
                )
                Spacer(modifier = Modifier.height(20.dp))
                WorkModeSectionTitle(
                    text = stringResource(Res.string.work_mode_privileged_optimization),
                    ready =
                        platform.privilegeAvailable &&
                            platform.privilegeCapabilities?.injectEvents == true,
                )
                TextListItem(
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list =
                        listOf(
                            stringResource(Res.string.work_mode_privileged_a11y_description),
                            stringResource(Res.string.work_mode_privileged_input_description),
                        ),
                )
                if (!platform.writeSecureSettings) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PrivilegeAuthButton(onPrivilege)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.padding(horizontal = itemHorizontalPadding).fillMaxWidth(),
                onClick = {
                    if (automationAvailable) {
                        SettingsRepository.updateAutomatorMode(AutomatorMode.Automation.value)
                    } else vm.setPrivilegeAction(WorkModePrivilegeAction.SwitchMode)
                },
                colors = surfaceCardColors,
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            start = 20.dp,
                            top = 16.dp,
                            end = 20.dp,
                            bottom = 12.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = automatorMode == AutomatorMode.Automation,
                        onClick = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = stringResource(AutomatorMode.Automation.labelResource),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextListItem(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list =
                        listOf(
                            stringResource(Res.string.automation_a11y_description),
                            stringResource(Res.string.automation_no_display_issues),
                            stringResource(Res.string.automation_undetectable_a11y),
                            stringResource(Res.string.automation_touch_optimization),
                        ),
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = { mainVm.navigator.navigate(CompanionScopeAppListRoute) },
                    ) {
                        Text(
                            text = stringResource(Res.string.companion_scope),
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
private fun WorkModeSectionTitle(text: String, ready: Boolean) {
    val readyDescription = stringResource(Res.string.work_mode_ready)
    val style = MaterialTheme.typography.titleSmall
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = style,
            modifier = Modifier.semantics { if (ready) stateDescription = readyDescription },
        )
        if (ready) {
            GkIcon(
                imageVector = GkIcons.Check,
                modifier = Modifier.iconTextSize(style),
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun PrivilegeAuthButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Text(
            text = stringResource(Res.string.permission_grant),
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
                    modifier =
                        Modifier.padding(vertical = (lineHeightDp - 4.dp) / 2)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                            .size(4.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, style = style)
            }
        }
    }
}

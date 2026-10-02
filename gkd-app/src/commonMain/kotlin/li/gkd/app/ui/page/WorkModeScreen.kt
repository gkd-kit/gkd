package li.gkd.app.ui.page

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_enable
import li.gkd.app.resources.a11y_permission_grant
import li.gkd.app.resources.a11y_permission_ready
import li.gkd.app.resources.a11y_permission_regrant_description
import li.gkd.app.resources.a11y_scoped
import li.gkd.app.resources.action_cancel
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_understood
import li.gkd.app.resources.automation_a11y_description
import li.gkd.app.resources.automation_no_display_issues
import li.gkd.app.resources.automation_privilege_required_description
import li.gkd.app.resources.automation_scope_compatibility_hint
import li.gkd.app.resources.automation_undetectable_a11y
import li.gkd.app.resources.help_view
import li.gkd.app.resources.keep_alive_permission_description
import li.gkd.app.resources.keep_alive_permission_missing
import li.gkd.app.resources.keep_alive_setup_add_tile
import li.gkd.app.resources.keep_alive_setup_heading
import li.gkd.app.resources.keep_alive_setup_open_tiles
import li.gkd.app.resources.keep_alive_setup_place_tile
import li.gkd.app.resources.keep_alive_tile_description
import li.gkd.app.resources.keep_alive_title
import li.gkd.app.resources.permission_grant
import li.gkd.app.resources.privilege_service_connected
import li.gkd.app.resources.privilege_service_required
import li.gkd.app.resources.secure_settings_permission_description
import li.gkd.app.resources.secure_settings_permission_grant
import li.gkd.app.resources.secure_settings_permission_granted
import li.gkd.app.resources.settings_go_to
import li.gkd.app.resources.work_mode_basic
import li.gkd.app.resources.work_mode_enhanced
import li.gkd.app.resources.work_mode_title
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkAnimatedBooleanContent
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.option.AutomatorModeOption
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.surfaceCardColors
import org.jetbrains.compose.resources.stringResource


@Composable
fun WorkModeScreen(
    writeSecureSettings: Boolean,
    a11yRunning: Boolean,
    privilegeAvailable: Boolean,
    automatorMode: AutomatorModeOption,
    onBack: () -> Unit,
    onA11yMode: () -> Unit,
    onAutomationMode: () -> Unit,
    onEnableA11y: () -> Unit,
    onHelp: () -> Unit,
    onPrivilege: () -> Unit,
    appName: String,
    onScope: () -> Unit,
) {
    var showPrivilegeRequired by rememberSaveable { mutableStateOf(false) }
    if (showPrivilegeRequired) GkAlertDialog(
        onDismissRequest = { showPrivilegeRequired = false },
        title = { Text(stringResource(Res.string.privilege_service_required)) },
        text = { Text(stringResource(Res.string.automation_privilege_required_description)) },
        confirmButton = {
            TextButton(onClick = {
                showPrivilegeRequired = false; onPrivilege()
            }) { Text(stringResource(Res.string.settings_go_to)) }
        },
        dismissButton = {
            TextButton(onClick = { showPrivilegeRequired = false }) {
                Text(
                    stringResource(Res.string.action_cancel)
                )
            }
        },
    )
    var showKeepAlive by rememberSaveable { mutableStateOf(false) }
    if (showKeepAlive) {
        val tutorial = stringResource(Res.string.keep_alive_tile_description, appName) +
                stringResource(Res.string.keep_alive_setup_heading) + stringResource(Res.string.keep_alive_setup_open_tiles) +
                stringResource(
                    Res.string.keep_alive_setup_add_tile,
                    appName
                ) + stringResource(Res.string.keep_alive_setup_place_tile)
        GkAlertDialog(
            onDismissRequest = { showKeepAlive = false },
            title = { Text(stringResource(Res.string.keep_alive_title)) },
            text = {
                Text(
                    tutorial + if (writeSecureSettings) "" else
                        stringResource(Res.string.keep_alive_permission_missing) + stringResource(
                            Res.string.keep_alive_permission_description
                        )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showKeepAlive = false; if (!writeSecureSettings) onPrivilege()
                }) {
                    Text(stringResource(if (writeSecureSettings) Res.string.action_understood else Res.string.settings_go_to))
                }
            },
            dismissButton = if (writeSecureSettings) null else {
                {
                    TextButton(onClick = {
                        showKeepAlive = false
                    }) { Text(stringResource(Res.string.action_close)) }
                }
            },
        )
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GkScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                GkIconButton(
                    imageVector = GkIcons.ArrowBack,
                    onClick = onBack
                )
            }, title = {
                Text(text = stringResource(Res.string.work_mode_title))
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
                onClick = onA11yMode,
                colors = surfaceCardColors,
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        top = 16.dp,
                        end = 20.dp,
                        bottom = 12.dp
                    ),
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
                    text = stringResource(Res.string.work_mode_basic),
                    style = MaterialTheme.typography.titleSmall
                )
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        stringResource(Res.string.a11y_permission_grant),
                        stringResource(Res.string.a11y_permission_regrant_description)
                    ),
                )
                GkAnimatedBooleanContent(
                    targetState = writeSecureSettings || a11yRunning,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 8.dp),
                            text = stringResource(Res.string.a11y_permission_ready),
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
                                onClick = onEnableA11y,
                            ) {
                                Text(
                                    text = stringResource(Res.string.a11y_enable),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            TextButton(
                                onClick = onHelp,
                            ) {
                                Text(
                                    text = stringResource(Res.string.help_view),
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
                    text = stringResource(Res.string.work_mode_enhanced),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        stringResource(Res.string.secure_settings_permission_grant),
                        stringResource(Res.string.secure_settings_permission_description),
                    ),
                )
                GkAnimatedBooleanContent(
                    targetState = writeSecureSettings,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 8.dp),
                            text = stringResource(Res.string.secure_settings_permission_granted),
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
                        PrivilegeAuthButton(onPrivilege)
                    }
                    TextButton(
                        onClick = { showKeepAlive = true },
                    ) {
                        Text(
                            text = stringResource(Res.string.keep_alive_title),
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
                onClick = {
                    if (privilegeAvailable) onAutomationMode() else showPrivilegeRequired = true
                },
                colors = surfaceCardColors,
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        top = 16.dp,
                        end = 20.dp,
                        bottom = 12.dp
                    ),
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
                        stringResource(Res.string.automation_a11y_description),
                        stringResource(Res.string.automation_no_display_issues),
                        stringResource(Res.string.automation_undetectable_a11y),
                        stringResource(Res.string.automation_scope_compatibility_hint),
                    ),
                )
                GkAnimatedBooleanContent(
                    targetState = privilegeAvailable,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 8.dp),
                            text = stringResource(Res.string.privilege_service_connected),
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
                        onClick = onScope,
                    ) {
                        Text(
                            text = stringResource(Res.string.a11y_scoped),
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

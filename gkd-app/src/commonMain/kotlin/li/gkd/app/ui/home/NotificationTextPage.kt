package li.gkd.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.META
import li.gkd.app.notif.formatNotificationTemplate
import li.gkd.app.resources.*
import li.gkd.app.rule.ruleGroupState
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkTemplateVariableRow
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.navigation.GkEditor
import li.gkd.app.ui.page.EditorSaveResult
import li.gkd.app.ui.page.EditorSession
import li.gkd.app.ui.platform.LocalUiHost
import li.gkd.app.ui.platform.hideIme
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.stringResource

@Composable
fun NotificationTextPage() {
    val host = LocalUiHost.current
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { NotificationTextViewModel() }
    val focusManager = LocalFocusManager.current
    var titleFocused by remember { mutableStateOf(false) }
    var subtitleFocused by remember { mutableStateOf(false) }
    val rules by ruleGroupState.collectAsStateWithLifecycle()
    val count by SettingsRepository.actionCount.collectAsStateWithLifecycle()
    val groups = rules.value?.groups
    val renderPreview: (String) -> String = { text ->
        formatNotificationTemplate(
            text,
            groups?.globalGroups?.size,
            groups?.appSize,
            groups?.appGroupSize,
            count,
        )
    }

    val titleEnabled by vm.titleEnabled.collectAsStateWithLifecycle()
    val subtitleEnabled by vm.subtitleEnabled.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val defaultText =
        when (val state = rules) {
            Loadable.Loading -> stringResource(Res.string.config_loading)
            is Loadable.Failure -> stringResource(Res.string.data_load_failed)
            is Loadable.Ready -> {
                val summary =
                    dashboardSummary(state.value.groups) ?: stringResource(Res.string.rules_empty)
                if (count > 0) stringResource(Res.string.action_count_summary, summary, count)
                else summary
            }
        }

    GkEditor(
        EditorSession(
            title = stringResource(Res.string.notification_text),
            hasChanges = vm::hasChanges,
            onSave = {
                if (vm.save()) {
                    ToastUtils.show(Res.string.update_success.getSync())
                }
                EditorSaveResult.Saved
            },
        ),
        navigator = mainVm.navigator,
        hideIme = host::hideIme,
        actions = vm.editorActions,
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GkTextSwitch(
                title = stringResource(Res.string.notification_title_custom),
                checked = titleEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled && titleFocused) {
                        focusManager.clearFocus()
                        host.hideIme()
                    }
                    vm.setTitleEnabled(enabled)
                },
                paddingDisabled = true,
            )
            OutlinedTextField(
                enabled = titleEnabled,
                value = title,
                onValueChange = { vm.setTitle(it.filter { c -> c !in "\n\r" }.take(32)) },
                label = { Text(stringResource(Res.string.notification_title_label)) },
                placeholder = { Text(stringResource(Res.string.template_text_input_hint)) },
                singleLine = true,
                supportingText = {
                    Text(
                        stringResource(Res.string.progress_fraction, title.length, 32),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
                modifier = Modifier.fillMaxWidth().onFocusChanged { titleFocused = it.isFocused },
            )
            GkTextSwitch(
                title = stringResource(Res.string.notification_subtitle_custom),
                checked = subtitleEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled && subtitleFocused) {
                        focusManager.clearFocus()
                        host.hideIme()
                    }
                    vm.setSubtitleEnabled(enabled)
                },
                paddingDisabled = true,
            )
            OutlinedTextField(
                enabled = subtitleEnabled,
                value = text,
                onValueChange = { vm.setText(it.take(64)) },
                label = { Text(stringResource(Res.string.notification_subtitle_label)) },
                placeholder = { Text(stringResource(Res.string.template_text_input_hint)) },
                minLines = 2,
                maxLines = 4,
                supportingText = {
                    Text(
                        stringResource(Res.string.progress_fraction, text.length, 64),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
                modifier =
                    Modifier.fillMaxWidth().onFocusChanged { subtitleFocused = it.isFocused },
            )
            NotificationTextPreview(
                if (titleEnabled) renderPreview(title) else META.appName,
                if (subtitleEnabled) renderPreview(text) else defaultText,
            )
            NotificationTemplateVariables()
            GkPageBottomSpace()
        }
    }
}

@Composable
private fun NotificationTextPreview(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.notification_text_preview),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(Res.string.notification_text_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
            )
            if (text.isNotEmpty()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun NotificationTemplateVariables() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.notification_template_variables),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(Res.string.notification_template_variables_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GkTemplateVariableRow(
                $$"${i}",
                stringResource(Res.string.notification_variable_global_rules),
            )
            GkTemplateVariableRow($$"${k}", stringResource(Res.string.notification_variable_apps))
            GkTemplateVariableRow(
                $$"${u}",
                stringResource(Res.string.notification_variable_app_rules),
            )
            GkTemplateVariableRow(
                $$"${n}",
                stringResource(Res.string.notification_variable_triggers),
            )
        }
    }
}

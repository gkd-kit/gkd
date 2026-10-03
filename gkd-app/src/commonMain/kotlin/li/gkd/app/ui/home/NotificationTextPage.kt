package li.gkd.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.resources.Res
import li.gkd.app.resources.notification_body_label
import li.gkd.app.resources.notification_template_variables
import li.gkd.app.resources.notification_template_variables_hint
import li.gkd.app.resources.notification_text
import li.gkd.app.resources.notification_text_custom
import li.gkd.app.resources.notification_text_preview
import li.gkd.app.resources.notification_text_preview_hint
import li.gkd.app.resources.notification_title_label
import li.gkd.app.resources.notification_variable_app_rules
import li.gkd.app.resources.notification_variable_apps
import li.gkd.app.resources.notification_variable_global_rules
import li.gkd.app.resources.notification_variable_triggers
import li.gkd.app.resources.progress_fraction
import li.gkd.app.resources.template_text_input_hint
import li.gkd.app.resources.update_success
import li.gkd.app.rule.ruleGroupState
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkSwitch
import li.gkd.app.ui.component.GkTemplateVariableRow
import li.gkd.app.ui.navigation.EditorFrame
import li.gkd.app.ui.page.EditorSession
import li.gkd.app.ui.text.getSync
import org.jetbrains.compose.resources.stringResource

@Composable
fun NotificationTextPage(showToast: (String) -> Unit, editorFrame: EditorFrame) {
    val initial = remember { settings.value }
    val rules by ruleGroupState.collectAsStateWithLifecycle()
    val count by SettingsRepository.actionCount.collectAsStateWithLifecycle()
    val groups = rules.value?.groups
    val renderPreview: (String) -> String = { text ->
        HomeDataText.format(
            text,
            groups?.globalGroups?.size,
            groups?.appSize,
            groups?.appGroupSize,
            count,
        )
    }


    var enabled by rememberSaveable { mutableStateOf(initial.useCustomNotifText) }
    var title by rememberSaveable { mutableStateOf(initial.customNotifTitle) }
    var text by rememberSaveable { mutableStateOf(initial.customNotifText) }

    editorFrame(
        EditorSession(
            title = stringResource(Res.string.notification_text),
            hasChanges = {
                enabled != initial.useCustomNotifText ||
                    title != initial.customNotifTitle ||
                    text != initial.customNotifText
            },
            onSave = {
                if (SettingsRepository.saveNotificationText(enabled, title, text)) {
                    showToast(Res.string.update_success.getSync())
                }
            },
        ),
        null,
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .toggleable(
                                value = enabled,
                                role = Role.Switch,
                                onValueChange = { enabled = it },
                            )
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            stringResource(Res.string.notification_text_custom),
                            modifier = Modifier.weight(1f),
                        )
                        GkSwitch(checked = enabled, onCheckedChange = null)
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.filter { c -> c !in "\n\r" }.take(32) },
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
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(64) },
                label = { Text(stringResource(Res.string.notification_body_label)) },
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
                modifier = Modifier.fillMaxWidth(),
            )
            NotificationTextPreview(renderPreview(title), renderPreview(text))
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
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
            )
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

package li.gkd.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_toast
import li.gkd.app.resources.action_toast_enable
import li.gkd.app.resources.action_toast_example_group
import li.gkd.app.resources.action_toast_example_rule
import li.gkd.app.resources.action_toast_preview
import li.gkd.app.resources.action_toast_preview_hint
import li.gkd.app.resources.action_toast_style_custom
import li.gkd.app.resources.action_toast_style_hint
import li.gkd.app.resources.action_toast_style_system
import li.gkd.app.resources.action_toast_text
import li.gkd.app.resources.action_toast_variable_count
import li.gkd.app.resources.action_toast_variable_group
import li.gkd.app.resources.action_toast_variable_rule
import li.gkd.app.resources.action_toast_variables
import li.gkd.app.resources.action_toast_variables_hint
import li.gkd.app.resources.progress_fraction
import li.gkd.app.resources.toast_style
import li.gkd.app.resources.toast_text_input_hint
import li.gkd.app.resources.update_success
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkSwitch
import li.gkd.app.ui.component.GkTemplateVariableRow
import li.gkd.app.ui.navigation.EditorFrame
import li.gkd.app.ui.page.EditorSession
import li.gkd.app.ui.text.getSync
import li.gkd.app.ui.util.ActionToastTemplate
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActionToastPage(
    showToast: (String) -> Unit,
    editorFrame: EditorFrame,
    onPreview: (String, Boolean) -> Unit,
) {
    val initial = remember { settings.value }

    var enabled by rememberSaveable { mutableStateOf(initial.toastWhenClick) }
    var systemStyle by rememberSaveable { mutableStateOf(initial.useSystemToast) }
    var text by rememberSaveable { mutableStateOf(initial.actionToast) }
    val previewText =
        ActionToastTemplate.render(
            text,
            stringResource(Res.string.action_toast_example_rule),
            stringResource(Res.string.action_toast_example_group),
            3L,
        )

    editorFrame(
        EditorSession(
            title = stringResource(Res.string.action_toast),
            hasChanges = {
                enabled != initial.toastWhenClick ||
                    systemStyle != initial.useSystemToast ||
                    text != initial.actionToast
            },
            saveEnabled = text.isNotEmpty() && text.length <= 64,
            onSave = {
                if (SettingsRepository.saveActionToast(enabled, systemStyle, text)) {
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
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .toggleable(
                                value = enabled,
                                role = Role.Switch,
                                onValueChange = { enabled = it },
                            )
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(Res.string.action_toast_enable),
                        modifier = Modifier.weight(1f),
                    )
                    GkSwitch(checked = enabled, onCheckedChange = null)
                }
            }
            ActionToastCard {
                Text(
                    stringResource(Res.string.toast_style),
                    style = MaterialTheme.typography.titleSmall,
                )
                Column(Modifier.selectableGroup()) {
                    ActionToastStyleOption(
                        stringResource(Res.string.action_toast_style_custom),
                        !systemStyle,
                    ) {
                        systemStyle = false
                        onPreview(previewText, false)
                    }
                    ActionToastStyleOption(
                        stringResource(Res.string.action_toast_style_system),
                        systemStyle,
                    ) {
                        systemStyle = true
                        onPreview(previewText, true)
                    }
                }
                Text(
                    stringResource(Res.string.action_toast_style_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(64) },
                label = { Text(stringResource(Res.string.action_toast_text)) },
                placeholder = { Text(stringResource(Res.string.toast_text_input_hint)) },
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.action_toast_preview),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(Res.string.action_toast_preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ActionToastCard {
                Text(previewText, style = MaterialTheme.typography.bodyMedium)
            }
            ActionToastCard {
                Text(
                    stringResource(Res.string.action_toast_variables),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(Res.string.action_toast_variables_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GkTemplateVariableRow(
                    $$"${1}",
                    stringResource(Res.string.action_toast_variable_rule),
                )
                GkTemplateVariableRow(
                    $$"${2}",
                    stringResource(Res.string.action_toast_variable_group),
                )
                GkTemplateVariableRow(
                    $$"${3}",
                    stringResource(Res.string.action_toast_variable_count),
                )
            }
            GkPageBottomSpace()
        }
    }
}

@Composable
private fun ActionToastCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ActionToastStyleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(MaterialTheme.shapes.small)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

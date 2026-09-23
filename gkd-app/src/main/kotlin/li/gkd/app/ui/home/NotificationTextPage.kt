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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.notif.replaceNotificationTemplate
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.store.AppStore.actionCountFlow
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkEditorScaffold
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkSwitch
import li.gkd.app.ui.component.GkTemplateVariableRow
import li.gkd.app.util.ToastUtils.toast

@Serializable
data object NotificationTextRoute : NavKey

@Composable
fun NotificationTextPage() {
    val vm = viewModel<SettingsVm>()
    val initialStore = remember { storeFlow.value }
    val initialEnabled by rememberSaveable { mutableStateOf(initialStore.useCustomNotifText) }
    val initialTitle by rememberSaveable { mutableStateOf(initialStore.customNotifTitle) }
    val initialText by rememberSaveable { mutableStateOf(initialStore.customNotifText) }
    var enabled by rememberSaveable { mutableStateOf(initialEnabled) }
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var text by rememberSaveable { mutableStateOf(initialText) }

    GkEditorScaffold(
        title = { Text(UiStrings.notification_text) },
        hasChanges = { enabled != initialEnabled || title != initialTitle || text != initialText },
        onSave = {
            if (vm.saveNotificationText(enabled, title, text)) toast(UiStrings.update_success)
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
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
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(value = enabled, role = Role.Switch, onValueChange = { enabled = it })
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(UiStrings.notification_text_custom, modifier = Modifier.weight(1f))
                        GkSwitch(checked = enabled, onCheckedChange = null)
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.filter { c -> c !in "\n\r" }.take(32) },
                label = { Text(UiStrings.notification_title_label) },
                placeholder = { Text(UiStrings.template_text_input_hint) },
                singleLine = true,
                supportingText = {
                    Text(UiStrings.progress_fraction(title.length, 32),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(64) },
                label = { Text(UiStrings.notification_body_label) },
                placeholder = { Text(UiStrings.template_text_input_hint) },
                minLines = 2,
                maxLines = 4,
                supportingText = {
                    Text(UiStrings.progress_fraction(text.length, 64),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            NotificationTextPreview(title, text)
            NotificationTemplateVariables()
            GkPageBottomSpace()
        }
    }
}

@Composable
private fun NotificationTextPreview(title: String, text: String) {
    val ruleSummary by SubscriptionState.ruleSummaryFlow.collectAsStateWithLifecycle()
    val actionCount by actionCountFlow.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(UiStrings.notification_text_preview, style = MaterialTheme.typography.titleSmall)
        Text(
            UiStrings.notification_text_preview_hint,
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
            Text(title.replaceNotificationTemplate(ruleSummary, actionCount),
                style = MaterialTheme.typography.titleMedium)
            Text(text.replaceNotificationTemplate(ruleSummary, actionCount),
                style = MaterialTheme.typography.bodyMedium)
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
            Text(UiStrings.notification_template_variables, style = MaterialTheme.typography.titleSmall)
            Text(
                UiStrings.notification_template_variables_hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GkTemplateVariableRow($$"${i}", UiStrings.notification_variable_global_rules)
            GkTemplateVariableRow($$"${k}", UiStrings.notification_variable_apps)
            GkTemplateVariableRow($$"${u}", UiStrings.notification_variable_app_rules)
            GkTemplateVariableRow($$"${n}", UiStrings.notification_variable_triggers)
        }
    }
}

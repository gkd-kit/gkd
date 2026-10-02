package li.gkd.app.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_reload_latest
import li.gkd.app.resources.app_count
import li.gkd.app.resources.category_add
import li.gkd.app.resources.category_description
import li.gkd.app.resources.category_edit
import li.gkd.app.resources.category_edit_conflict
import li.gkd.app.resources.category_group_count
import li.gkd.app.resources.category_name_prefix
import li.gkd.app.resources.category_no_matching_groups
import li.gkd.app.subscription.CategoryPolicy
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionJson.json
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.text.subscriptionMessageResource
import org.jetbrains.compose.resources.stringResource


@Composable
fun CategoryEditorScreen(
    subscription: RawSubscription,
    categoryKey: Int?,
    policy: CategoryPolicy,
    frame: EditorFrame,
    appName: @Composable (RawSubscription.RawApp, Modifier) -> Unit,
    onSave: suspend (name: String, description: String) -> Unit,
) {
    val category = subscription.categories.find { it.key == categoryKey }
    val categorySnapshot = remember(category) { category?.let { json.encodeToString(it) } }
    var originalCategory by rememberSaveable { mutableStateOf(categorySnapshot) }
    var name by rememberSaveable { mutableStateOf(category?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(category?.desc.orEmpty()) }
    var originalName by rememberSaveable { mutableStateOf(name) }
    var originalDescription by rememberSaveable { mutableStateOf(description) }
    var previewName by remember(subscription, categoryKey) { mutableStateOf(name) }
    LaunchedEffect(name) {
        delay(300)
        previewName = name
    }
    val conflict = categoryKey != null && categorySnapshot != originalCategory
    val validation = remember(subscription, categoryKey, name) {
        runCatching { policy.validateEdit(subscription, categoryKey, name) }
    }
    val preview = remember(subscription, categoryKey, previewName) {
        runCatching { policy.previewEdit(subscription, categoryKey, previewName, "") }
    }
    val error = if (conflict) stringResource(Res.string.category_edit_conflict)
    else validation.exceptionOrNull()?.subscriptionMessageResource()
    val previewCategoryKey = categoryKey ?: preview.getOrNull()?.categories?.maxOfOrNull { it.key }
    val apps = preview.getOrNull()?.let { edited ->
        previewCategoryKey?.let(edited::getCategoryApps)
    }.orEmpty()
    val nameError = error?.takeIf { !conflict && name.isNotBlank() }
    val descriptionFocusRequester = remember { FocusRequester() }
    frame(
        EditorSession(
            title = stringResource(if (categoryKey == null) Res.string.category_add else Res.string.category_edit),
            hasChanges = { name.trim() != originalName.trim() || description.trim() != originalDescription.trim() },
            saveEnabled = error == null,
            onSave = { onSave(name.trim(), description.trim()) },
        )
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(
                            nameError ?: stringResource(Res.string.category_name_prefix),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    isError = nameError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { descriptionFocusRequester.requestFocus() },
                    ),
                    modifier = Modifier.fillMaxWidth().autoFocus(immediateFocus = true),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.category_description)) },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().focusRequester(descriptionFocusRequester),
                )
            }
            if (conflict) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            stringResource(Res.string.category_edit_conflict),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (category != null) {
                            TextButton(onClick = {
                                originalCategory = categorySnapshot
                                name = category.name
                                description = category.desc.orEmpty()
                                originalName = name
                                originalDescription = description
                            }) { Text(stringResource(Res.string.action_reload_latest)) }
                        }
                    }
                }
            }
            if (!conflict && name.isNotBlank()) {
                if (apps.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.category_no_matching_groups),
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(apps.size.toString(), style = MaterialTheme.typography.bodyMedium)
                        GkIcon(
                            imageVector = GkIcons.Android,
                            modifier = Modifier.size(18.dp),
                            contentDescription = stringResource(Res.string.app_count, apps.size),
                        )
                        val groupCount = apps.sumOf { it.groups.size }
                        Text(groupCount.toString(), style = MaterialTheme.typography.bodyMedium)
                        GkIcon(
                            imageVector = GkIcons.FlashOn,
                            modifier = Modifier.size(18.dp),
                            contentDescription = stringResource(
                                Res.string.category_group_count,
                                groupCount
                            ),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        apps.forEachIndexed { index, app ->
                            item(key = "app:${app.id}", contentType = "app") {
                                appName(
                                    app, Modifier.fillMaxWidth().padding(
                                        top = if (index == 0) 0.dp else 12.dp, bottom = 4.dp,
                                    )
                                )
                            }
                            items(
                                app.groups,
                                key = { "group:${app.id}:${it.key}" },
                                contentType = { "group" }) { group ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.small,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(group.name, style = MaterialTheme.typography.bodyLarge)
                                        group.desc?.takeIf { it.isNotBlank() }?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package li.gkd.app.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_rule_input_hint
import li.gkd.app.resources.global_rule_input_hint
import li.gkd.app.resources.rule_add
import li.gkd.app.resources.rule_edit
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.EditorFrame
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.navigation.inputInsets
import li.gkd.app.ui.page.EditorSession
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.style.getJson5Transformation
import li.gkd.app.ui.style.scaffoldPadding
import org.jetbrains.compose.resources.stringResource

@Composable
fun UpsertRuleGroupPage(
    route: UpsertRuleGroupRoute,
    onBack: () -> Unit,
    replaceRoute: (AppRoute) -> Unit,
    showToast: (String) -> Unit,
    editorFrame: EditorFrame,
) {
    val vm = viewModel {
        UpsertRuleGroupViewModel(route, showToast)
    }
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var addedAppId by remember { mutableStateOf<String?>(null) }
    GkSubscriptionPageContent(vm.uiState, onBack) { data ->
        val text = draft ?: data.initialText
        val inputInsets: Modifier = inputInsets()

        editorFrame(
            EditorSession(
                title =
                    stringResource(if (vm.isEdit) Res.string.rule_edit else Res.string.rule_add),
                hasChanges = { vm.hasTextChanged(text) },
                saveEnabled = text.isNotBlank(),
                onSave = { addedAppId = vm.saveRule(text) },
            ),
            {
                if (route.forward) {
                    replaceRoute(
                        if (route.appId == null) SubsGlobalGroupListRoute(route.subsId)
                        else SubsAppGroupListRoute(route.subsId, addedAppId ?: route.appId)
                    )
                } else onBack()
            },
        ) { paddingValues ->
            val textColors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                )
            Box(modifier = Modifier.scaffoldPadding(paddingValues).fillMaxSize()) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyLarge
                ) {
                    val modifier =
                        Modifier.autoFocus(immediateFocus = true).fillMaxSize().then(inputInsets)
                    TextField(
                        value = text,
                        onValueChange = {
                            vm.beginEditing()
                            draft = it
                        },
                        modifier = modifier,
                        shape = RectangleShape,
                        colors = textColors,
                        visualTransformation = getJson5Transformation(LocalDarkTheme.current),
                        placeholder = {
                            Text(
                                text =
                                    if (vm.isApp) stringResource(Res.string.app_rule_input_hint)
                                    else stringResource(Res.string.global_rule_input_hint)
                            )
                        },
                    )
                }
                if (text.isNotEmpty()) {
                    Text(
                        text = text.length.toString(),
                        modifier =
                            Modifier.padding(8.dp)
                                .align(Alignment.TopEnd)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

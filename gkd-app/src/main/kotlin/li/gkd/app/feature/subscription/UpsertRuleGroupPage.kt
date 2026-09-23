package li.gkd.app.feature.subscription

import li.gkd.app.MainViewModel

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.setValue
import li.gkd.app.ui.component.GkEditorScaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.style.getJson5Transformation
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.autoFocus

@Serializable
data class UpsertRuleGroupRoute(
    val subsId: Long,
    val groupKey: Int? = null,
    val appId: String? = null,
    val forward: Boolean = false,
) : NavKey

@Composable
fun UpsertRuleGroupPage(route: UpsertRuleGroupRoute) {
    val subsId = route.subsId
    val appId = route.appId
    val forward = route.forward

    val mainVm = MainViewModel.requireCurrent()
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { UpsertRuleGroupVm(route) }
    GkSubscriptionPageContent(vm.uiState) { state ->
        val editedText by vm.textFlow.collectAsStateWithLifecycle()
        val text = editedText ?: state.initialText

        var addedAppId by remember { mutableStateOf<String?>(null) }
        GkEditorScaffold(
            title = { Text(if (vm.isEdit) UiStrings.rule_edit else UiStrings.rule_add) },
            hasChanges = vm::hasTextChanged,
            saveEnabled = text.isNotBlank(),
            onSave = { addedAppId = vm.saveRule() },
            onSaved = {
                if (forward) {
                    mainVm.navigatePage(
                        if (appId == null) SubsGlobalGroupListRoute(subsItemId = subsId)
                        else SubsAppGroupListRoute(subsItemId = subsId, appId = addedAppId ?: appId),
                        replaced = true,
                    )
                } else {
                    mainVm.popPage()
                }
            },
        ) { paddingValues ->
            val textColors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            )
            Box(
                modifier = Modifier
                    .scaffoldPadding(paddingValues)
                    .fillMaxSize(),
            ) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                    val imeShowing by context.imeController.showAnimationRunningFlow.collectAsStateWithLifecycle()
                    val modifier = Modifier
                        .autoFocus(immediateFocus = true)
                        .fillMaxSize()
                        .run {
                            if (imeShowing) {
                                this
                            } else {
                                imePadding()
                            }
                        }
                    TextField(
                        value = text,
                        onValueChange = vm::setText,
                        modifier = modifier,
                        shape = RectangleShape,
                        colors = textColors,
                        visualTransformation = getJson5Transformation(LocalDarkTheme.current),
                        placeholder = {
                            Text(text = if (vm.isApp) UiStrings.app_rule_input_hint else UiStrings.global_rule_input_hint)
                        },
                    )
                }
                if (text.isNotEmpty()) {
                    Text(
                        text = text.length.toString(),
                        modifier = Modifier
                            .padding(8.dp)
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

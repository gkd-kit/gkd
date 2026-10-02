package li.gkd.app.ui.page


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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_rule_input_hint
import li.gkd.app.resources.global_rule_input_hint
import li.gkd.app.resources.rule_add
import li.gkd.app.resources.rule_edit
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.style.getJson5Transformation
import li.gkd.app.ui.style.scaffoldPadding
import org.jetbrains.compose.resources.stringResource

@Composable
fun UpsertRuleGroupScreen(
    isEdit: Boolean,
    isApp: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    hasChanges: suspend () -> Boolean,
    onSave: suspend () -> Unit,
    frame: EditorFrame,
    inputInsets: Modifier = Modifier,
) {
    frame(
        EditorSession(
            title = stringResource(if (isEdit) Res.string.rule_edit else Res.string.rule_add),
            hasChanges = hasChanges, saveEnabled = text.isNotBlank(), onSave = onSave,
        )
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
                val modifier = Modifier
                    .autoFocus(immediateFocus = true)
                    .fillMaxSize()
                    .then(inputInsets)
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = modifier,
                    shape = RectangleShape,
                    colors = textColors,
                    visualTransformation = getJson5Transformation(LocalDarkTheme.current),
                    placeholder = {
                        Text(
                            text = if (isApp) stringResource(Res.string.app_rule_input_hint) else stringResource(
                                Res.string.global_rule_input_hint
                            )
                        )
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

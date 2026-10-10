package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleDisplayIconButton(
    byCategory: Boolean,
    onClick: () -> Unit,
) {
    GkIconButton(
        imageVector = if (byCategory) GkIcons.Category else GkIcons.ViewStream,
        contentDescription = stringResource(
            if (byCategory) Res.string.rule_display_switch_to_flat
            else Res.string.rule_display_switch_to_category,
        ),
        onClick = onClick,
        animateMorph = true,
    )
}

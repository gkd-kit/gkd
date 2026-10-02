package li.gkd.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_collapse
import li.gkd.app.resources.action_expand
import li.gkd.app.resources.collapsed
import li.gkd.app.resources.expanded
import org.jetbrains.compose.resources.stringResource

private const val ExpansionDurationMillis = 220

@Composable
fun GkExpandableSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    expandLabel: String = stringResource(Res.string.action_expand),
    collapseLabel: String = stringResource(Res.string.action_collapse),
    header: @Composable (indicator: @Composable () -> Unit) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val description =
        if (expanded) stringResource(Res.string.expanded) else stringResource(Res.string.collapsed)
    val transition = updateTransition(expanded, label = "sectionExpansion")
    val rotation by transition.animateFloat(
        transitionSpec = { tween(ExpansionDurationMillis, easing = FastOutSlowInEasing) },
        label = "expansionIndicatorRotation",
    ) { if (it) 180f else 0f }

    Column(modifier) {
        Box(
            Modifier.fillMaxWidth()
                .clickable(
                    onClickLabel = if (expanded) collapseLabel else expandLabel,
                    onClick = onToggle
                )
                .semantics { stateDescription = description }) {
            header {
                GkIcon(
                    imageVector = GkIcons.ExpandMore,
                    modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                )
            }
        }
        // The same transition drives both animations and retains content until collapse finishes.
        transition.AnimatedVisibility(
            visible = { it },
            enter = expandVertically(
                animationSpec = tween(ExpansionDurationMillis, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
            ),
            exit = shrinkVertically(
                animationSpec = tween(ExpansionDurationMillis, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            Column(Modifier.fillMaxWidth().semantics {
                if (!expanded) hideFromAccessibility()
            }, content = content)
        }
    }
}

package li.gkd.app.ui.component

import li.gkd.app.text.UiStrings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun GkRuleListItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedMode: Boolean = false,
    selected: Boolean = false,
    highlighted: Boolean = false,
    selectable: Boolean = true,
    selectionEnabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onSelect: () -> Unit = {},
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable (Modifier) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected || highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(durationMillis = 300),
        label = "Rule card background",
    )
    Card(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 3.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                .then(
                    if (!selectedMode || selectable) {
                        Modifier.combinedClickable(
                            enabled = !selectedMode || selectionEnabled,
                            onClick = if (selectedMode) onSelect else onClick,
                            onLongClick = if (selectable && selectionEnabled) onLongClick else null,
                            onClickLabel = if (selectedMode) UiStrings.selection_toggle else UiStrings.details_view,
                        )
                    } else {
                        Modifier
                    }
                ).semantics {
                    if (selectedMode && selectable) {
                        role = Role.Checkbox
                        this.selected = selected
                    }
                }.heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                leading?.invoke()
                Column(Modifier.weight(1f), content = content)
            }
            // Include the switch's surrounding space in its hit target, up to the card edges.
            // 52 dp switch + 8 dp on either side matches the content inset.
            Box(Modifier.width(68.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (selectedMode) {
                    if (selectable) Checkbox(selected, onCheckedChange = null, enabled = selectionEnabled)
                } else {
                    trailing(Modifier.fillMaxSize())
                }
            }
        }
    }
}

package li.gkd.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleCategoryTabs(
    categories: List<String?>,
    selectedIndex: Int,
    visible: Boolean,
    firstTabTitle: String?,
    onSelect: (Int) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(300), expandFrom = Alignment.Top) + fadeIn(tween(300)),
        exit = shrinkVertically(tween(300), shrinkTowards = Alignment.Top) + fadeOut(tween(300)),
    ) {
        PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 8.dp) {
            if (firstTabTitle != null) Tab(
                selected = selectedIndex == 0,
                onClick = { onSelect(0) },
                text = { Text(firstTabTitle, maxLines = 1) },
            )
            categories.forEachIndexed { index, name ->
                val tabIndex = index + if (firstTabTitle != null) 1 else 0
                Tab(
                    selected = selectedIndex == tabIndex,
                    onClick = { onSelect(tabIndex) },
                    text = { Text(name ?: stringResource(Res.string.rule_category_uncategorized), maxLines = 1) },
                )
            }
        }
    }
}

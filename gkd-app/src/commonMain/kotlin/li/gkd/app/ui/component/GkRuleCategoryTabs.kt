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
import li.gkd.app.resources.Res
import li.gkd.app.resources.rule_category_all
import li.gkd.app.resources.rule_category_uncategorized
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleCategoryTabs(
    categories: List<String?>,
    selectedIndex: Int,
    visible: Boolean,
    firstTabTitle: String = stringResource(Res.string.rule_category_all),
    onSelect: (Int) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(300), expandFrom = Alignment.Top) + fadeIn(tween(300)),
        exit = shrinkVertically(tween(300), shrinkTowards = Alignment.Top) + fadeOut(tween(300)),
    ) {
        PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 8.dp) {
            Tab(
                selected = selectedIndex == 0,
                onClick = { onSelect(0) },
                text = { Text(firstTabTitle, maxLines = 1) },
            )
            categories.forEachIndexed { index, name ->
                Tab(
                    selected = selectedIndex == index + 1,
                    onClick = { onSelect(index + 1) },
                    text = { Text(name ?: stringResource(Res.string.rule_category_uncategorized), maxLines = 1) },
                )
            }
        }
    }
}

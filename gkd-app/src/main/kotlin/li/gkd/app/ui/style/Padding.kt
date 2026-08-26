package li.songe.gkd.ui.style

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

val itemHorizontalPadding = 16.dp
val itemVerticalPadding = 12.dp
val EmptyHeight = 80.dp
val cardHorizontalPadding = 12.dp

fun Modifier.itemPadding() = this.padding(itemHorizontalPadding, itemVerticalPadding)

fun Modifier.titleItemPadding(showTop: Boolean = true) = this.padding(
    itemHorizontalPadding,
    if (showTop) itemVerticalPadding + itemVerticalPadding / 2 else 0.dp,
    itemHorizontalPadding,
    itemVerticalPadding - itemVerticalPadding / 2
)

fun Modifier.appItemPadding() = this.padding(itemHorizontalPadding, itemVerticalPadding)

fun Modifier.scaffoldPadding(values: PaddingValues): Modifier {
    return padding(
        top = values.calculateTopPadding(),
        // 被 LazyXXX 使用时, 移除 bottom padding, 否则 底部导航栏 无法实现透明背景
    )
}

@Composable
fun Modifier.iconTextSize(
    textStyle: TextStyle = LocalTextStyle.current,
    square: Boolean = true,
): Modifier {
    val density = LocalDensity.current
    val lineHeightDp = density.run { textStyle.lineHeight.toDp() }
    val fontSizeDp = density.run { textStyle.fontSize.toDp() }
    return if (square) {
        padding((lineHeightDp - fontSizeDp) / 2).size(fontSizeDp)
    } else {
        size(height = lineHeightDp, width = fontSizeDp)
    }
}

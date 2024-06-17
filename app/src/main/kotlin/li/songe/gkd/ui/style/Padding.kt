package li.songe.gkd.ui.style

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val itemHorizontalPadding = 16.dp
val itemVerticalPadding = 12.dp

fun Modifier.itemPadding() = this then padding(itemHorizontalPadding, itemVerticalPadding)

fun Modifier.titleItemPadding() =
    this then padding(
        itemHorizontalPadding,
        itemVerticalPadding + itemVerticalPadding / 2,
        itemHorizontalPadding,
        itemVerticalPadding - itemVerticalPadding / 2
    )

fun Modifier.appItemPadding() = this then padding(10.dp, 10.dp)

fun Modifier.menuPadding() =
    this then padding(MenuDefaults.DropdownMenuItemContentPadding).padding(vertical = 8.dp)

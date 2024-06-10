package li.songe.gkd.ui.style

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun Modifier.itemPadding() = this then padding(16.dp, 12.dp)

fun Modifier.appItemPadding() = this then padding(10.dp, 8.dp)

fun Modifier.menuPadding() =
    this then padding(MenuDefaults.DropdownMenuItemContentPadding).padding(vertical = 8.dp)

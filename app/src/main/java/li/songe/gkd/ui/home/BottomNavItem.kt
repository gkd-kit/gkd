package li.songe.gkd.ui.home

import androidx.annotation.DrawableRes
import li.songe.gkd.R

data class BottomNavItem(
    val label: String,
    @DrawableRes
    val icon: Int,
    val route: String,
)

val BottomNavItems = listOf(
    BottomNavItem(
        label = "订阅",
        icon = R.drawable.ic_link,
        route = "subscription"
    ),
    BottomNavItem(
        label = "设置",
        icon = R.drawable.ic_cog,
        route = "settings"
    ),
)
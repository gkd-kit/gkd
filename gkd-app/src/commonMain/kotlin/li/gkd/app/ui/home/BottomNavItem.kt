package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

sealed class BottomNavItem(
    val key: Int,
    val labelResource: StringResource,
    val icon: ImageVector,
) {
    val label: String @Composable get() = stringResource(labelResource)

    object Dashboard : BottomNavItem(
        key = 0,
        labelResource = Res.string.home_title,
        icon = GkIcons.Home,
    )

    object SubsManage : BottomNavItem(
        key = 1,
        labelResource = Res.string.subscription_title,
        icon = GkIcons.StackedDocuments,
    )

    object AppList : BottomNavItem(
        key = 2,
        labelResource = Res.string.apps_title,
        icon = GkIcons.Android,
    )

    object Settings : BottomNavItem(
        key = 3,
        labelResource = Res.string.settings_title,
        icon = GkIcons.Settings,
    )

    companion object {
        val allSubObjects by lazy { arrayOf(Dashboard, SubsManage, AppList, Settings) }
    }
}

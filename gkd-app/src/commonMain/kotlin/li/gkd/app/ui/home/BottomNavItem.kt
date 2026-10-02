package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import li.gkd.app.resources.Res
import li.gkd.app.resources.apps_title
import li.gkd.app.resources.home_title
import li.gkd.app.resources.settings_title
import li.gkd.app.resources.subscription_title
import li.gkd.app.ui.component.GkIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

sealed class BottomNavItem(
    val key: Int,
    val labelKey: StringResource,
    val icon: ImageVector,
) {
    val label: String @Composable get() = stringResource(labelKey)

    object Dashboard : BottomNavItem(
        key = 0,
        labelKey = Res.string.home_title,
        icon = GkIcons.Home,
    )

    object SubsManage : BottomNavItem(
        key = 1,
        labelKey = Res.string.subscription_title,
        icon = GkIcons.StackedDocuments,
    )

    object AppList : BottomNavItem(
        key = 2,
        labelKey = Res.string.apps_title,
        icon = GkIcons.Android,
    )

    object Settings : BottomNavItem(
        key = 3,
        labelKey = Res.string.settings_title,
        icon = GkIcons.Settings,
    )

    companion object {
        val allSubObjects by lazy { arrayOf(Dashboard, SubsManage, AppList, Settings) }
    }
}


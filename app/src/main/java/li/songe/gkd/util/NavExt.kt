package li.songe.gkd.util

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.ramcosta.composedestinations.spec.Direction


val LocalNavController =
    compositionLocalOf<NavHostController> { error("not found DestinationsNavigator") }

private var lastNavigateTime = -1L
fun NavController.navigate(
    direction: Direction,
    navOptionsBuilder: NavOptionsBuilder.() -> Unit = {},
) {
    val t = System.currentTimeMillis()
    if (t - lastNavigateTime < 1000) {
        return
    }
    lastNavigateTime = t
    navigate(direction.route, navOptionsBuilder)
}
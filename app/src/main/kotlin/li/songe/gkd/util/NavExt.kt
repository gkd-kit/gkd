package li.songe.gkd.util

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.ramcosta.composedestinations.spec.Direction


val LocalNavController =
    compositionLocalOf<NavHostController> { error("not found DestinationsNavigator") }

private val navThrottle = useThrottle()
fun NavController.navigate(
    direction: Direction,
    navOptionsBuilder: NavOptionsBuilder.() -> Unit = {},
) {
    navThrottle {
        navigate(direction.route, navOptionsBuilder)
    }
}
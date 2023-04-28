package li.songe.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope

val LocalRoute = compositionLocalOf<Route> { error("not default value for Route") }
val LocalRouter = compositionLocalOf<Router> { error("not default value for Router") }
internal val LocalRouteShow = compositionLocalOf { true }

const val pageTransitionDurationMillis = 300

@Composable
fun usePageShow(block: suspend CoroutineScope.() -> Unit) {
    val show = LocalRouteShow.current
    LaunchedEffect(show) {
        if (show) {
            block()
        }
    }
}

@Composable
fun usePageHide(block: suspend CoroutineScope.() -> Unit) {
    val show = LocalRouteShow.current
    LaunchedEffect(show) {
        if (!show) {
            block()
        }
    }
}
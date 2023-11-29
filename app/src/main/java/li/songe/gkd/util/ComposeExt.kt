package li.songe.gkd.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.RequestPermissionLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext


val LocalLauncher =
    compositionLocalOf<StartActivityLauncher> { error("not found StartActivityLauncher") }

val LocalPickContentLauncher =
    compositionLocalOf<PickContentLauncher> { error("not found LocalPickContentLauncher") }

val LocalRequestPermissionLauncher =
    compositionLocalOf<RequestPermissionLauncher> { error("not found RequestPermissionLauncher") }

@Composable
fun <T> usePollState(
    context: CoroutineContext = Dispatchers.Default,
    interval: Long = 1000L,
    getter: () -> T,
): MutableState<T> {
    val mutableState = remember { mutableStateOf(getter()) }
    LaunchedEffect(Unit) {
        withContext(context) {
            while (isActive) {
                delay(interval)
                mutableState.value = getter()
            }
        }
    }
    return mutableState
}




package li.songe.gkd.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.blankj.utilcode.util.ToastUtils
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.RequestPermissionLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext


val LocalLauncher =
    compositionLocalOf<StartActivityLauncher> { error("not found StartActivityLauncher") }

val LocalPickContentLauncher =
    compositionLocalOf<PickContentLauncher> { error("not found LocalPickContentLauncher") }

val LocalRequestPermissionLauncher =
    compositionLocalOf<RequestPermissionLauncher> { error("not found RequestPermissionLauncher") }

@Composable
fun <T> usePollState(interval: Long = 1000L, getter: () -> T): MutableState<T> {
    val mutableState = remember { mutableStateOf(getter()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(interval)
            mutableState.value = getter()
        }
    }
    return mutableState
}

@Composable
fun LaunchedEffectTry(
    key1: Any? = null,
    block: suspend CoroutineScope.() -> Unit,
) {
    LaunchedEffect(key1) {
        try {
            withContext(IO) {
                block()
            }
        } catch (e: CancellationException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
            ToastUtils.showShort(e.message ?: "")
        }
    }
}




package li.songe.gkd.ui.component

import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.util.map
import li.songe.gkd.util.subsIdToRawFlow

@Composable
fun useSubs(subsId: Long?): RawSubscription? {
    val scope = rememberCoroutineScope()
    return remember(subsId) { subsIdToRawFlow.map(scope) { it[subsId] } }.collectAsState().value
}

@Composable
fun useSubsGroup(
    subs: RawSubscription?,
    groupKey: Int?,
    appId: String?,
): RawSubscription.RawGroupProps? {
    return remember(subs, groupKey, appId) {
        if (subs != null && groupKey != null) {
            if (appId != null) {
                subs.apps.find { it.id == appId }?.groups?.find { it.key == groupKey }
            } else {
                subs.globalGroups.find { it.key == groupKey }
            }
        } else {
            null
        }
    }
}

@Composable
private fun useAutoFocus(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(null) {
        delay(DefaultDurationMillis.toLong())
        focusRequester.requestFocus()
    }
    return focusRequester
}

@Composable
fun Modifier.autoFocus() = focusRequester(useAutoFocus())

@Composable
fun useListScrollState(k1: Any?, k2: Any? = null): Pair<TopAppBarScrollBehavior, LazyListState> {
    // key 函数的依赖变化时, compose 将重置 key 函数那行代码之后所有代码的状态, 因此需要需要将 key 作用域限定在 Composable fun 内
    val scrollBehavior = key(k1, k2) { TopAppBarDefaults.enterAlwaysScrollBehavior() }
    val listState = key(k1, k2) { rememberLazyListState() }
    return scrollBehavior to listState
}

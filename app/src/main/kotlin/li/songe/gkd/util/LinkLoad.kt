package li.songe.gkd.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

// 避免打开页面时短时间内数据未加载完成导致短暂显示的空数据提示
class LinkLoad(scope: CoroutineScope) {
    private val firstLoadCountFlow = MutableStateFlow(0)
    val firstLoadingFlow by lazy { firstLoadCountFlow.map(scope) { it > 0 } }
    fun <T> invoke(targetFlow: Flow<T>): Flow<T> {
        firstLoadCountFlow.update { it + 1 }
        var used = false
        return targetFlow.onEach {
            if (!used) {
                firstLoadCountFlow.update {
                    if (!used) {
                        used = true
                        it - 1
                    } else {
                        it
                    }
                }
            }
        }
    }
}
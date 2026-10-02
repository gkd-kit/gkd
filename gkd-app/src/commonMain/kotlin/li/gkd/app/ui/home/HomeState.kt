package li.gkd.app.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.TimeSource

data class PageScrollResetRequest(val id: Long, val navItem: BottomNavItem)

/** Owned by the HomeRoute ViewModel; page scroll positions remain in Compose. */
class HomeState(
    private val clock: () -> Long = monotonicClock(),
) {
    val selectedTab: StateFlow<BottomNavItem>
        field = MutableStateFlow<BottomNavItem>(BottomNavItem.Dashboard)
    val scrollResetRequest: StateFlow<PageScrollResetRequest?>
        field = MutableStateFlow<PageScrollResetRequest?>(null)
    private var nextRequestId = 0L
    private var previousClick: Pair<BottomNavItem, Long>? = null

    fun selectTab(tab: BottomNavItem) {
        if (selectedTab.value != tab) scrollResetRequest.value = null
        selectedTab.value = tab
        previousClick = null
    }

    fun clickTab(tab: BottomNavItem) {
        val now = clock()
        val previous = previousClick
        if (selectedTab.value != tab) scrollResetRequest.value = null
        if (previous?.first == tab && selectedTab.value == tab && now - previous.second in 0L until 500L) {
            scrollResetRequest.value = PageScrollResetRequest(++nextRequestId, tab)
        }
        selectedTab.value = tab
        previousClick = tab to now
    }

    fun consumeScrollReset(request: PageScrollResetRequest) {
        scrollResetRequest.compareAndSet(request, null)
    }
}

private fun monotonicClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}

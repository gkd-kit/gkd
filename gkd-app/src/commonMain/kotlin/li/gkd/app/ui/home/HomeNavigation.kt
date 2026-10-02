package li.gkd.app.ui.home

/** Host commands target the active HomeRoute's state; no host-owned tab copy. */
class HomeNavigation {
    private var boundState: HomeState? = null
    private var pendingTab: BottomNavItem? = null

    val selectedTab: BottomNavItem
        get() = boundState?.selectedTab?.value ?: pendingTab ?: BottomNavItem.Dashboard

    fun selectTab(tab: BottomNavItem) {
        val state = boundState
        if (state == null) pendingTab = tab else state.selectTab(tab)
    }

    fun bind(state: HomeState) {
        boundState = state
        pendingTab?.let(state::selectTab)
        pendingTab = null
    }

    fun unbind(state: HomeState) {
        if (boundState === state) boundState = null
    }
}

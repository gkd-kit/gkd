package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun GkHomeHost(state: HomeState, page: @Composable (BottomNavItem) -> ScaffoldExt) {
    val selected by state.selectedTab.collectAsStateWithLifecycle()
    val holder = rememberSaveableStateHolder()
    holder.SaveableStateProvider(selected.key) {
        GkHomeScaffold(page(selected), selected, state::clickTab)
    }
}

@Composable
fun ResetPageScrollOnRequest(
    state: HomeState,
    tab: BottomNavItem,
    reset: suspend () -> Unit
) {
    val request by state.scrollResetRequest.collectAsStateWithLifecycle()
    val currentReset by rememberUpdatedState(reset)
    LaunchedEffect(request) {
        request?.takeIf { it.navItem == tab }?.let {
            currentReset()
            state.consumeScrollReset(it)
        }
    }
}

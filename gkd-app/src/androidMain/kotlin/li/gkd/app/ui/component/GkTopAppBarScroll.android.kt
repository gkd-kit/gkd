package li.gkd.app.ui.component

import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

@Composable
actual fun Modifier.gkTopAppBarScroll(behavior: TopAppBarScrollBehavior): Modifier =
    nestedScroll(behavior.nestedScrollConnection)

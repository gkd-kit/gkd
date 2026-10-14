package li.gkd.app.ui.component

import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun Modifier.gkTopAppBarScroll(behavior: TopAppBarScrollBehavior): Modifier

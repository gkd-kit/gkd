package li.gkd.app.ui

import androidx.compose.runtime.Composable
import li.gkd.app.ui.navigation.WebViewRoute

@Composable
expect fun WebViewPage(
    route: WebViewRoute,
)

package li.gkd.app.ui

import androidx.compose.runtime.Composable
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.platform.UiHost

@Composable
expect fun WebViewPage(
    route: WebViewRoute,
    host: UiHost,
)

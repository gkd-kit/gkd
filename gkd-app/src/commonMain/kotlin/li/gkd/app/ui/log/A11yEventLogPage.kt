package li.gkd.app.ui.log

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.home.appLabel
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.page.A11yEventLogScreen

@Composable
fun A11yEventLogPage(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    copyText: (String) -> Unit,
    appIcon: @Composable (String, Dp) -> Unit,
) {
    val vm = viewModel { A11yEventLogViewModel() }
    A11yEventLogScreen(
        vm.pagingDataFlow.collectAsLazyPagingItems(), onBack,
        onOpenApp = { onNavigate(AppConfigRoute(it)) },
        onCopy = copyText, appLabel = { appLabel(it) }, appIcon = { appIcon(it, 24.dp) },
        appName = { id, modifier ->
            GkAppNameText(
                id,
                modifier = modifier,
                style = MaterialTheme.typography.titleSmall
            )
        },
    )
}

package li.gkd.app.ui.log

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.time.format
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.home.appLabel
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.page.ActivityLogScreen

@Composable
fun ActivityLogPage(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    showText: (String) -> Unit,
    appIcon: @Composable (String, Dp) -> Unit,
) {
    val vm = viewModel { ActivityLogViewModel() }
    ActivityLogScreen(
        vm.pagingDataFlow.collectAsLazyPagingItems(), onBack,
        onOpenApp = { onNavigate(AppConfigRoute(it)) },
        onDetails = { record ->
            showText(
                listOfNotNull(
                    AppInfoRepository.snapshot?.apps.orEmpty()[record.appId]?.name,
                    record.appId, record.activityId, record.ctime.format("yyyy-MM-dd HH:mm:ss.SSS"),
                ).joinToString("\n")
            )
        },
        appLabel = { appLabel(it) },
        appIcon = { appIcon(it, 24.dp) },
        appName = { id, modifier ->
            GkAppNameText(
                id,
                modifier = modifier,
                style = MaterialTheme.typography.titleSmall
            )
        },
    )
}

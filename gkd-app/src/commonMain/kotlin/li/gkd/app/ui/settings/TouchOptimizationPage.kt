package li.gkd.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.a11y.GestureService
import li.gkd.app.a11y.observeGestureServiceWritePermission
import li.gkd.app.error.messageResource
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.*
import li.gkd.app.state.Loadable
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkLoadingState
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTooltipIconButtonBox
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.image.GkAppIcon
import li.gkd.app.ui.platform.LocalUiHost
import li.gkd.app.ui.platform.launchApp
import li.gkd.app.ui.platform.openGestureServiceSettings
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun TouchOptimizationPage() {
    val vm = viewModel { TouchOptimizationViewModel() }
    val host = LocalUiHost.current
    val scope = rememberCoroutineScope()
    val mainVm = MainViewModel.requireCurrent()
    val state by mainVm.gestureServices.collectAsStateWithLifecycle()
    val canClose by remember {
        observeGestureServiceWritePermission()
    }
        .collectAsStateWithLifecycle(initialValue = false)
    val services = state.value.orEmpty()
    val otherServices = services.filterNot { it.isGkd }
    val hasGkd = otherServices.size != services.size
    val openSystemSettings = scope.launchUiAction {
        if (host.openGestureServiceSettings() == PlatformResult.Unsupported) {
            ToastUtils.show(getString(Res.string.platform_action_unsupported))
        }
    }
    GkScaffold(
        topBar = {
            GkTopAppBar(
                navigationIcon = {
                    GkIconButton(imageVector = GkIcons.ArrowBack, onClick = mainVm.navigator::pop)
                },
                title = { Text(stringResource(Res.string.touch_optimization_title)) },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = services.isNotEmpty(),
                modifier = Modifier.semantics { if (services.isEmpty()) hideFromAccessibility() },
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                BottomAppBar {
                    FlowRow(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = itemHorizontalPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    Res.string.touch_optimization_remaining,
                                    services.size.toString(),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (otherServices.isNotEmpty()) {
                            if (canClose) {
                                Button(
                                    onClick = { vm.requestClose(otherServices, keepGkd = hasGkd) },
                                ) {
                                    Text(
                                        stringResource(
                                            if (hasGkd) Res.string.touch_optimization_close_others
                                            else Res.string.touch_optimization_close_all,
                                        ),
                                    )
                                }
                            } else {
                                Button(onClick = openSystemSettings) {
                                    Text(
                                        stringResource(
                                            Res.string.touch_optimization_system_settings,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(modifier = Modifier.padding(contentPadding)) {
            when (val current = state) {
                Loadable.Loading -> item { GkLoadingState() }
                is Loadable.Failure -> item { GkEmptyState(text = current.cause.messageResource()) }
                is Loadable.Ready -> {
                    if (services.isEmpty()) {
                        item(key = "complete") {
                            GkEmptyState(
                                text = stringResource(Res.string.touch_optimization_complete),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    } else {
                        item(key = "description") {
                            Surface(
                                modifier =
                                    Modifier.animateItem()
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = itemHorizontalPadding,
                                            vertical = 8.dp,
                                        ),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(
                                    text =
                                        stringResource(Res.string.touch_optimization_description),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        items(services, key = { it.id }) { service ->
                            GestureServiceCard(
                                service = service,
                                modifier = Modifier.animateItem(),
                                onClick =
                                    if (service.isGkd) vm::requestPrivilegeInfo
                                    else
                                        scope.launchUiAction {
                                            if (vm.confirmOpenApp(service)) {
                                                val message =
                                                    when (
                                                        val result = host.launchApp(service.appId)
                                                    ) {
                                                        is PlatformResult.Success ->
                                                            if (result.value) null
                                                            else
                                                                Res.string
                                                                    .touch_optimization_app_no_launcher
                                                        PlatformResult.Unsupported ->
                                                            Res.string.platform_action_unsupported
                                                    }
                                                if (message != null)
                                                    ToastUtils.show(getString(message))
                                            }
                                        },
                                onAction =
                                    when {
                                        service.isGkd -> vm::requestPrivilegeInfo
                                        canClose -> {
                                            { vm.requestClose(listOf(service)) }
                                        }
                                        else -> openSystemSettings
                                    },
                            )
                        }
                    }
                }
            }
            item { GkPageBottomSpace() }
        }
    }
}

@Composable
private fun GestureServiceCard(
    service: GestureService,
    modifier: Modifier,
    onClick: () -> Unit,
    onAction: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier.fillMaxWidth().padding(horizontal = itemHorizontalPadding, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GkAppIcon(appId = service.appId, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(service.appName, style = MaterialTheme.typography.bodyLarge)
                if (service.name != service.appName) {
                    Text(
                        text = service.name,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (service.isGkd) {
                GkIconButton(
                    imageVector = GkIcons.Info,
                    onClick = onAction,
                    contentDescription =
                        stringResource(Res.string.touch_optimization_privilege_title),
                )
            } else {
                val closeLabel =
                    stringResource(Res.string.touch_optimization_close_service, service.name)
                GkTooltipIconButtonBox(closeLabel) {
                    FilledTonalIconButton(onClick = onAction) {
                        GkIcon(imageVector = GkIcons.Stop, contentDescription = closeLabel)
                    }
                }
            }
        }
    }
}

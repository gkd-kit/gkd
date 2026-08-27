package li.gkd.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.META
import li.gkd.app.data.mtimeStr
import li.gkd.app.ui.ActionLogRoute
import li.gkd.app.ui.SubsAppListRoute
import li.gkd.app.ui.SubsCategoryRoute
import li.gkd.app.ui.SubsGlobalGroupListRoute
import li.gkd.app.ui.SubsSheetVm
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.db.LOCAL_SUBS_ID
import li.gkd.app.util.SubscriptionResult
import li.gkd.app.util.launchTry
import li.gkd.app.util.subsItemsFlow
import li.gkd.app.util.subsMapFlow
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

class SubsSheetState {
    private val subsIdFlow = MutableStateFlow<Long?>(null)

    fun show(subsId: Long) {
        subsIdFlow.value = subsId
    }

    private fun dismiss() {
        subsIdFlow.value = null
    }

    @Composable
    fun Render() {
        val requestedSubsId by subsIdFlow.collectAsStateWithLifecycle()
        var renderedSubsId by remember { mutableStateOf(requestedSubsId) }
        LaunchedEffect(requestedSubsId) {
            if (requestedSubsId != null) {
                renderedSubsId = requestedSubsId
            }
        }
        val currentRenderedSubsId = renderedSubsId
        if (currentRenderedSubsId != null) {
            RenderSheet(
                renderedSubsId = currentRenderedSubsId,
                requestedSubsId = requestedSubsId,
                onRenderedSubsIdChange = { renderedSubsId = it },
            )
        }
    }

    @Composable
    private fun RenderSheet(
        renderedSubsId: Long,
        requestedSubsId: Long?,
        onRenderedSubsIdChange: (Long?) -> Unit,
    ) {
        val vm = viewModel<SubsSheetVm>()
        val scope = vm.scope
        val subsItems by subsItemsFlow.collectAsStateWithLifecycle()
        val subsItem = subsItems.find { it.id == renderedSubsId }
        LaunchedEffect(requestedSubsId, subsItem) {
            if (requestedSubsId == null && subsItem == null) {
                onRenderedSubsIdChange(null)
            }
        }
        if (subsItem != null) {
            val mainVm = LocalMainViewModel.current
            val subsIdToRaw by subsMapFlow.collectAsStateWithLifecycle()
            val scrollState = rememberScrollState()
            val sheetGesturesEnabled by remember {
                derivedStateOf { scrollState.value == 0 }
            }
            val sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            )
            val closeImmediately = {
                dismiss()
                onRenderedSubsIdChange(null)
            }
            LaunchedEffect(requestedSubsId, sheetState) {
                if (requestedSubsId == null) {
                    if (sheetState.isVisible) {
                        sheetState.hide()
                    }
                    if (!sheetState.isVisible) {
                        onRenderedSubsIdChange(null)
                    }
                }
            }
            AppModalBottomSheet(
                onDismissRequest = ::dismiss,
                sheetState = sheetState,
                sheetGesturesEnabled = sheetGesturesEnabled,
            ) {
                val subscription = subsIdToRaw[subsItem.id]
                val showName = subscription?.name ?: "id=${subsItem.id}"
                val childModifier = remember {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = itemHorizontalPadding, vertical = 8.dp)
                }
                Column(
                    modifier = Modifier
                        .verticalScroll(
                            state = scrollState,
                            enabled = sheetState.currentValue == SheetValue.Expanded
                        )
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = showName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = childModifier
                    )
                    if (subscription != null) {
                        Column(
                            modifier = childModifier.clearAndSetSemantics {
                                contentDescription =
                                    "作者：${subscription.author ?: "未知"}, 版本号：v${subscription.version}, 更新时间：${subsItem.mtimeStr}"
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "作者",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = "v${subscription.version}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(horizontal = 2.dp),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                if (!subsItem.isLocal) {
                                    Text(
                                        text = subscription.author ?: "未知",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                            if (subscription.author == null) {
                                                it.copy(alpha = 0.5f)
                                            } else {
                                                it
                                            }
                                        },
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                } else {
                                    Text(
                                        text = META.appName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                                Text(
                                    text = subsItem.mtimeStr,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (subscription.globalGroups.isNotEmpty() || subsItem.isLocal) {
                            Row(
                                modifier = Modifier
                                    .clickable(onClickLabel = "查看全局规则列表", onClick = throttle {
                                        closeImmediately()
                                        mainVm.navigatePage(SubsGlobalGroupListRoute(subsItem.id))
                                    })
                                    .then(childModifier),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "全局规则",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = if (subscription.globalGroups.isNotEmpty()) "共 ${subscription.globalGroups.size} 全局规则" else "暂无",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                            if (subscription.globalGroups.isEmpty()) {
                                                it.copy(alpha = 0.5f)
                                            } else {
                                                it
                                            }
                                        },
                                    )
                                }
                                PerfIcon(
                                    imageVector = PerfIcon.KeyboardArrowRight,
                                )
                            }
                        }
                        if (subscription.appGroups.isNotEmpty() || subsItem.isLocal) {
                            Row(
                                modifier = Modifier
                                    .clickable(onClickLabel = "查看应用规则列表", onClick = throttle {
                                        closeImmediately()
                                        mainVm.navigatePage(SubsAppListRoute(subsItem.id))
                                    })
                                    .then(childModifier),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "应用规则",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = if (subscription.appGroups.isNotEmpty()) "共 ${subscription.apps.size} 应用 ${subscription.appGroups.size} 规则" else "暂无",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                            if (subscription.appGroups.isEmpty()) {
                                                it.copy(alpha = 0.5f)
                                            } else {
                                                it
                                            }
                                        },
                                    )
                                }
                                PerfIcon(
                                    imageVector = PerfIcon.KeyboardArrowRight,
                                )
                            }

                        }
                        if (subscription.categories.isNotEmpty() || subsItem.isLocal) {
                            Row(
                                modifier = Modifier
                                    .clickable(onClickLabel = "查看规则类别列表", onClick = throttle {
                                        closeImmediately()
                                        mainVm.navigatePage(SubsCategoryRoute(subsItem.id))
                                    })
                                    .then(childModifier),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "规则类别",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = if (subscription.categories.isNotEmpty()) "共 ${subscription.categories.size} 类别" else "暂无",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                            if (subscription.categories.isEmpty()) {
                                                it.copy(alpha = 0.5f)
                                            } else {
                                                it
                                            }
                                        },
                                    )
                                }
                                PerfIcon(
                                    imageVector = PerfIcon.KeyboardArrowRight,
                                )
                            }
                        }
                        val updateUrl = subsItem.updateUrl
                        if (!subsItem.isLocal && updateUrl != null) {
                            Row(
                                modifier = Modifier
                                    .clickable(onClickLabel = "编辑订阅链接", onClick = throttle {
                                        if (vm.isBusy) {
                                            toast("正在刷新订阅,请稍后操作")
                                            return@throttle
                                        }
                                        scope.launchTry {
                                            val url = mainVm.subsLinkDialog.request(
                                                initialValue = updateUrl,
                                            )
                                                    ?: return@launchTry
                                            vm.addOrModifySubscription(url, subsItem).message?.let {
                                                toast(it)
                                            }
                                        }
                                    })
                                    .then(childModifier),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = "订阅链接",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = updateUrl,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        softWrap = false,
                                        overflow = TextOverflow.MiddleEllipsis,
                                        modifier = Modifier
                                            .clearAndSetSemantics {}
                                            .clickable(onClickLabel = "查看订阅链接", onClick = {
                                                mainVm.openUrl(updateUrl)
                                            })
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                PerfIcon(
                                    imageVector = PerfIcon.Edit,
                                )
                            }
                        }
                    } else {
                        val loading by vm.updating.collectAsStateWithLifecycle()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(modifier = Modifier.height(EmptyHeight))
                            if (loading) {
                                CircularProgressIndicator()
                            } else {
                                Text(
                                    text = "文件加载错误或不存在",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                TextButton(onClick = throttle {
                                    scope.launchTry {
                                        vm.refresh().message?.let { toast(it) }
                                    }
                                }) {
                                    Text(text = "重新加载")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = childModifier,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (!subsItem.isLocal && subscription?.supportUri != null) {
                            PerfIconButton(
                                imageVector = PerfIcon.HelpOutline,
                                onClick = throttle {
                                    mainVm.openUrl(subscription.supportUri)
                                },
                            )
                        }
                        PerfIconButton(imageVector = PerfIcon.History, onClick = throttle {
                            closeImmediately()
                            mainVm.navigatePage(ActionLogRoute(subsId = subsItem.id))
                        })
                        if (subsItem.id != LOCAL_SUBS_ID) {
                            PerfIconButton(
                                imageVector = PerfIcon.Delete,
                                onClick = throttle {
                                    scope.launchTry {
                                        if (!mainVm.dialogRequests.confirm(
                                            title = "删除订阅",
                                            text = "确定删除 ${subscription?.name ?: subsItem.id} ?",
                                            error = true,
                                        )) return@launchTry
                                        val result = vm.deleteSubscriptionItem(subsItem.id)
                                        result.message?.let {
                                            toast(it)
                                        }
                                        if (result is SubscriptionResult.Success) {
                                            closeImmediately()
                                        }
                                    }
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(EmptyHeight / 2))
                }
            }
        }
    }
}

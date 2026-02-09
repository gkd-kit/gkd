package li.songe.gkd.ui.component

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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import li.songe.gkd.META
import li.songe.gkd.ui.ActionLogRoute
import li.songe.gkd.ui.SubsAppListRoute
import li.songe.gkd.ui.SubsCategoryRoute
import li.songe.gkd.ui.SubsGlobalGroupListRoute
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.deleteSubscription
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.subsMapFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubsMutex

@Composable
fun SubsSheet(
    vm: ViewModel,
    sheetSubsIdFlow: MutableStateFlow<Long?>
) {
    val subsItems by subsItemsFlow.collectAsState()
    val (subsId, setSubsId) = remember { mutableStateOf(sheetSubsIdFlow.value) }
    val subsItem = subsItems.find { it.id == subsId }
    if (subsItem == null) {
        LaunchedEffect(null) {
            sheetSubsIdFlow.collect {
                setSubsId(it)
            }
        }
    } else {
        val mainVm = LocalMainViewModel.current
        val subsIdToRaw by subsMapFlow.collectAsState()
        var swipeEnabled by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { swipeEnabled }
        )
        LaunchedEffect(null) {
            sheetSubsIdFlow.collect {
                if (it == null && sheetState.isVisible) {
                    launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            setSubsId(null)
                        }
                    }
                } else {
                    setSubsId(it)
                }
            }
        }
        val scrollState = rememberScrollState()
        remember {
            derivedStateOf {
                scrollState.value == 0
            }
        }.let { a ->
            LaunchedEffect(a.value) {
                swipeEnabled = a.value
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                sheetSubsIdFlow.value = null
            },
            sheetState = sheetState
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
                                .clickable(onClickLabel = "查看全局规则组列表", onClick = throttle {
                                    setSubsId(null)
                                    sheetSubsIdFlow.value = null
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
                                    text = if (subscription.globalGroups.isNotEmpty()) "共 ${subscription.globalGroups.size} 全局规则组" else "暂无",
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
                                .clickable(onClickLabel = "查看应用规则组列表", onClick = throttle {
                                    setSubsId(null)
                                    sheetSubsIdFlow.value = null
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
                                    text = if (subscription.appGroups.isNotEmpty()) "共 ${subscription.apps.size} 应用 ${subscription.appGroups.size} 规则组" else "暂无",
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
                                    setSubsId(null)
                                    sheetSubsIdFlow.value = null
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
                    if (!subsItem.isLocal && subsItem.updateUrl != null) {
                        Row(
                            modifier = Modifier
                                .clickable(onClickLabel = "编辑订阅链接", onClick = throttle {
                                    if (updateSubsMutex.mutex.isLocked) {
                                        toast("正在刷新订阅,请稍后操作")
                                        return@throttle
                                    }
                                    mainVm.viewModelScope.launchTry {
                                        val url =
                                            mainVm.inputSubsLinkOption.getResult(initValue = subsItem.updateUrl)
                                                ?: return@launchTry
                                        mainVm.addOrModifySubs(url, subsItem)
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
                                    text = subsItem.updateUrl,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    softWrap = false,
                                    overflow = TextOverflow.MiddleEllipsis,
                                    modifier = Modifier
                                        .clearAndSetSemantics {}
                                        .clickable(onClickLabel = "查看订阅链接", onClick = {
                                            mainVm.textFlow.value = subsItem.updateUrl
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
                    val loading by updateSubsMutex.state.collectAsState()
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
                            TextButton(onClick = throttle { checkSubsUpdate(showToast = true) }) {
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
                                mainVm.textFlow.value = subscription.supportUri
                            },
                        )
                    }
                    PerfIconButton(imageVector = PerfIcon.History, onClick = throttle {
                        setSubsId(null)
                        sheetSubsIdFlow.value = null
                        mainVm.navigatePage(ActionLogRoute(subsId = subsItem.id))
                    })
                    if (subscription != null || !subsItem.isLocal) {
                        PerfIconButton(imageVector = PerfIcon.Share, onClick = throttle {
                            mainVm.showShareDataIdsFlow.value = setOf(subsItem.id)
                        })
                    }
                    if (subsItem.id != LOCAL_SUBS_ID) {
                        PerfIconButton(
                            imageVector = PerfIcon.Delete,
                            onClick = throttle(
                                vm.viewModelScope.launchAsFn {
                                    mainVm.dialogFlow.waitResult(
                                        title = "删除订阅",
                                        text = "确定删除 ${subscription?.name ?: subsItem.id} ?",
                                        error = true,
                                    )
                                    sheetSubsIdFlow.value = null
                                    setSubsId(null)
                                    deleteSubscription(subsItem.id)
                                }
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(EmptyHeight / 2))
            }
        }
    }
}

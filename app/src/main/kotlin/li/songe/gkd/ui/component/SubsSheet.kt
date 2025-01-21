package li.songe.gkd.ui.component

import androidx.activity.compose.LocalActivity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.ActionLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.CategoryPageDestination
import com.ramcosta.composedestinations.generated.destinations.CategoryPageDestination.invoke
import com.ramcosta.composedestinations.generated.destinations.GlobalGroupListPageDestination
import com.ramcosta.composedestinations.generated.destinations.SubsAppListPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import li.songe.gkd.META
import li.songe.gkd.MainActivity
import li.songe.gkd.data.deleteSubscription
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.copyText
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.openUri
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
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
        val context = LocalActivity.current as MainActivity
        val navController = LocalNavController.current
        val subsIdToRaw by subsIdToRawFlow.collectAsState()
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
        LaunchedEffect(scrollState.value) {
            swipeEnabled = scrollState.value == 0
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
                        modifier = childModifier
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
                                .clickable(onClick = throttle {
                                    setSubsId(null)
                                    sheetSubsIdFlow.value = null
                                    navController
                                        .toDestinationsNavigator()
                                        .navigate(GlobalGroupListPageDestination(subsItem.id))
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    }
                    if (subscription.appGroups.isNotEmpty() || subsItem.isLocal) {
                        Row(
                            modifier = Modifier
                                .clickable(onClick = throttle {
                                    setSubsId(null)
                                    sheetSubsIdFlow.value = null
                                    navController
                                        .toDestinationsNavigator()
                                        .navigate(SubsAppListPageDestination(subsItem.id))
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }

                    }
                    if (subscription.categories.isNotEmpty() || subsItem.isLocal) {
                        Row(
                            modifier = Modifier
                                .clickable(onClick = throttle {
                                    setSubsId(null)
                                    sheetSubsIdFlow.value = null
                                    navController
                                        .toDestinationsNavigator()
                                        .navigate(CategoryPageDestination(subsItem.id))
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    }
                    if (!subsItem.isLocal && subsItem.updateUrl != null) {
                        Row(
                            modifier = Modifier
                                .clickable(onClick = throttle {
                                    if (updateSubsMutex.mutex.isLocked) {
                                        toast("正在刷新订阅,请稍后操作")
                                        return@throttle
                                    }
                                    context.mainVm.viewModelScope.launchTry {
                                        val url =
                                            context.mainVm.inputSubsLinkOption.getResult(initValue = subsItem.updateUrl)
                                                ?: return@launchTry
                                        context.mainVm.addOrModifySubs(url, subsItem)
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
                                StartEllipsisText(
                                    text = subsItem.updateUrl,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clickable(onClick = throttle {
                                            copyText(subsItem.updateUrl)
                                        })
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
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
                    if (!subsItem.isLocal && subscription?.supportUri != null)  {
                        IconButton(onClick = throttle {
                            openUri(subscription.supportUri)
                        }) {
                            Icon(imageVector = Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                        }
                    }
                    IconButton(onClick = throttle {
                        setSubsId(null)
                        sheetSubsIdFlow.value = null
                        navController.toDestinationsNavigator()
                            .navigate(ActionLogPageDestination(subsId = subsItem.id))
                    }) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null)
                    }
                    if (subscription != null || !subsItem.isLocal) {
                        IconButton(onClick = throttle {
                            context.mainVm.showShareDataIdsFlow.value = setOf(subsItem.id)
                        }) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null)
                        }
                    }
                    if (subsItem.id != LOCAL_SUBS_ID) {
                        IconButton(onClick = throttle {
                            vm.viewModelScope.launchTry {
                                context.mainVm.dialogFlow.waitResult(
                                    title = "删除订阅",
                                    text = "确定删除 ${subscription?.name ?: subsItem.id} ?",
                                    error = true,
                                )
                                sheetSubsIdFlow.value = null
                                setSubsId(null)
                                deleteSubscription(subsItem.id)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(EmptyHeight / 2))
            }
        }
    }
}

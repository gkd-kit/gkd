package li.songe.gkd.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.TransferData
import li.songe.gkd.data.exportTransferData
import li.songe.gkd.ui.destinations.CategoryPageDestination
import li.songe.gkd.ui.destinations.GlobalRulePageDestination
import li.songe.gkd.ui.destinations.SubsPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.exportZipDir
import li.songe.gkd.util.formatTimeAgo
import li.songe.gkd.util.json
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.map
import li.songe.gkd.util.navigate
import li.songe.gkd.util.openUri
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.subsLoadErrorsFlow
import li.songe.gkd.util.subsRefreshErrorsFlow
import li.songe.gkd.util.subsRefreshingFlow
import li.songe.gkd.util.toast


@Composable
fun SubsItemCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
    subsItem: SubsItem,
    subscription: RawSubscription?,
    index: Int,
    vm: ViewModel,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val subsLoadError by remember(subsItem.id) {
        subsLoadErrorsFlow.map(scope) { it[subsItem.id] }
    }.collectAsState()
    val subsRefreshError by remember(subsItem.id) {
        subsRefreshErrorsFlow.map(scope) { it[subsItem.id] }
    }.collectAsState()
    val subsRefreshing by subsRefreshingFlow.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = {
            if (!subsRefreshingFlow.value) {
                expanded = true
            }
        },
        modifier = modifier.padding(16.dp, 2.dp),
        shape = MaterialTheme.shapes.small,
        interactionSource = interactionSource,
    ) {
        SubsMenuItem(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            subItem = subsItem,
            subscription = subscription,
            vm = vm
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (subscription != null) {
                    Text(
                        text = index.toString() + ". " + (subscription.name),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = subsItem.sourceText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatTimeAgo(subsItem.mtime),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (subsItem.id >= 0) {
                            Text(
                                text = "v" + (subscription.version.toString()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Text(
                        text = subscription.numText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (subscription.groupsSize == 0) {
                            LocalContentColor.current.copy(alpha = 0.5f)
                        } else {
                            LocalContentColor.current
                        }
                    )
                } else {
                    Text(
                        text = "${index}. id:${subsItem.id}",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val color = if (subsLoadError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    }
                    Text(
                        text = subsLoadError?.message
                            ?: if (subsRefreshing) "加载中..." else "文件不存在",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                if (subsRefreshError != null) {
                    Text(
                        text = "加载错误: ${subsRefreshError?.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
                checked = subsItem.enable,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun SubsMenuItem(
    expanded: Boolean,
    onExpandedChange: ((Boolean) -> Unit),
    subItem: SubsItem,
    subscription: RawSubscription?,
    vm: ViewModel
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) }
    ) {
        if (subscription != null) {
            if (subItem.id < 0 || subscription.apps.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(text = "应用规则")
                    },
                    onClick = {
                        onExpandedChange(false)
                        navController.navigate(SubsPageDestination(subItem.id))
                    }
                )
            }
            if (subItem.id < 0 || subscription.categories.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(text = "规则类别")
                    },
                    onClick = {
                        onExpandedChange(false)
                        navController.navigate(CategoryPageDestination(subItem.id))
                    }
                )
            }
            if (subItem.id < 0 || subscription.globalGroups.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(text = "全局规则")
                    },
                    onClick = {
                        onExpandedChange(false)
                        navController.navigate(GlobalRulePageDestination(subItem.id))
                    }
                )
            }
        }
        DropdownMenuItem(
            text = {
                Text(text = "导出数据")
            },
            onClick = {
                onExpandedChange(false)
                vm.viewModelScope.launchTry(Dispatchers.IO) {
                    val transferDataFile = exportZipDir.resolve("${TransferData.TYPE}.json")
                    transferDataFile.writeText(
                        json.encodeToString(
                            exportTransferData(
                                listOf(
                                    subItem.id
                                )
                            )
                        )
                    )
                    val file = exportZipDir.resolve("backup-${subItem.id}.zip")
                    if (file.exists()) {
                        file.delete()
                    }
                    ZipUtils.zipFiles(listOf(transferDataFile), file)
                    transferDataFile.delete()
                    context.shareFile(file, "分享数据文件")
                }
            }
        )
        subItem.updateUrl?.let {
            DropdownMenuItem(
                text = {
                    Text(text = "复制链接")
                },
                onClick = {
                    onExpandedChange(false)
                    ClipboardUtils.copyText(subItem.updateUrl)
                    toast("复制成功")
                }
            )
        }
        subscription?.supportUri?.let { supportUri ->
            DropdownMenuItem(
                text = {
                    Text(text = "问题反馈")
                },
                onClick = {
                    onExpandedChange(false)
                    context.openUri(supportUri)
                }
            )
        }
        if (subItem.id != -2L) {
            DropdownMenuItem(
                text = {
                    Text(text = "删除订阅", color = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    onExpandedChange(false)
                    vm.viewModelScope.launchTry {
                        val result = getDialogResult(
                            "删除订阅",
                            "是否删除订阅 ${subscription?.name ?: subItem.id} ?",
                        )
                        if (!result) return@launchTry
                        subItem.removeAssets()
                    }
                }
            )
        }
    }
}

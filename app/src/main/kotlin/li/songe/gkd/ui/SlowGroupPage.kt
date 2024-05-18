package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.ui.destinations.GlobalRulePageDestination
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.navigate
import li.songe.gkd.util.ruleSummaryFlow

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun SlowGroupPage() {
    val navController = LocalNavController.current
    val ruleSummary by ruleSummaryFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()
    var showInfoDlg by remember {
        mutableStateOf(false)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                title = { Text(text = if (ruleSummary.slowGroupCount > 0) "缓慢查询-${ruleSummary.slowGroupCount}" else "缓慢查询") },
                actions = {
                    IconButton(onClick = {
                        showInfoDlg = true
                    }) {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(
                ruleSummary.slowGlobalGroups,
                { (_, r) -> "${r.subsItem.id}-${r.group.key}" }
            ) { (group, rule) ->
                SlowGroupCard(
                    modifier = Modifier
                        .clickable {
                            navController.navigate(
                                GlobalRulePageDestination(
                                    rule.subsItem.id,
                                    group.key
                                )
                            )
                        }
                        .itemPadding(),
                    title = group.name,
                    desc = "${rule.rawSubs.name}/全局规则"
                )
            }
            items(
                ruleSummary.slowAppGroups,
                { (_, r) -> "${r.subsItem.id}-${r.appId}-${r.group.key}" }
            ) { (group, rule) ->
                SlowGroupCard(
                    modifier = Modifier
                        .clickable {
                            navController.navigate(
                                AppItemPageDestination(
                                    rule.subsItem.id,
                                    rule.app.id,
                                    group.key
                                )
                            )
                        }
                        .itemPadding(),
                    title = group.name,
                    desc = "${rule.rawSubs.name}/应用规则/${appInfoCache[rule.app.id]?.name ?: rule.app.name ?: rule.app.id}"
                )
            }
            item {
                Spacer(modifier = Modifier.height(40.dp))
                if (ruleSummary.slowGroupCount == 0) {
                    Text(
                        text = "暂无规则",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showInfoDlg) {
        AlertDialog(
            onDismissRequest = { showInfoDlg = false },
            title = { Text(text = "什么是缓慢查询") },
            text = { Text(text = "任意单个规则满足以下 3 个条件即判定为缓慢查询\n\n1. 选择器无法快速查询或使用 << 连接符\n2. preKeys 为空\n3. matchTime 为空或大于 10s") },
            confirmButton = {
                TextButton(onClick = { showInfoDlg = false }) {
                    Text(text = "确定")
                }
            },
        )
    }
}

@Composable
fun SlowGroupCard(title: String, desc: String, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null
        )
    }
}
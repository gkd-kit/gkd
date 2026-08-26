package li.gkd.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.appInfoMapFlow
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.ruleSummaryFlow
import li.gkd.app.util.throttle

@Serializable
data object SlowGroupRoute : NavKey

@Composable
fun SlowGroupPage() {
    val mainVm = LocalMainViewModel.current
    val ruleSummary by ruleSummaryFlow.collectAsStateWithLifecycle()
    val appInfoCache by appInfoMapFlow.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                        mainVm.popPage()
                    })
                },
                title = { Text(text = "缓慢查询") },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Info,
                        onClick = throttle(mainVm.scope.launchAsFn {
                            mainVm.dialogRequests.showMessage(
                                title = "缓慢查询",
                                text = arrayOf(
                                    "任意单个规则同时满足以下 3 个条件即判定为缓慢查询",
                                    "1. 选择器右侧无法快速查询且不是主动查询, 或内部使用<<且无法快速查询\n2. preKeys 为空\n3. matchTime 为空或大于 10s",
                                    "缓慢查询可能导致触发缓慢或更多耗电, 一些可能优化的建议操作\n1. 降低选择器获取新节点次数\n2. 降低或限制规则查询时间或次数"
                                ).joinToString("\n\n"),
                            )
                        }),
                    )
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding)
        ) {
            items(
                ruleSummary.slowGlobalGroups,
                { (_, r) -> r.subsItem.id to r.group.key }
            ) { (group, rule) ->
                SlowGroupCard(
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            mainVm.navigatePage(
                                SubsGlobalGroupListRoute(
                                    rule.subsItem.id,
                                    group.key
                                )
                            )
                        })
                        .itemPadding(),
                    title = group.name,
                    desc = "${rule.rawSubs.name}/全局规则"
                )
            }
            items(
                ruleSummary.slowAppGroups,
                { (_, r) -> Triple(r.subsItem.id, r.appId, r.group.key) }
            ) { (group, rule) ->
                SlowGroupCard(
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            mainVm.navigatePage(
                                SubsAppGroupListRoute(
                                    rule.subsItem.id,
                                    rule.app.id,
                                    group.key
                                )
                            )
                        })
                        .itemPadding(),
                    title = group.name,
                    desc = "${rule.rawSubs.name}/应用规则/${appInfoCache[rule.app.id]?.name ?: rule.app.name ?: rule.app.id}"
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (ruleSummary.slowGroupCount == 0) {
                    EmptyText(text = "暂无规则")
                }
            }
        }
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
        PerfIcon(
            imageVector = PerfIcon.KeyboardArrowRight,
        )
    }
}

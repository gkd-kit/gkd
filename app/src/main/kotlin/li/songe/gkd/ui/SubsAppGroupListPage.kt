package li.songe.gkd.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import li.songe.gkd.MainActivity
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.RuleGroupCard
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SubsAppGroupListPage(
    subsItemId: Long,
    appId: String,
    focusGroupKey: Int? = null, // 背景/边框高亮一下
) {
    val context = LocalActivity.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<SubsAppGroupListVm>()
    val subs = vm.subsRawFlow.collectAsState().value
    val subsConfigs by vm.subsConfigsFlow.collectAsState()
    val categoryConfigs by vm.categoryConfigsFlow.collectAsState()
    val appRaw by vm.subsAppFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val groupToCategoryMap = subs?.groupToCategoryMap ?: emptyMap()

    val editable = subsItemId < 0

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = {
            TowLineText(
                title = subs?.name ?: subsItemId.toString(),
                subtitle = appInfoCache[appId]?.name ?: appRaw.name ?: appId
            )
        }, actions = {
            AnimatedVisibility(
                visible = editable && subs != null && appRaw.groups.isNotEmpty(),
            ) {
                IconButton(onClick = throttle(vm.viewModelScope.launchAsFn {
                    subs ?: return@launchAsFn
                    context.mainVm.dialogFlow.waitResult(
                        title = "删除规则组",
                        text = "删除当前列表所有规则组?"
                    )
                    updateSubscription(subs.copy(
                        apps = subs.apps.filter { a -> a.id != appId }
                    ))
                    DbSet.subsConfigDao.deleteAppConfig(subsItemId, appId)
                    toast("删除成功")
                })) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        })
    }, floatingActionButton = {
        if (editable) {
            FloatingActionButton(onClick = throttle {
                context.mainVm.ruleGroupState.editOrAddGroupFlow.value = ShowGroupState(
                    subsId = subsItemId,
                    appId = appId,
                )
            }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "add",
                )
            }
        }
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding)
        ) {
            items(appRaw.groups, { it.key }) { group ->
                val category = groupToCategoryMap[group]
                val subsConfig = subsConfigs.find { it.groupKey == group.key }
                val categoryConfig = categoryConfigs.find {
                    it.categoryKey == category?.key
                }
                RuleGroupCard(
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        placementSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = IntOffset.VisibilityThreshold
                        ),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ),
                    subs = subs!!,
                    appId = appId,
                    group = group,
                    category = category,
                    subsConfig = subsConfig,
                    categoryConfig = categoryConfig,
                    highlighted = group.key == focusGroupKey,
                )
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (appRaw.groups.isEmpty()) {
                    EmptyText(text = "暂无规则")
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }
}

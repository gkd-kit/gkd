package li.songe.gkd.ui

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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.RuleGroupCard
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.throttle

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun GlobalGroupListPage(subsItemId: Long, focusGroupKey: Int? = null) {
    val mainVm = LocalMainViewModel.current
    val navController = LocalNavController.current
    val vm = viewModel<GlobalGroupListVm>()
    val subs = vm.subsRawFlow.collectAsState().value
    val subsConfigs by vm.subsConfigsFlow.collectAsState()

    val editable = subsItemId < 0 && subs != null
    val globalGroups = subs?.globalGroups ?: emptyList()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
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
                    subtitle = "全局规则"
                )
            })
        },
        floatingActionButton = {
            if (editable) {
                FloatingActionButton(onClick = throttle {
                    mainVm.ruleGroupState.editOrAddGroupFlow.value = ShowGroupState(
                        subsId = subsItemId,
                    )
                }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "add",
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(paddingValues)
        ) {
            items(globalGroups, { g -> g.key }) { group ->
                val subsConfig = subsConfigs.find { it.groupKey == group.key }
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
                    appId = null,
                    group = group,
                    highlighted = group.key == focusGroupKey,
                    subsConfig = subsConfig,
                    category = null,
                    categoryConfig = null,
                )
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (globalGroups.isEmpty()) {
                    EmptyText(text = "暂无规则")
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }
}

package li.songe.gkd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.MainActivity
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.StartEllipsisText
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun ActivityLogPage() {
    val context = LocalContext.current as MainActivity
    val mainVm = context.mainVm
    val vm = hiltViewModel<ActivityLogVm>()
    val navController = LocalNavController.current

    val logCount by vm.logCountFlow.collectAsState()
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = throttle {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            title = {
                Text(text = "界面记录")
            },
            actions = {
                if (logCount > 0) {
                    IconButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                        mainVm.dialogFlow.waitResult(
                            title = "删除记录",
                            text = "是否删除所有界面记录?",
                        )
                        DbSet.activityLogDao.deleteAll()
                    })) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                        )
                    }
                }
            })
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
        ) {
            items(
                count = list.itemCount,
                key = list.itemKey { it.id }
            ) { i ->
                val activityLog = list[i] ?: return@items
                if (i > 0) {
                    HorizontalDivider()
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Row {
                        Text(text = activityLog.date)
                        Spacer(modifier = Modifier.width(10.dp))
                        val appInfo = appInfoCache[activityLog.appId]
                        val appShowName = appInfo?.name ?: activityLog.appId
                        Text(
                            text = appShowName,
                            style = LocalTextStyle.current.let {
                                if (appInfo?.isSystem == true) {
                                    it.copy(textDecoration = TextDecoration.Underline)
                                } else {
                                    it
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    val showActivityId = activityLog.showActivityId
                    if (showActivityId != null) {
                        StartEllipsisText(text = showActivityId)
                    } else {
                        Text(text = "null", color = LocalContentColor.current.copy(alpha = 0.5f))
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (logCount == 0) {
                    Text(
                        text = "暂无记录",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
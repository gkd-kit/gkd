package li.songe.gkd.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AppRegistration
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.generated.destinations.ImagePreviewPageDestination
import com.ramcosta.composedestinations.generated.destinations.SubsAppGroupListPageDestination
import com.ramcosta.composedestinations.generated.destinations.SubsGlobalGroupListPageDestination
import com.ramcosta.composedestinations.utils.currentDestinationAsState
import kotlinx.coroutines.delay
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.ui.icon.ResetSettings
import li.songe.gkd.ui.share.LocalDarkTheme
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.LocalNavController
import li.songe.gkd.ui.style.getJson5AnnotatedString
import li.songe.gkd.util.copyText
import li.songe.gkd.util.throttle

@Composable
fun RuleGroupDialog(
    subs: RawSubscription,
    group: RawSubscription.RawGroupProps,
    appId: String?,
    onDismissRequest: () -> Unit,
    onClickEdit: (() -> Unit) = {},
    onClickEditExclude: () -> Unit,
    onClickResetSwitch: (() -> Unit)?,
    onClickDelete: () -> Unit = {}
) {
    val mainVm  = LocalMainViewModel.current
    val navController = LocalNavController.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "规则组详情") },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                val maxHeight = 300.dp
//                val showMaxLine = ceil(maxHeight.value / textStyle.lineHeight.value).toInt()
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = maxHeight)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        val textState = remember {
                            mutableStateOf(
                                group.cacheStr.run {
                                    // 优化: 大字符串第一次显示卡顿
                                    if (length > 1000) substring(0, 1000) else this
                                }
                            )
                        }
                        LaunchedEffect(group.cacheStr) {
                            delay(50)
                            if (group.cacheStr.length != textState.value.length) {
                                textState.value = group.cacheStr
                            }
                        }
                        val darkTheme = LocalDarkTheme.current
                        Text(
                            text = remember(textState.value, darkTheme) {
                                getJson5AnnotatedString(
                                    textState.value,
                                    darkTheme
                                )
                            },
                            modifier = Modifier.padding(4.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Icon(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clickable(onClick = throttle {
                            copyText(group.cacheStr)
                        })
                        .padding(4.dp)
                        .size(24.dp),
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                )
                Text(
                    text = group.cacheStr.length.toString(),
                    modifier = Modifier
                        .padding(end = 4.dp, bottom = 4.dp)
                        .align(Alignment.BottomEnd)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
        confirmButton = {
            Row {
                val currentDestination by navController.currentDestinationAsState()
                val (direction, destination) = remember(subs.id, appId, group.key) {
                    if (group is RawSubscription.RawGlobalGroup) {
                        SubsGlobalGroupListPageDestination(
                            subsItemId = subs.id,
                            focusGroupKey = group.key
                        ) to SubsGlobalGroupListPageDestination
                    } else {
                        SubsAppGroupListPageDestination(
                            subsItemId = subs.id,
                            appId = appId.toString(),
                            focusGroupKey = group.key
                        ) to SubsAppGroupListPageDestination
                    }
                }
                if (currentDestination?.baseRoute != destination.baseRoute) {
                    IconButton(onClick = throttle {
                        onDismissRequest()
                        navController.navigate(direction.route)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
                if (group.allExampleUrls.isNotEmpty()) {
                    IconButton(onClick = throttle {
                        onDismissRequest()
                        mainVm.navigatePage(
                            ImagePreviewPageDestination(
                                title = group.name,
                                uris = group.allExampleUrls.toTypedArray()
                            )
                        )
                    }) {
                        Icon(imageVector = Icons.Outlined.Image, contentDescription = null)
                    }
                }
                if (subs.isLocal) {
                    IconButton(onClick = throttle(onClickEdit)) {
                        Icon(imageVector = Icons.Outlined.Edit, contentDescription = null)
                    }
                }
                IconButton(onClick = throttle(onClickEditExclude)) {
                    Icon(
                        imageVector = Icons.Outlined.AppRegistration,
                        contentDescription = null
                    )
                }
                AnimatedVisibility(
                    visible = onClickResetSwitch != null,
                ) {
                    IconButton(onClick = throttle(onClickResetSwitch ?: {})) {
                        Icon(
                            imageVector = ResetSettings,
                            contentDescription = null
                        )
                    }
                }
                if (subs.isLocal) {
                    IconButton(onClick = throttle(onClickDelete)) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                        )
                    }
                }
            }
        },
    )
}

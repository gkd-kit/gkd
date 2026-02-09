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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.ui.ImagePreviewRoute
import li.songe.gkd.ui.SubsAppGroupListRoute
import li.songe.gkd.ui.SubsGlobalGroupListRoute
import li.songe.gkd.ui.icon.ResetSettings
import li.songe.gkd.ui.share.LocalDarkTheme
import li.songe.gkd.ui.share.LocalMainViewModel
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
    val mainVm = LocalMainViewModel.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "规则组详情") },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                val maxHeight = 300.dp
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = maxHeight)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .verticalScroll(rememberScrollState())
                        .clearAndSetSemantics {
                            contentDescription = "规则组内容"
                        }
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
                PerfIcon(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clickable(onClick = throttle {
                            copyText(group.cacheStr)
                        })
                        .padding(4.dp)
                        .size(24.dp),
                    imageVector = PerfIcon.ContentCopy,
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
                val currentRoute = mainVm.topRoute
                val targetRoute = remember(subs.id, appId, group.key) {
                    if (group is RawSubscription.RawGlobalGroup) {
                        SubsGlobalGroupListRoute(
                            subsItemId = subs.id,
                            focusGroupKey = group.key
                        )
                    } else {
                        SubsAppGroupListRoute(
                            subsItemId = subs.id,
                            appId = appId.toString(),
                            focusGroupKey = group.key
                        )
                    }
                }
                if (targetRoute::class != currentRoute::class) {
                    PerfIconButton(imageVector = PerfIcon.ArrowForward, onClick = throttle {
                        onDismissRequest()
                        mainVm.navigatePage(targetRoute)
                    })
                }
                if (group.allExampleUrls.isNotEmpty()) {
                    PerfIconButton(imageVector = PerfIcon.Image, onClick = throttle {
                        onDismissRequest()
                        mainVm.navigatePage(
                            ImagePreviewRoute(
                                title = group.name,
                                uris = group.allExampleUrls,
                            )
                        )
                    })
                }
                if (subs.isLocal) {
                    PerfIconButton(imageVector = PerfIcon.Edit, onClick = throttle(onClickEdit))
                }
                PerfIconButton(
                    imageVector = PerfIcon.Block,
                    onClickLabel = "编辑规则排除名单",
                    onClick = throttle(onClickEditExclude),
                )
                AnimatedVisibility(
                    visible = onClickResetSwitch != null,
                ) {
                    PerfIconButton(
                        imageVector = ResetSettings,
                        onClickLabel = "重置开关状态至默认值",
                        onClick = throttle(onClickResetSwitch ?: {}),
                    )
                }
                if (subs.isLocal) {
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle(onClickDelete),
                    )
                }
            }
        },
    )
}

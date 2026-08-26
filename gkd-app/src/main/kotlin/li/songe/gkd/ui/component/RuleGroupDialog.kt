package li.songe.gkd.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.ui.ImagePreviewItem
import li.songe.gkd.ui.ImagePreviewRoute
import li.songe.gkd.ui.SubsAppGroupListRoute
import li.songe.gkd.ui.SubsGlobalGroupListRoute
import li.songe.gkd.ui.icon.ResetSettings
import li.songe.gkd.ui.share.LocalDarkTheme
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.JSON5_LARGE_TEXT_THRESHOLD
import li.songe.gkd.ui.style.getJson5AnnotatedString
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
    val source = group.cacheStr
    val darkTheme = LocalDarkTheme.current
    val annotatedText = remember(source, darkTheme) {
        getJson5AnnotatedString(source, darkTheme)
    }
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
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "规则详情") },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val maxHeight = 300.dp
                val textModifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = maxHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                val contentPadding = PaddingValues(4.dp)
                if (source.length > JSON5_LARGE_TEXT_THRESHOLD) {
                    LazyCopyableText(
                        text = annotatedText,
                        modifier = textModifier,
                        contentPadding = contentPadding,
                        textStyle = MaterialTheme.typography.bodySmall,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        textContentDescription = "规则内容",
                    )
                } else {
                    CopyableText(
                        text = annotatedText,
                        textToCopy = source,
                        modifier = textModifier,
                        contentPadding = contentPadding,
                        textStyle = MaterialTheme.typography.bodySmall,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        textContentDescription = "规则内容",
                    )
                }
                Text(
                    text = source.length.toString(),
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
                                items = buildRuleGroupPreviewItems(group),
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

// 规则组示例图需要保留“图片属于哪个子规则”的上下文，预览页才能显示更具体的标题。
private fun buildRuleGroupPreviewItems(group: RawSubscription.RawGroupProps): List<ImagePreviewItem> {
    val uriTitlesMap = linkedMapOf<String, LinkedHashSet<String>>()

    fun addPreviewItem(uri: String, title: String?) {
        val titles = uriTitlesMap.getOrPut(uri) { linkedSetOf() }
        title?.takeIf { it.isNotBlank() }?.let(titles::add)
    }

    group.exampleUrls.orEmpty().forEach { uri ->
        addPreviewItem(
            uri = uri,
            title = group.name,
        )
    }
    group.rules.forEach { rule ->
        val ruleTitle = buildRulePreviewTitle(rule)
        rule.exampleUrls.orEmpty().forEach { uri ->
            addPreviewItem(
                uri = uri,
                title = ruleTitle,
            )
        }
    }

    return uriTitlesMap.map { (uri, titles) ->
        ImagePreviewItem(
            uri = uri,
            titles = titles.toList(),
        )
    }
}

private fun buildRulePreviewTitle(rule: RawSubscription.RawRuleProps): String? {
    return when {
        !rule.name.isNullOrBlank() -> rule.name
        rule.key != null -> "key=${rule.key}"
        !rule.preKeys.isNullOrEmpty() -> "preKeys=${(rule.preKeys as Iterable<Any?>).joinToString(",")}"
        else -> null
    }
}

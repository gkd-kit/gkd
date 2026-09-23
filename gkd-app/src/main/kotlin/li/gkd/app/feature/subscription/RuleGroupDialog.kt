package li.gkd.app.feature.subscription

import li.gkd.app.MainViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.text.UiStrings
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ExcludeData
import li.gkd.app.domain.rule.RuleControlState
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.ui.ImagePreviewItem
import li.gkd.app.ui.ImagePreviewRoute
import li.gkd.app.ui.icon.RuleList
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.style.JSON5_LARGE_TEXT_THRESHOLD
import li.gkd.app.ui.style.getJson5AnnotatedString
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.component.GkCopyableText
import li.gkd.app.ui.component.GkFullscreenDialog
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkLazyCopyableText
import li.gkd.app.ui.component.GkRuleExclusionsCard
import li.gkd.app.ui.component.GkRuleSettingsContent
import li.gkd.app.ui.component.GkRuleSettingsSheet
import li.gkd.app.ui.component.GkGroupNameText
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.removeRuleCategoryPrefix

@Composable
fun RuleGroupDialog(
    subs: RawSubscription,
    group: RawSubscription.RawGroupProps,
    appId: String?,
    excludeData: ExcludeData,
    excludeAppId: String?,
    onDismissRequest: () -> Unit,
    onClickEdit: () -> Unit = {},
    onClickEditExclude: () -> Unit,
    control: RuleControlState?,
    onSettingChange: (RuleSetting) -> Unit,
    onClickDelete: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val mainVm = MainViewModel.requireCurrent()
    // Keep the header width stable during dismissal after navigating to the list.
    val sourceRoute = remember { mainVm.topRoute }
    val category = if (group is RawSubscription.RawAppGroup) {
        subs.getCategory(group.name)?.takeIf { it.name.isNotBlank() }
    } else null
    val title = category?.let { group.name.removeRuleCategoryPrefix(it.name) }
        ?.takeIf { it.isNotEmpty() } ?: group.name
    val targetRoute = remember(subs.id, appId, group.groupType, group.key) {
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
    val inTargetList = when (val current = sourceRoute) {
        is SubsAppGroupListRoute -> group is RawSubscription.RawAppGroup &&
            current.subsItemId == subs.id && current.appId == appId
        is SubsGlobalGroupListRoute -> group is RawSubscription.RawGlobalGroup &&
            current.subsItemId == subs.id
        else -> false
    }
    var menu by remember { mutableStateOf(false) }
    var showSource by remember(group) { mutableStateOf(false) }
    GkRuleSettingsSheet(
        sheetState = sheetState,
        title = title,
        titleContent = {
            GkGroupNameText(
                text = title,
                isGlobal = group is RawSubscription.RawGlobalGroup,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        subtitle = group.desc?.takeIf { it.isNotBlank() },
        footerLeadingContent = if (category != null) {
            {
                AssistChip(
                    onClick = {
                        val route = SubsCategoryGroupRoute(subs.id, category.key)
                        onDismissRequest()
                        if (mainVm.topRoute != route) mainVm.navigatePage(route)
                    },
                    label = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    trailingIcon = {
                        GkIcon(GkIcons.KeyboardArrowRight, Modifier.size(16.dp),
                            contentDescription = null)
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    border = null,
                )
            }
        } else null,
        onDismissRequest = onDismissRequest,
        actions = {
            if (!inTargetList) {
                GkIconButton(
                    imageVector = RuleList,
                    contentDescription = UiStrings.rule_view_parent_list,
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        onDismissRequest()
                        mainVm.navigatePage(targetRoute)
                    },
                )
            }
            if (subs.isLocal) {
                GkIconButton(
                    imageVector = GkIcons.Edit,
                    contentDescription = UiStrings.rule_edit,
                    modifier = Modifier.size(48.dp),
                    onClick = onClickEdit,
                )
            }
            Box {
                GkIconButton(imageVector = GkIcons.MoreVert, modifier = Modifier.size(48.dp),
                    onClick = { menu = true })
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(UiStrings.rule_view_source) }, onClick = {
                        menu = false
                        showSource = true
                    })
                    if (group.allExampleUrls.isNotEmpty()) {
                        DropdownMenuItem(text = { Text(UiStrings.rule_view_examples) }, onClick = {
                            menu = false
                            onDismissRequest()
                            mainVm.navigatePage(ImagePreviewRoute(title = group.name, items = buildRuleGroupPreviewItems(group)))
                        })
                    }
                    DropdownMenuItem(text = { Text(UiStrings.subscription_settings) }, onClick = {
                        menu = false
                        onDismissRequest()
                        mainVm.subsSheet.show(subs.id)
                    })
                    if (subs.isLocal) {
                        DropdownMenuItem(text = { Text(UiStrings.rule_delete) },
                            onClick = { menu = false; onClickDelete() })
                    }
                }
            }
        },
    ) {
        if (control == null) {
            Text(UiStrings.config_loading)
        } else {
            GkRuleSettingsContent(control, onSettingChange,
                title = if (group is RawSubscription.RawGlobalGroup && appId != null) UiStrings.rule_enable_in_app else UiStrings.rule_enable,
                onViewControl = if (group is RawSubscription.RawAppGroup && appId != null) ({
                    mainVm.ruleControlDialog.show(li.gkd.app.domain.rule.RuleGroupTarget.App(subs.id, appId, group.key))
                }) else null)
            GkRuleExclusionsCard(excludeData, excludeAppId, onClick = onClickEditExclude)
        }
    }
    if (showSource) {
        RuleSourceDialog(group, onDismissRequest = { showSource = false })
    }
}

@Composable
private fun RuleSourceDialog(group: RawSubscription.RawGroupProps, onDismissRequest: () -> Unit) {
    // Syntax highlighting is only needed after the user opens the source viewer.
    val source = group.cacheStr
    val darkTheme = LocalDarkTheme.current
    val annotatedText = remember(source, darkTheme) { getJson5AnnotatedString(source, darkTheme) }
    GkFullscreenDialog(onDismissRequest = onDismissRequest) {
        Scaffold(topBar = {
            GkTopAppBar(
                title = { GkTwoLineText(title = UiStrings.rule_source, subtitle = group.name) },
                actions = {
                    GkIconButton(GkIcons.Close, onClick = onDismissRequest,
                        contentDescription = UiStrings.dialog_close)
                },
            )
        }) { contentPadding ->
            val textModifier = Modifier.scaffoldPadding(contentPadding).padding(16.dp)
                .fillMaxSize().clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            if (source.length > JSON5_LARGE_TEXT_THRESHOLD) {
                GkLazyCopyableText(text = annotatedText, modifier = textModifier,
                    contentPadding = PaddingValues(16.dp), textStyle = MaterialTheme.typography.bodySmall,
                    contentColor = MaterialTheme.colorScheme.onSurface, textContentDescription = UiStrings.rule_content)
            } else {
                GkCopyableText(text = annotatedText, textToCopy = source, modifier = textModifier,
                    contentPadding = PaddingValues(16.dp), textStyle = MaterialTheme.typography.bodySmall,
                    contentColor = MaterialTheme.colorScheme.onSurface, textContentDescription = UiStrings.rule_content)
            }
        }
    }
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
        rule.key != null -> UiStrings.rule_key_description(rule.key)
        !rule.preKeys.isNullOrEmpty() -> UiStrings.rule_pre_keys_description((rule.preKeys as Iterable<Any?>).joinToString(","))
        else -> null
    }
}

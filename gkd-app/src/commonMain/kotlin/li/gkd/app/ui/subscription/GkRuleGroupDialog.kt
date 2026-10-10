package li.gkd.app.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import li.gkd.app.ui.component.GkTriStateSwitch
import li.gkd.app.ui.icon.ResetSettings
import li.gkd.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.Alignment
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
import li.gkd.app.model.ExcludeData
import li.gkd.app.rule.RuleControlState
import li.gkd.app.rule.toRuleGroupTarget
import li.gkd.app.rule.RuleSetting
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.ui.component.GkCopyableText
import li.gkd.app.ui.component.GkGroupNameText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkLazyCopyableText
import li.gkd.app.ui.component.GkRuleExclusionsCard
import li.gkd.app.ui.component.GkRuleSettingsContent
import li.gkd.app.ui.component.GkRuleSettingsSheet
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.removeRuleCategoryPrefix
import li.gkd.app.ui.icon.RuleList
import li.gkd.app.ui.navigation.AppNavigator
import li.gkd.app.ui.navigation.ImagePreviewItem
import li.gkd.app.ui.navigation.ImagePreviewRoute
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsCategoryGroupRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.platform.GkFullscreenDialog
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.style.JSON5_LARGE_TEXT_THRESHOLD
import li.gkd.app.ui.style.getJson5AnnotatedString
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.copyText
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkRuleGroupDialog(
    navigator: AppNavigator,
    openSubscription: (Long) -> Unit,
    ruleControl: RuleControlDialogState,
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
    matchAnyApp: Boolean?,
    onMatchAnyAppChange: (Boolean?) -> Unit,
    onClickDelete: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    // Keep the header width stable during dismissal after navigating to the list.
    val sourceRoute = remember { navigator.topRoute }
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
                        navigator.navigate(route)
                    },
                    label = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    trailingIcon = {
                        GkIcon(
                            GkIcons.KeyboardArrowRight, Modifier.size(16.dp),
                            contentDescription = null
                        )
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
                    contentDescription = stringResource(Res.string.rule_view_parent_list),
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        onDismissRequest()
                        navigator.navigate(targetRoute)
                    },
                )
            }
            if (subs.isLocal) {
                GkIconButton(
                    imageVector = GkIcons.Edit,
                    contentDescription = stringResource(Res.string.rule_edit),
                    modifier = Modifier.size(48.dp),
                    onClick = onClickEdit,
                )
            }
            Box {
                GkIconButton(
                    imageVector = GkIcons.MoreVert, modifier = Modifier.size(48.dp),
                    onClick = { menu = true })
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.rule_view_source)) },
                        onClick = {
                            menu = false
                            showSource = true
                        })
                    if (group.allExampleUrls.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.rule_view_examples)) },
                            onClick = {
                                menu = false
                                onDismissRequest()
                                navigator.navigate(
                                    ImagePreviewRoute(
                                        title = group.name,
                                        items = buildRuleGroupPreviewItems(group)
                                    )
                                )
                            })
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.subscription_settings)) },
                        onClick = {
                            menu = false
                            onDismissRequest()
                            openSubscription(subs.id)
                        })
                    if (subs.isLocal) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.rule_delete)) },
                            onClick = { menu = false; onClickDelete() })
                    }
                }
            }
        },
    ) {
        if (control == null) {
            Text(stringResource(Res.string.config_loading))
        } else {
            GkRuleSettingsContent(
                control, onSettingChange,
                title = if (group is RawSubscription.RawGlobalGroup && appId != null) stringResource(
                    Res.string.rule_enable_in_app
                ) else stringResource(Res.string.rule_enable),
                onViewControl = {
                    ruleControl.show(group.toRuleGroupTarget(subs.id, appId))
                }
            )
            if (group is RawSubscription.RawGlobalGroup && appId == null) {
                DefaultAppScopeSetting(group, matchAnyApp, onMatchAnyAppChange)
            }
            GkRuleExclusionsCard(excludeData, excludeAppId, onClick = onClickEditExclude)
        }
    }
    if (showSource) {
        RuleSourceDialog(group, onDismissRequest = { showSource = false })
    }
}


@Composable
private fun DefaultAppScopeSetting(
    group: RawSubscription.RawGlobalGroup,
    matchAnyApp: Boolean?,
    onChange: (Boolean?) -> Unit,
) {
    val subscriptionDefault = group.matchAnyApp ?: true
    val checked = matchAnyApp ?: subscriptionDefault
    val title = stringResource(Res.string.global_rule_default_apps)
    val description = stringResource(when (matchAnyApp) {
        true -> Res.string.global_rule_default_apps_enabled
        false -> Res.string.global_rule_default_apps_disabled
        null -> when (subscriptionDefault) {
            true -> Res.string.global_rule_default_apps_follow_enabled
            false -> Res.string.global_rule_default_apps_follow_disabled
        }
    })
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f)
                    .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            GkIconButton(
                imageVector = ResetSettings,
                contentDescription = stringResource(Res.string.settings_reset_default),
                enabled = matchAnyApp != null,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                onClick = { onChange(null) },
            )
            GkTriStateSwitch(
                checked = checked,
                onCheckedChange = { onChange(checked != true) },
                modifier = Modifier.width(84.dp).fillMaxHeight().semantics {
                    contentDescription = title
                },
                stateDescription = description,
            )
        }
    }
}

@Composable
private fun RuleSourceDialog(
    group: RawSubscription.RawGroupProps,
    onDismissRequest: () -> Unit,
) {
    // Syntax highlighting is only needed after the user opens the source viewer.
    val source = group.cacheStr
    val darkTheme = LocalDarkTheme.current
    val annotatedText = remember(source, darkTheme) { getJson5AnnotatedString(source, darkTheme) }
    GkFullscreenDialog(onDismissRequest) {
        GkScaffold(topBar = {
            GkTopAppBar(
                title = {
                    GkTwoLineText(
                        title = stringResource(Res.string.rule_source),
                        subtitle = group.name
                    )
                },
                actions = {
                    GkIconButton(
                        GkIcons.Close, onClick = onDismissRequest,
                        contentDescription = stringResource(Res.string.dialog_close)
                    )
                },
            )
        }) { contentPadding ->
            val textModifier = Modifier.scaffoldPadding(contentPadding).padding(16.dp)
                .fillMaxSize().clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            if (source.length > JSON5_LARGE_TEXT_THRESHOLD) {
                GkLazyCopyableText(
                    onCopy = ::copyText,
                    text = annotatedText,
                    modifier = textModifier,
                    contentPadding = PaddingValues(16.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    textContentDescription = stringResource(Res.string.rule_content)
                )
            } else {
                GkCopyableText(
                    onCopy = ::copyText,
                    text = annotatedText,
                    textToCopy = source,
                    modifier = textModifier,
                    contentPadding = PaddingValues(16.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    textContentDescription = stringResource(Res.string.rule_content)
                )
            }
        }
    }
}

// 规则组示例图需要保留“图片属于哪个子规则”的上下文，预览页才能显示更具体的标题。
private fun buildRuleGroupPreviewItems(
    group: RawSubscription.RawGroupProps
): List<ImagePreviewItem> {
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
        rule.key != null -> Res.string.rule_key_description.getSync(rule.key)
        !rule.preKeys.isNullOrEmpty() -> Res.string.rule_pre_keys_description.getSync(
            (rule.preKeys as Iterable<Any?>).joinToString(
                ","
            )
        )

        else -> null
    }
}

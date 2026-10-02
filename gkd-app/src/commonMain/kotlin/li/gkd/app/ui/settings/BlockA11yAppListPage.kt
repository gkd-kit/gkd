package li.gkd.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import li.gkd.app.app.AppQuery
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_whitelist
import li.gkd.app.resources.a11y_whitelist_follow_apps
import li.gkd.app.resources.a11y_whitelist_independent
import li.gkd.app.resources.action_save
import li.gkd.app.resources.app_ids_input_hint
import li.gkd.app.resources.app_name_id_input_hint
import li.gkd.app.resources.edit_discard_confirmation
import li.gkd.app.resources.filter_title
import li.gkd.app.resources.mode_switch
import li.gkd.app.resources.notice_title
import li.gkd.app.resources.search_no_results
import li.gkd.app.resources.sort_title
import li.gkd.app.resources.unchanged
import li.gkd.app.resources.update_success
import li.gkd.app.resources.whitelist_text_edit
import li.gkd.app.resources.whitelist_text_edit_mode_enter
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.GkAnimatedBooleanContent
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkAppBarTextField
import li.gkd.app.ui.component.GkAppCheckboxCard
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMenuGroupCard
import li.gkd.app.ui.component.GkMenuItemCheckbox
import li.gkd.app.ui.component.GkMenuItemRadioButton
import li.gkd.app.ui.component.GkMultiTextField
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.isFullVisible
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.icon.GkBackCloseIcon
import li.gkd.app.ui.icon.GkSearchCloseIconButton
import li.gkd.app.ui.icon.LockOpenRight
import li.gkd.app.ui.navigation.GkBackHandler
import li.gkd.app.ui.navigation.launchUiAction
import li.gkd.app.ui.option.AppGroupOption
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.scaffoldPadding
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun BlockA11yAppListPage(
    vm: BlockA11yAppListViewModel,
    onBack: () -> Unit,
    showToast: (String) -> Unit,
    dialogs: DialogRequests,
    hideIme: () -> Boolean,
    appIcon: @Composable (String, Dp) -> Unit,
) {
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    var searchStr by rememberSaveable { mutableStateOf("") }
    var query by remember { mutableStateOf(searchStr) }
    LaunchedEffect(searchStr) { delay(200); query = searchStr }
    val source by vm.uiState.collectAsStateWithLifecycle()
    val result = when (val loaded = source) {
        is Loadable.Ready -> Loadable.Ready(
            AppQuery.select(
                loaded.value.apps, query, store.a11yAppGroupType, store.a11yAppSort,
                actionOrder = loaded.value.actionOrder, visitOrder = loaded.value.visitOrder,
            )
        )

        is Loadable.Failure -> loaded
        Loadable.Loading -> Loadable.Loading
    }
    val appInfos = result.value?.apps.orEmpty()
    val showAllApps = result.value?.showAllApps == true
    val editorState = rememberAppListEditorState()
    val showSearchBar = editorState.searchOpen
    val editable = editorState.editing
    val editText = editorState.draft
    val pageScrollState = rememberListScrollState(canScroll = { !editable })
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnListChange(appInfos, key = { it.id })
    GkBackHandler(editable, launchUiAction(vm.scope, showToast) {
        hideIme()
        if (vm.editor.hasChanges(editorState.draft)) {
            if (!dialogs.confirm(
                    title = getString(Res.string.notice_title),
                    text = getString(Res.string.edit_discard_confirmation),
                )
            ) return@launchUiAction
        }
        editorState.closeEditor()
    })
    GkScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                canScroll = !editable && !store.blockA11yAppListFollowMatch,
                navigationIcon = {
                    IconButton(
                        onClick = launchUiAction(vm.scope, showToast) {
                            if (editable) {
                                if (vm.editor.hasChanges(editorState.draft)) {
                                    hideIme()
                                    if (!dialogs.confirm(
                                            title = getString(Res.string.notice_title),
                                            text = getString(Res.string.edit_discard_confirmation),
                                        )
                                    ) return@launchUiAction
                                }
                                editorState.closeEditor()
                            } else {
                                hideIme()
                                onBack()
                            }
                        }
                    ) {
                        GkBackCloseIcon(backOrClose = !editable)
                    }
                },
                title = {
                    val firstShowSearchBar = remember { showSearchBar }
                    if (showSearchBar) {
                        GkBackHandler(true) {
                            if (!hideIme()) {
                                editorState.closeSearch({ searchStr = it.trim() })
                            }
                        }
                        GkAppBarTextField(
                            value = searchStr,
                            onValueChange = { searchStr = it.trim() },
                            hint = stringResource(Res.string.app_name_id_input_hint),
                            modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                        )
                    } else {
                        val titleModifier = Modifier
                            .noRippleClickable(
                                onClick = {
                                    pageScrollState.resetScroll()
                                }
                            )
                        Text(
                            modifier = titleModifier,
                            text = stringResource(Res.string.a11y_whitelist),
                        )
                    }
                },
                actions = {
                    GkAnimatedBooleanContent(
                        targetState = editable,
                        contentAlignment = Alignment.TopEnd,
                        contentTrue = {
                            GkIconButton(
                                imageVector = GkIcons.Check,
                                contentDescription = stringResource(Res.string.action_save),
                                onClick = launchUiAction(vm.scope, showToast) {
                                    val draft = editorState.draft
                                    hideIme()
                                    val changed = vm.editor.save(draft)
                                    showToast(getString(if (changed) Res.string.update_success else Res.string.unchanged))
                                    if (editorState.draft == draft) editorState.closeEditor()
                                },
                            )
                        },
                        contentFalse = {
                            Row {
                                var expanded by remember { mutableStateOf(false) }
                                AnimatedVisibility(!store.blockA11yAppListFollowMatch) {
                                    Row {
                                        GkSearchCloseIconButton(
                                            onClick = {
                                                editorState.toggleSearch(
                                                    searchStr,
                                                    { searchStr = it.trim() }
                                                )
                                            },
                                            isSearchOpen = showSearchBar,
                                        )
                                        GkFilterIconButton(filtered = !showAllApps, onClick = {
                                            expanded = true
                                        })
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .wrapContentSize(Alignment.TopStart)
                                ) {
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        GkMenuGroupCard(
                                            inTop = true,
                                            title = stringResource(Res.string.sort_title)
                                        ) {
                                            AppSortOption.objects.forEach { option ->
                                                GkMenuItemRadioButton(
                                                    text = option.label,
                                                    selected = AppSortOption.objects.findOption(
                                                        store.a11yAppSort
                                                    ) == option,
                                                    onClick = { vm.setSortType(option) },
                                                )
                                            }
                                        }
                                        GkMenuGroupCard(
                                            inTop = true,
                                            title = stringResource(Res.string.filter_title)
                                        ) {
                                            AppGroupOption.normalObjects.forEach { option ->
                                                val newValue = option.invert(store.a11yAppGroupType)
                                                GkMenuItemCheckbox(
                                                    enabled = newValue != 0,
                                                    text = option.label,
                                                    checked = option.include(store.a11yAppGroupType),
                                                    onClick = { vm.setAppGroupType(newValue) },
                                                )
                                            }
                                        }
                                    }
                                }
                                GkIconButton(
                                    imageVector = if (store.blockA11yAppListFollowMatch) GkIcons.Lock else LockOpenRight,
                                    animateMorph = true,
                                    contentDescription = if (store.blockA11yAppListFollowMatch) stringResource(
                                        Res.string.a11y_whitelist_follow_apps
                                    ) else stringResource(Res.string.a11y_whitelist_independent),
                                    onClickLabel = stringResource(Res.string.mode_switch),
                                    onClick = {
                                        editorState.closeSearch({ searchStr = it.trim() })
                                        vm.toggleFollowMatchList()
                                    }
                                )
                            }
                        },
                    )
                })
        },
        floatingActionButton = {
            GkAnimatedFloatingActionButton(
                visible = !editable && scrollBehavior.isFullVisible && !store.blockA11yAppListFollowMatch,
                onClickLabel = stringResource(Res.string.whitelist_text_edit_mode_enter),
                onClick = {
                    editorState.closeSearch({ searchStr = it.trim() })
                    editorState.startEditing(vm.editor.initialText())
                },
                imageVector = GkIcons.Edit,
                contentDescription = stringResource(Res.string.whitelist_text_edit)
            )
        },
    ) { contentPadding ->
        if (store.blockA11yAppListFollowMatch) {
            Column(
                modifier = Modifier.scaffoldPadding(contentPadding),
            ) {
                GkPageBottomSpace()
                Text(
                    text = stringResource(Res.string.a11y_whitelist_follow_apps),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        } else if (editable) {
            GkMultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                text = editText,
                onTextChange = editorState::setText,
                immediateFocus = true,
                placeholderText = stringResource(Res.string.app_ids_input_hint),
                indicatorSize = editorState.indicatorSize,
            )
        } else {
            val blockA11yAppList by SettingsRepository.blockA11yAppList.collectAsStateWithLifecycle()
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(appInfos, { it.id }) { appInfo ->
                    GkAppCheckboxCard(
                        appIcon = { appIcon(appInfo.id, 32.dp) },
                        appName = { GkAppNameText(appInfo.id, appInfo.name) },
                        appInfo = appInfo,
                        checked = blockA11yAppList.contains(appInfo.id),
                        onCheckedChange = { vm.toggleApp(appInfo.id) },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    if (result is Loadable.Loading) {
                        CircularProgressIndicator()
                        GkPageBottomSpace()
                    } else if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        GkEmptyState(text = stringResource(Res.string.search_no_results))
                        GkPageBottomSpace(height = GkPageBottomSpaceDefaults.CompactHeight)
                    } else {
                        GkPageBottomSpace()
                    }
                }
            }
        }
    }
}

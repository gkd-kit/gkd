package li.gkd.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import li.gkd.app.app.AppQuery
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_scoped
import li.gkd.app.resources.action_save
import li.gkd.app.resources.app_ids_input_hint
import li.gkd.app.resources.app_name_id_input_hint
import li.gkd.app.resources.filter_title
import li.gkd.app.resources.scoped_a11y_help
import li.gkd.app.resources.search_no_results
import li.gkd.app.resources.sort_title
import li.gkd.app.resources.text_edit
import li.gkd.app.resources.text_edit_mode_enter
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
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
import li.gkd.app.ui.image.GkAppIcon
import li.gkd.app.ui.option.AppGroupOption
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.platform.GkBackHandler
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.platform.hideIme
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.scaffoldPadding
import org.jetbrains.compose.resources.stringResource

@Composable
fun A11yScopeAppListPage(host: UiHost) {
    val vm = viewModel { A11yScopeAppListViewModel() }
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val searchStr by vm.searchStr.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(searchStr) }
    LaunchedEffect(searchStr) { delay(200); query = searchStr }
    val source by vm.uiState.collectAsStateWithLifecycle()
    val result = when (val loaded = source) {
        is Loadable.Ready -> Loadable.Ready(
            AppQuery.select(
                loaded.value.apps, query, store.a11yScopeAppGroupType, store.a11yScopeAppSort,
                actionOrder = loaded.value.actionOrder, visitOrder = loaded.value.visitOrder,
            )
        )

        is Loadable.Failure -> loaded
        Loadable.Loading -> Loadable.Loading
    }
    val appInfos = result.value?.apps.orEmpty()
    val showAllApps = result.value?.showAllApps == true
    val editorState = vm.editorState
    val editorUiState by editorState.state.collectAsStateWithLifecycle()
    val showSearchBar = editorUiState.searchOpen
    val editable = editorUiState.editing
    val editText = editorUiState.draft
    val pageScrollState = rememberListScrollState(canScroll = { !editable })
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnListChange(appInfos, key = { it.id })
    GkBackHandler(editable, vm.scope.launchUiAction {
        host.hideIme()
        vm.closeEditor()
    })
    GkScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                canScroll = !editable,
                navigationIcon = {
                    IconButton(
                        onClick = vm.scope.launchUiAction {
                            host.hideIme()
                            vm.onBack()
                        }
                    ) {
                        GkBackCloseIcon(backOrClose = !editable)
                    }
                },
                title = {
                    val firstShowSearchBar = remember { showSearchBar }
                    if (showSearchBar) {
                        GkBackHandler(true) {
                            if (!host.hideIme()) {
                                editorState.closeSearch { vm.setSearchStr(it.trim()) }
                            }
                        }
                        GkAppBarTextField(
                            value = searchStr,
                            onValueChange = { vm.setSearchStr(it.trim()) },
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
                            text = stringResource(Res.string.a11y_scoped),
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
                                onClick = vm.scope.launchUiAction {
                                    host.hideIme()
                                    vm.saveEditor()
                                },
                            )
                        },
                        contentFalse = {
                            Row {
                                var expanded by remember { mutableStateOf(false) }
                                GkSearchCloseIconButton(
                                    onClick = {
                                        editorState.toggleSearch(
                                            searchStr
                                        ) { vm.setSearchStr(it.trim()) }
                                    },
                                    isSearchOpen = showSearchBar,
                                )
                                GkFilterIconButton(filtered = !showAllApps, onClick = {
                                    expanded = true
                                })
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
                                                        store.a11yScopeAppSort
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
                                                val newValue =
                                                    option.invert(store.a11yScopeAppGroupType)
                                                GkMenuItemCheckbox(
                                                    enabled = newValue != 0,
                                                    text = option.label,
                                                    checked = option.include(store.a11yScopeAppGroupType),
                                                    onClick = { vm.setAppGroupType(newValue) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                    GkIconButton(
                        imageVector = GkIcons.HelpOutline,
                        contentDescription = stringResource(Res.string.scoped_a11y_help),
                        onClick = vm.scope.launchUiAction { vm.showHelp() },
                    )
                })
        },
        floatingActionButton = {
            GkAnimatedFloatingActionButton(
                visible = !editable && scrollBehavior.isFullVisible,
                onClickLabel = stringResource(Res.string.text_edit_mode_enter),
                onClick = vm::startEditing,
                imageVector = GkIcons.Edit,
                contentDescription = stringResource(Res.string.text_edit)
            )
        },
    ) { contentPadding ->
        if (editable) {
            GkMultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                text = editText,
                onTextChange = editorState::setText,
                immediateFocus = true,
                placeholderText = stringResource(Res.string.app_ids_input_hint),
                indicatorSize = editorUiState.indicatorSize,
            )
        } else {
            val a11yScopeAppList by SettingsRepository.a11yScopeAppList.collectAsStateWithLifecycle()
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(appInfos, { it.id }) { appInfo ->
                    val checked = a11yScopeAppList.contains(appInfo.id)
                    GkAppCheckboxCard(
                        appIcon = { GkAppIcon(appInfo.id, 32.dp) },
                        appName = { GkAppNameText(appInfo.id, appInfo.name) },
                        appInfo = appInfo,
                        checked = checked,
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

package li.gkd.app.ui

import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.store.AppStore.blockA11yAppListFlow
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.ui.icon.GkBackCloseIcon
import li.gkd.app.ui.icon.LockOpenRight
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.AppGroupOption
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.findOption
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.ui.component.GkAnimatedBooleanContent
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.icon.GkSearchCloseIconButton
import li.gkd.app.ui.component.GkAppBarTextField
import li.gkd.app.ui.component.GkAppCheckboxCard
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMenuGroupCard
import li.gkd.app.ui.component.GkMenuItemCheckbox
import li.gkd.app.ui.component.GkMenuItemRadioButton
import li.gkd.app.ui.component.GkMultiTextField
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.isFullVisible
import li.gkd.app.ui.component.rememberListScrollState

@Serializable
data object BlockA11yAppListRoute : NavKey

@Composable
fun BlockA11yAppListPage() {
    val mainVm = MainViewModel.requireCurrent()
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { BlockA11yAppListVm(mainVm) }
    val store by storeFlow.collectAsStateWithLifecycle()
    val appInfos by vm.appInfosFlow.collectAsStateWithLifecycle()
    val showAllApps by vm.appFilter.showAllAppFlow.collectAsStateWithLifecycle()
    val searchStr by vm.searchStrFlow.collectAsStateWithLifecycle()
    val showSearchBar by vm.showSearchBarFlow.collectAsStateWithLifecycle()
    val editable by vm.editableFlow.collectAsStateWithLifecycle()
    val editText by vm.textFlow.collectAsStateWithLifecycle()
    val pageScrollState = rememberListScrollState(canScroll = { !editable })
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnListChange(appInfos, key = { it.id })
    BackHandler(editable, vm.scope.launchUiAction {
        context.imeController.requestHide()
        if (vm.textChanged) {
            if (!mainVm.dialogRequests.confirm(
                title = UiStrings.notice_title,
                text = UiStrings.edit_discard_confirmation,
            )) return@launchUiAction
        }
        vm.setEditable(false)
    })
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                canScroll = !editable && !store.blockA11yAppListFollowMatch,
                navigationIcon = {
                    IconButton(
                        onClick = throttle(vm.scope.launchUiAction {
                            if (editable) {
                                if (vm.textChanged) {
                                    context.imeController.requestHide()
                                    if (!mainVm.dialogRequests.confirm(
                                        title = UiStrings.notice_title,
                                        text = UiStrings.edit_discard_confirmation,
                                    )) return@launchUiAction
                                }
                                vm.setEditable(false)
                            } else {
                                context.imeController.hideAndAwait()
                                mainVm.popPage()
                            }
                        })
                    ) {
                        GkBackCloseIcon(backOrClose = !editable)
                    }
                },
                title = {
                    val firstShowSearchBar = remember { showSearchBar }
                    if (showSearchBar) {
                        BackHandler {
                            if (!context.imeController.requestHide()) {
                                vm.setSearchBarVisible(false)
                            }
                        }
                        GkAppBarTextField(
                            value = searchStr,
                            onValueChange = vm::setSearchStr,
                            hint = UiStrings.app_name_id_input_hint,
                            modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                        )
                    } else {
                        val titleModifier = Modifier
                            .noRippleClickable(
                                onClick = throttle {
                                    pageScrollState.resetScroll()
                                }
                            )
                        Text(
                            modifier = titleModifier,
                            text = UiStrings.a11y_whitelist,
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
                                contentDescription = UiStrings.action_save,
                                onClick = throttle {
                                    vm.saveText()
                                    context.imeController.requestHide()
                                },
                            )
                        },
                        contentFalse = {
                            Row {
                                var expanded by remember { mutableStateOf(false) }
                                AnimatedVisibility(!store.blockA11yAppListFollowMatch) {
                                    Row {
                                        GkSearchCloseIconButton(
                                            onClick = throttle {
                                                vm.toggleSearchBar()
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
                                        GkMenuGroupCard(inTop = true, title = UiStrings.sort_title) {
                                            AppSortOption.objects.forEach { option ->
                                                GkMenuItemRadioButton(
                                                    text = option.label,
                                                    selected = AppSortOption.objects.findOption(store.a11yAppSort) == option,
                                                    onClick = { vm.setSortType(option) },
                                                )
                                            }
                                        }
                                        GkMenuGroupCard(inTop = true, title = UiStrings.filter_title) {
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
                                    contentDescription = if (store.blockA11yAppListFollowMatch) UiStrings.a11y_whitelist_follow_apps else UiStrings.a11y_whitelist_independent,
                                    onClickLabel = UiStrings.mode_switch,
                                    onClick = throttle {
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
                onClickLabel = UiStrings.whitelist_text_edit_mode_enter,
                onClick = {
                    vm.setEditable(true)
                },
                imageVector = GkIcons.Edit,
                contentDescription = UiStrings.whitelist_text_edit
            )
        },
    ) { contentPadding ->
        if (store.blockA11yAppListFollowMatch) {
            Column(
                modifier = Modifier.scaffoldPadding(contentPadding),
            ) {
                GkPageBottomSpace()
                Text(
                    text = UiStrings.a11y_whitelist_follow_apps,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        } else if (editable) {
            GkMultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                text = editText,
                onTextChange = vm::setText,
                immediateFocus = true,
                placeholderText = UiStrings.app_ids_input_hint,
                indicatorSize = vm.indicatorSizeFlow.collectAsStateWithLifecycle().value,
            )
        } else {
            val blockA11yAppList by blockA11yAppListFlow.collectAsStateWithLifecycle()
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(appInfos, { it.id }) { appInfo ->
                    GkAppCheckboxCard(
                        appInfo = appInfo,
                        checked = blockA11yAppList.contains(appInfo.id),
                        onCheckedChange = { vm.toggleApp(appInfo.id) },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        GkEmptyState(text = UiStrings.search_no_results)
                        GkPageBottomSpace(height = GkPageBottomSpaceDefaults.CompactHeight)
                    } else {
                        GkPageBottomSpace()
                    }
                }
            }
        }
    }
}

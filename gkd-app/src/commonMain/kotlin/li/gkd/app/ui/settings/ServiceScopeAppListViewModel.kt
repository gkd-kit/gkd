package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.app.AppSort
import li.gkd.app.platform.requestAutomatorRestart
import li.gkd.app.platform.requestCompanionServiceUpdate
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.home.scopeAppSource
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

enum class ServiceScopeKind(val title: StringResource) {
    Companion(Res.string.companion_paused_apps_title),
    Disabled(Res.string.a11y_whitelist),
}

class ServiceScopeAppListViewModel(private val kind: ServiceScopeKind) : BaseViewModel() {
    val appList =
        when (kind) {
            ServiceScopeKind.Companion -> SettingsRepository.companionScopeAppList
            ServiceScopeKind.Disabled -> SettingsRepository.blockA11yAppList
        }

    val editorState = AppListEditorState()

    val searchStr: StateFlow<String>
        field = MutableStateFlow("")

    fun setSearchStr(value: String) {
        searchStr.value = value
    }

    val uiState = scopeAppSource.stateLoadable()
    private val editor =
        AppIdListEditor(
            { appList.value },
            {
                when (kind) {
                    ServiceScopeKind.Companion -> saveCompanionScope { _ -> it }
                    ServiceScopeKind.Disabled -> SettingsRepository.saveBlockA11yAppList(it)
                }
            },
        )

    suspend fun showCompanionHelp() {
        MainViewModel.requireCurrent()
            .dialogRequests
            .showMessage(
                title = getString(Res.string.companion_scope),
                text = getString(Res.string.companion_help_description),
            )
    }

    fun startEditing() {
        editorState.closeSearch(::setSearchStr)
        editor.startEditing(editorState)
    }

    suspend fun closeEditor() {
        editor.close(editorState) {
            MainViewModel.requireCurrent()
                .dialogRequests
                .confirm(
                    title = getString(Res.string.notice_title),
                    text = getString(Res.string.edit_discard_confirmation),
                )
        }
    }

    suspend fun onBack() {
        if (editorState.state.value.editing) closeEditor()
        else MainViewModel.requireCurrent().navigator.pop()
    }

    suspend fun saveEditor() {
        editor.save(editorState) { changed ->
            ToastUtils.show(
                getString(if (changed) Res.string.update_success else Res.string.unchanged),
            )
        }
    }

    fun setSortType(value: AppSort) {
        SettingsRepository.updateSettings { it.copy(serviceScopeAppSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        SettingsRepository.updateSettings { it.copy(serviceScopeAppGroupType = value) }
    }

    suspend fun toggleApp(appId: String) {
        val toggle: (Set<String>) -> Set<String> = { if (appId in it) it - appId else it + appId }
        when (kind) {
            ServiceScopeKind.Companion -> saveCompanionScope(toggle)
            ServiceScopeKind.Disabled -> SettingsRepository.updateBlockA11yAppList(toggle)
        }
    }

    private suspend fun saveCompanionScope(transform: (Set<String>) -> Set<String>) {
        val previous = appList.value
        SettingsRepository.updateCompanionScopeAppList(transform)
        if (appList.value != previous) requestCompanionServiceUpdate()
    }

    fun toggleFollowMatchList() {
        SettingsRepository.updateSettings {
            it.copy(blockA11yAppListFollowMatch = !it.blockA11yAppListFollowMatch)
        }
        requestAutomatorRestart()
    }
}

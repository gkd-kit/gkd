package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.platform.requestAutomatorRestart
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.home.scopeAppSource
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

class BlockA11yAppListViewModel : BaseViewModel() {
    val editorState = AppListEditorState()

    val searchStr: StateFlow<String>
        field = MutableStateFlow("")

    fun setSearchStr(value: String) {
        searchStr.value = value
    }

    val uiState = scopeAppSource.stateLoadable()
    private val editor = AppIdListEditor(
        { SettingsRepository.blockA11yAppList.value },
        SettingsRepository::replaceBlockA11yAppList
    )

    fun startEditing() {
        editorState.closeSearch(::setSearchStr)
        editorState.startEditing(editor.initialText())
    }

    suspend fun closeEditor() {
        if (editor.hasChanges(editorState.state.value.draft)) {
            val confirmed = MainViewModel.requireCurrent().dialogRequests.confirm(
                title = getString(Res.string.notice_title),
                text = getString(Res.string.edit_discard_confirmation),
            )
            if (!confirmed) return
        }
        editorState.closeEditor()
    }

    suspend fun onBack() {
        if (editorState.state.value.editing) closeEditor()
        else MainViewModel.requireCurrent().navigator.pop()
    }

    suspend fun saveEditor() {
        val draft = editorState.state.value.draft
        val changed = editor.save(draft)
        ToastUtils.show(getString(if (changed) Res.string.update_success else Res.string.unchanged))
        if (editorState.state.value.draft == draft) editorState.closeEditor()
    }

    fun setSortType(value: AppSortOption) {
        SettingsRepository.updateSettings { it.copy(a11yAppSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        SettingsRepository.updateSettings { it.copy(a11yAppGroupType = value) }
    }

    fun toggleApp(appId: String) {
        SettingsRepository.updateBlockA11yAppList { if (appId in it) it - appId else it + appId }
    }

    fun toggleFollowMatchList() {
        SettingsRepository.updateSettings { it.copy(blockA11yAppListFollowMatch = !it.blockA11yAppListFollowMatch) }
        requestAutomatorRestart()
    }
}

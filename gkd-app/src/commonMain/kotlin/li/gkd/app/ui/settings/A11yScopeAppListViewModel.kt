package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_scoped
import li.gkd.app.resources.edit_discard_confirmation
import li.gkd.app.resources.notice_title
import li.gkd.app.resources.scoped_a11y_help_behavior
import li.gkd.app.resources.scoped_a11y_help_problem
import li.gkd.app.resources.scoped_a11y_help_scope
import li.gkd.app.resources.unchanged
import li.gkd.app.resources.update_success
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.home.scopeAppSource
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

class A11yScopeAppListViewModel : BaseViewModel() {
    val editorState = AppListEditorState()

    val searchStr: StateFlow<String>
        field = MutableStateFlow("")

    fun setSearchStr(value: String) {
        searchStr.value = value
    }

    val uiState = scopeAppSource.stateLoadable()
    private val editor = AppIdListEditor(
        { SettingsRepository.a11yScopeAppList.value },
        SettingsRepository::replaceA11yScopeAppList
    )

    suspend fun showHelp() {
        MainViewModel.requireCurrent().dialogRequests.showMessage(
            title = getString(Res.string.a11y_scoped),
            text = getString(Res.string.scoped_a11y_help_problem) +
                getString(Res.string.scoped_a11y_help_behavior) +
                getString(Res.string.scoped_a11y_help_scope),
        )
    }

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
        SettingsRepository.updateSettings { it.copy(a11yScopeAppSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        SettingsRepository.updateSettings { it.copy(a11yScopeAppGroupType = value) }
    }

    fun toggleApp(appId: String) {
        SettingsRepository.updateA11yScopeAppList { if (appId in it) it - appId else it + appId }
    }
}

package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import li.gkd.app.settings.SettingsAppIds

data class AppListEditorUiState(
    val searchOpen: Boolean = false,
    val editing: Boolean = false,
    val draft: String = "",
) {
    val indicatorSize get() = SettingsAppIds.decode(draft).size
}

class AppListEditorState {
    val state: StateFlow<AppListEditorUiState>
        field = MutableStateFlow(AppListEditorUiState())

    fun toggleSearch(query: String, updateQuery: (String) -> Unit) {
        if (!state.value.searchOpen) state.update { it.copy(searchOpen = true) }
        else if (query.isEmpty()) state.update { it.copy(searchOpen = false) }
        else updateQuery("")
    }

    fun closeSearch(updateQuery: (String) -> Unit) {
        state.update { it.copy(searchOpen = false) }
        updateQuery("")
    }

    fun startEditing(text: String) {
        state.value = AppListEditorUiState(editing = true, draft = text)
    }

    fun setText(text: String) {
        state.update { it.copy(draft = text) }
    }

    fun closeEditor() {
        state.update { it.copy(editing = false) }
    }
}

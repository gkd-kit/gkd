package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import li.gkd.app.settings.SettingsAppIds

class AppListEditorState(
    searchOpen: Boolean = false,
    editing: Boolean = false,
    draft: String = ""
) {
    var searchOpen by mutableStateOf(searchOpen)
        private set
    var editing by mutableStateOf(editing)
        private set
    var draft by mutableStateOf(draft)
        private set

    val indicatorSize get() = SettingsAppIds.decode(draft).size

    fun toggleSearch(query: String, updateQuery: (String) -> Unit) {
        if (!searchOpen) searchOpen = true
        else if (query.isEmpty()) searchOpen = false
        else updateQuery("")
    }

    fun closeSearch(updateQuery: (String) -> Unit) {
        searchOpen = false
        updateQuery("")
    }

    fun startEditing(text: String) {
        searchOpen = false
        draft = text
        editing = true
    }

    fun setText(text: String) {
        draft = text
    }

    fun closeEditor() {
        editing = false
    }
}

@Composable
fun rememberAppListEditorState(): AppListEditorState = rememberSaveable(
    saver = listSaver(
        save = { listOf(it.searchOpen, it.editing, it.draft) },
        restore = { AppListEditorState(it[0] as Boolean, it[1] as Boolean, it[2] as String) },
    ),
) { AppListEditorState() }

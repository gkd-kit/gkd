package li.gkd.app.ui.settings

import li.gkd.app.app.AppInfoRepository
import li.gkd.app.settings.SettingsAppIds

/** Prepares and persists explicit app ID edits; the owning page ViewModel manages the draft. */
class AppIdListEditor(
    private val currentIds: () -> Set<String>,
    private val persist: suspend (Set<String>) -> Unit,
) {
    private var savedIds = currentIds()

    fun startEditing(state: AppListEditorState) {
        savedIds = currentIds()
        state.startEditing(
            SettingsAppIds.format(savedIds, AppInfoRepository.snapshot?.apps.orEmpty()),
        )
    }

    fun hasChanges(text: String) = savedIds != SettingsAppIds.decode(text)

    suspend fun save(text: String): Boolean {
        val ids = SettingsAppIds.decode(text)
        val changed = savedIds != ids
        persist(ids)
        savedIds = ids
        return changed
    }

    suspend fun close(state: AppListEditorState, confirmDiscard: suspend () -> Boolean) {
        if (hasChanges(state.state.value.draft) && !confirmDiscard()) return
        state.closeEditor()
    }

    suspend fun save(state: AppListEditorState, onSaved: suspend (Boolean) -> Unit) {
        val draft = state.state.value.draft
        onSaved(save(draft))
        // Typing may continue while the write or feedback is suspended.
        if (state.state.value.draft == draft) state.closeEditor()
    }
}

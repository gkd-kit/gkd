package li.gkd.app.ui.snapshot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.snapshot.SnapshotNodeTree
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.state.Loadable
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.util.JsonUtils

data class SnapshotNodeSelection(
    val id: Int? = null,
    val candidates: List<Int> = emptyList(),
)

class SnapshotPreviewViewModel : BaseViewModel() {
    val tree: StateFlow<Loadable<SnapshotNodeTree>>
        field = MutableStateFlow<Loadable<SnapshotNodeTree>>(Loadable.Loading)
    val selection: StateFlow<SnapshotNodeSelection>
        field = MutableStateFlow(SnapshotNodeSelection())
    private var loadJob: Job? = null
    private var hitJob: Job? = null

    fun load(id: Long) {
        loadJob?.cancel()
        clearSelection()
        tree.value = Loadable.Loading
        loadJob = scope.launch {
            tree.value =
                try {
                    val text =
                        withContext(Dispatchers.IO) { SnapshotStore.snapshotFile(id).readText() }
                    Loadable.Ready(
                        withContext(Dispatchers.Default) {
                            val snapshot =
                                JsonUtils.keepingNulls.decodeFromString<ComplexSnapshot>(text)
                            SnapshotNodeTree(snapshot)
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Loadable.Failure(e)
                }
        }
    }

    fun hit(x: Float, y: Float, onSelected: (Int) -> Unit) {
        val current = tree.value.value ?: return
        hitJob?.cancel()
        hitJob = scope.launch {
            val hits =
                withContext(Dispatchers.Default) {
                    current.hit(x * current.snapshot.screenWidth, y * current.snapshot.screenHeight)
                }
            val firstHit = hits.firstOrNull()
            selection.value =
                SnapshotNodeSelection(
                    id = firstHit ?: selection.value.id,
                    candidates = if (hits.size > 1) hits else emptyList(),
                )
            if (firstHit != null) onSelected(firstHit)
        }
    }

    fun selectFirstNodeIfNone(snapshotId: Long) {
        val current = tree.value.value ?: return
        if (selection.value.id == null && current.snapshot.id == snapshotId) {
            current.roots.firstOrNull()?.let { select(it.id) }
        }
    }

    fun select(id: Int) {
        val candidates = selection.value.candidates
        selection.value =
            SnapshotNodeSelection(id, if (id in candidates) candidates else emptyList())
    }

    fun clearSelection() {
        hitJob?.cancel()
        selection.value = SnapshotNodeSelection()
    }

    fun dismissCandidates() {
        selection.value = selection.value.copy(candidates = emptyList())
    }
}
